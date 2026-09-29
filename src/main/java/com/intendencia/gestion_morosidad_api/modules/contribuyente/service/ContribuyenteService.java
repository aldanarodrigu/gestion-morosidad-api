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
import com.intendencia.gestion_morosidad_api.modules.padron.repository.PadronRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ContribuyenteService {

    private final GeoPagosClient geoPagosClient;
    private final ContribuyenteRepository contribuyenteRepository;
    private final PadronRepository padronRepository;
    private final SincronizacionContactosService sincronizacionContactosService;

    @Transactional
    public List<ContribuyenteResponse> listarContribuyentes() {

        sincronizarDesdeGeoPagos();

        return padronRepository.findAll()
                .stream()
                .map(ContribuyenteResponse::de)
                .toList();
    }

    @Transactional
    public Optional<ContribuyenteResponse> buscarPorCm(String cm) {

        Optional<Padron> padron = padronRepository.findByCm(cm);

        if (padron.isEmpty()) {
            sincronizarDesdeGeoPagos();
            padron = padronRepository.findByCm(cm);
        }

        return padron.map(ContribuyenteResponse::de);
    }

    @Transactional
    public void sincronizarDesdeGeoPagos() {

        var respuesta = geoPagosClient.obtenerContribuyentes();

        if (respuesta == null || respuesta.getData() == null) {
            return;
        }

        List<Dato> contactos = new ArrayList<>();
        respuesta.getData().forEach(dto -> guardarOActualizar(dto, contactos));
        sincronizacionContactosService.sincronizar(contactos);
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
        return valor != null ? valor.toString() : null;
    }
}
