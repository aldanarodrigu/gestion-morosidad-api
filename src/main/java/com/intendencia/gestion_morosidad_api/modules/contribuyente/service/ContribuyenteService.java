package com.intendencia.gestion_morosidad_api.modules.contribuyente.service;

import com.intendencia.gestion_morosidad_api.integration.intendencia.geopagos.client.GeoPagosClient;
import com.intendencia.gestion_morosidad_api.integration.intendencia.geopagos.dto.GeoPagosContribuyenteDto;
import com.intendencia.gestion_morosidad_api.modules.contacto.TipoContacto;
import com.intendencia.gestion_morosidad_api.modules.contacto.service.SincronizacionContactosService;
import com.intendencia.gestion_morosidad_api.modules.contacto.service.SincronizacionContactosService.Dato;
import com.intendencia.gestion_morosidad_api.modules.contribuyente.dto.ContribuyenteResponse;
import com.intendencia.gestion_morosidad_api.modules.contribuyente.entity.Contribuyente;
import com.intendencia.gestion_morosidad_api.modules.contribuyente.repository.ContribuyenteRepository;
import com.intendencia.gestion_morosidad_api.modules.padron.entity.Padron;
import com.intendencia.gestion_morosidad_api.modules.padron.dto.PadronResponse;
import com.intendencia.gestion_morosidad_api.modules.padron.repository.PadronRepository;
import com.intendencia.gestion_morosidad_api.shared.exception.IntegracionExternaException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ContribuyenteService {

    private final GeoPagosClient geoPagosClient;
    private final ContribuyenteRepository contribuyenteRepository;
    private final PadronRepository padronRepository;
    private final SincronizacionContactosService sincronizacionContactosService;

    @Transactional
    public List<ContribuyenteResponse> listarContribuyentes(String nombre, String documento) {

        Set<String> cmsConDeuda = sincronizarContribuyentesConDeuda();

        if (cmsConDeuda.isEmpty()) {
            return List.of();
        }

        String filtroNombre = normalizarFiltro(nombre);
        String filtroDocumento = normalizarFiltro(documento);
        return padronRepository.findByCmIn(cmsConDeuda)
                .stream()
                .filter(padron -> coincide(padron.getContribuyente().getNombre(), filtroNombre)
                        && coincide(padron.getContribuyente().getDocumento(), filtroDocumento))
                .map(ContribuyenteResponse::de)
                .toList();
    }

    @Transactional
    public Optional<ContribuyenteResponse> buscarPorCm(String cm) {
        return buscarPadronPorCm(cm).map(ContribuyenteResponse::de);
    }

    @Transactional
    public Optional<List<PadronResponse>> listarPadronesPorCm(String cm) {
        return buscarPadronPorCm(cm)
                .map(padron -> padronRepository
                        .findByContribuyenteOrderByNumeroPadronAsc(padron.getContribuyente())
                        .stream()
                        .map(PadronResponse::de)
                        .toList());
    }

    private Optional<Padron> buscarPadronPorCm(String cm) {
        Optional<Padron> padron = padronRepository.findByCm(cm);
        if (padron.isEmpty()) {
            sincronizarDesdeGeoPagos();
            padron = padronRepository.findByCm(cm);
        }
        return padron;
    }

    private static String normalizarFiltro(String filtro) {
        return filtro == null || filtro.isBlank() ? null : filtro.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean coincide(String valor, String filtro) {
        return filtro == null || valor != null && valor.toLowerCase(Locale.ROOT).contains(filtro);
    }

    @Transactional
    public void sincronizarDesdeGeoPagos() {
        sincronizarContribuyentesConDeuda();
    }

    private Set<String> sincronizarContribuyentesConDeuda() {
        // Contribuyentes incluye todos los padrones activos. Solo pendientes informa
        // quién tiene deuda vencida, por lo que se cruza por CM antes de persistir.
        var pendientes = geoPagosClient.obtenerFacturasPendientes();
        if (pendientes == null || pendientes.getData() == null) {
            throw new IntegracionExternaException("GeoPagos no devolvió datos de facturas pendientes");
        }
        Set<String> cmsConDeuda = pendientes.getData().stream()
                .filter(fila -> fila.importeDeuda() != null && fila.importeDeuda().signum() > 0)
                .map(fila -> convertirAString(fila.cm()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (cmsConDeuda.isEmpty()) {
            return cmsConDeuda;
        }

        var respuesta = geoPagosClient.obtenerContribuyentes();

        if (respuesta == null || respuesta.getData() == null) {
            throw new IntegracionExternaException("GeoPagos no devolvió datos de contribuyentes");
        }

        List<Dato> contactos = new ArrayList<>();
        respuesta.getData().stream()
                .filter(dto -> cmsConDeuda.contains(convertirAString(dto.cm())))
                .forEach(dto -> guardarOActualizar(dto, contactos));
        sincronizacionContactosService.sincronizar(contactos);
        return cmsConDeuda;
    }

    private void guardarOActualizar(GeoPagosContribuyenteDto dto, List<Dato> contactos) {

        String cm = convertirAString(dto.cm());

        if (cm == null) {
            return;
        }

        Padron padron = padronRepository.findByCm(cm).orElseGet(Padron::new);
        Contribuyente contribuyente = padron.getContribuyente();
        if (contribuyente == null) {
            contribuyente = new Contribuyente();
        }
        String nombre = dto.nombrePersonas();
        boolean cambioPersona = nombre != null && !nombre.isBlank()
                && contribuyente.getNombre() != null && !contribuyente.getNombre().equals(nombre);
        if (nombre != null && !nombre.isBlank()) {
            // El documento procede de otra consulta: no conservar el de una persona anterior.
            if (cambioPersona) {
                contribuyente.setDocumento(null);
            }
            contribuyente.setNombre(nombre);
        } else if (contribuyente.getNombre() == null) {
            contribuyente.setNombre("(sin nombre)");
        }
        contribuyente = contribuyenteRepository.save(contribuyente);

        if (cambioPersona) {
            contactos.add(new Dato(contribuyente, TipoContacto.TELEFONO,
                    SincronizacionContactosService.PENDIENTES, null, true));
            contactos.add(new Dato(contribuyente, TipoContacto.EMAIL,
                    SincronizacionContactosService.PENDIENTES, null, true));
            contactos.add(new Dato(contribuyente, TipoContacto.DOMICILIO,
                    SincronizacionContactosService.PENDIENTES, null, true));
        }

        padron.setCm(cm);
        padron.setNumeroPadron(convertirAString(dto.numeroPadron()));
        padron.setTipoPadron(dto.tipoPadron());
        padron.setLocalidad(dto.localidad());
        padron.setBlock(convertirAString(dto.block()));
        padron.setUnidad(convertirAString(dto.unidad()));
        padron.setContribuyente(contribuyente);

        padronRepository.save(padron);

        contactos.add(new Dato(contribuyente, TipoContacto.TELEFONO,
                SincronizacionContactosService.PERSONAS, dto.telefonoPersonas(), true));
        contactos.add(new Dato(contribuyente, TipoContacto.EMAIL,
                SincronizacionContactosService.PERSONAS, dto.emailPersonas(), true));
        // GeoPagos registra los datos de la última transacción, que puede ser de otra persona.
        contactos.add(new Dato(contribuyente, TipoContacto.TELEFONO,
                SincronizacionContactosService.GEOPAGOS, dto.telefonoGeoPagos(), false));
        contactos.add(new Dato(contribuyente, TipoContacto.EMAIL,
                SincronizacionContactosService.GEOPAGOS, dto.emailGeoPagos(), false));
    }

    private String convertirAString(Object valor) {
        if (valor == null) {
            return null;
        }
        String texto = valor instanceof Number numero
                ? new BigDecimal(numero.toString()).stripTrailingZeros().toPlainString()
                : valor.toString().trim();
        return texto.isEmpty() ? null : texto;
    }
}
