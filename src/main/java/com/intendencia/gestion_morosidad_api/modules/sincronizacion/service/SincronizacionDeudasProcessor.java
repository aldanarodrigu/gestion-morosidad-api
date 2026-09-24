package com.intendencia.gestion_morosidad_api.modules.sincronizacion.service;

import com.intendencia.gestion_morosidad_api.integration.intendencia.geopagos.dto.GeoPagosFacturaCanceladaDto;
import com.intendencia.gestion_morosidad_api.integration.intendencia.geopagos.dto.GeoPagosFacturaPendienteDto;
import com.intendencia.gestion_morosidad_api.modules.contribuyente.entity.Contribuyente;
import com.intendencia.gestion_morosidad_api.modules.contribuyente.repository.ContribuyenteRepository;
import com.intendencia.gestion_morosidad_api.modules.deuda.entity.Deuda;
import com.intendencia.gestion_morosidad_api.modules.deuda.entity.EstadoDeuda;
import com.intendencia.gestion_morosidad_api.modules.deuda.repository.DeudaRepository;
import com.intendencia.gestion_morosidad_api.modules.padron.entity.Padron;
import com.intendencia.gestion_morosidad_api.modules.padron.repository.PadronRepository;
import com.intendencia.gestion_morosidad_api.modules.segmento.service.ConfiguracionSegmentoService;
import com.intendencia.gestion_morosidad_api.modules.segmento.service.ReglasSegmentacion;
import com.intendencia.gestion_morosidad_api.modules.sincronizacion.dto.ResultadoSincronizacion;
import com.intendencia.gestion_morosidad_api.modules.tributo.entity.Tributo;
import com.intendencia.gestion_morosidad_api.modules.tributo.repository.TributoRepository;
import com.intendencia.gestion_morosidad_api.modules.tributo.service.CodigosTributo;
import com.intendencia.gestion_morosidad_api.shared.exception.IntegracionExternaException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aplica en la base, en una sola transacción, los datos obtenidos de GeoPagos.
 * Todo o nada: si algo falla no queda una sincronización a medias.
 * Carga las tablas en memoria una vez para no hacer consultas por cada fila.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class SincronizacionDeudasProcessor {

    private static final int MAX_ADVERTENCIAS = 100;
    private static final String SIN_NOMBRE = "(sin nombre)";

    private final DeudaRepository deudaRepository;
    private final PadronRepository padronRepository;
    private final ContribuyenteRepository contribuyenteRepository;
    private final TributoRepository tributoRepository;
    private final ConfiguracionSegmentoService configuracionSegmentoService;

    @Transactional
    public ResultadoSincronizacion procesar(
            List<GeoPagosFacturaPendienteDto> pendientes,
            List<GeoPagosFacturaCanceladaDto> canceladas,
            LocalDate hoy,
            LocalDateTime inicio) {

        Contexto ctx = new Contexto(hoy, LocalDateTime.now());

        if (pendientes.isEmpty() && deudaRepository.countByEstadoNot(EstadoDeuda.CANCELADA) > 0) {
            // Protección: una respuesta vacía por error del origen cancelaría todas las deudas
            throw new IntegracionExternaException(
                    "GeoPagos no devolvió deudas pendientes; no se aplicaron cambios. Verificar el servicio.");
        }

        for (GeoPagosFacturaPendienteDto fila : pendientes) {
            procesarPendiente(fila, ctx);
        }
        cancelarDeudasQueYaNoEstanPendientes(ctx);
        registrarCobros(canceladas, ctx);

        // Guardar lo nuevo respetando las dependencias (lo existente se actualiza solo al terminar la transacción)
        tributoRepository.saveAll(ctx.tributosNuevos);
        contribuyenteRepository.saveAll(ctx.contribuyentesNuevos);
        padronRepository.saveAll(ctx.padronesNuevos);
        deudaRepository.saveAll(ctx.deudasNuevas);

        ResultadoSincronizacion resultado = new ResultadoSincronizacion(
                inicio, LocalDateTime.now(), pendientes.size(), canceladas.size(),
                ctx.deudasNuevas.size(), ctx.actualizadas, ctx.canceladas, ctx.omitidos,
                List.copyOf(ctx.advertencias));
        log.info("Sincronización GeoPagos: {} pendientes, {} creadas, {} actualizadas, {} canceladas, {} omitidas",
                resultado.pendientesRecibidas(), resultado.deudasCreadas(), resultado.deudasActualizadas(),
                resultado.deudasCanceladas(), resultado.registrosOmitidos());
        return resultado;
    }

    private void procesarPendiente(GeoPagosFacturaPendienteDto fila, Contexto ctx) {
        String cm = texto(fila.cm());
        String numeroPadron = texto(fila.numeroPadron());
        if (cm == null || numeroPadron == null) {
            ctx.omitir("Registro sin CM o sin número de padrón");
            return;
        }
        if (!ctx.cmsProcesados.add(cm)) {
            ctx.omitir("CM " + cm + " repetido en la respuesta de GeoPagos");
            return;
        }
        String cmConEseNumero = ctx.cmPorNumeroPadron.get(numeroPadron);
        if (cmConEseNumero != null && !cmConEseNumero.equals(cm)) {
            // padrones.numero_padron es único: dos CM con el mismo número no se pueden guardar
            ctx.omitir("CM " + cm + ": el número de padrón " + numeroPadron
                    + " ya está asociado al CM " + cmConEseNumero);
            return;
        }

        Padron padron = ctx.padronesPorCm.get(cm);
        if (padron == null) {
            padron = new Padron();
            padron.setCm(cm);
            ctx.padronesPorCm.put(cm, padron);
            ctx.padronesNuevos.add(padron);
        } else if (padron.getNumeroPadron() != null) {
            ctx.cmPorNumeroPadron.remove(padron.getNumeroPadron());
        }

        Contribuyente contribuyente = padron.getContribuyente();
        if (contribuyente == null) {
            contribuyente = new Contribuyente();
            ctx.contribuyentesNuevos.add(contribuyente);
        }
        contribuyente.setNombre(Objects.requireNonNullElse(limitar(texto(fila.persona()), 255), SIN_NOMBRE));
        contribuyente.setDocumento(limitar(texto(fila.documento()), 50));

        padron.setNumeroPadron(limitar(numeroPadron, 100));
        padron.setTipoPadron(limitar(texto(fila.padtipo()), 100));
        padron.setLocalidad(limitar(texto(fila.localidad()), 255));
        padron.setBlock(limitar(texto(fila.block()), 100));
        padron.setUnidad(limitar(texto(fila.unidad()), 100));
        padron.setContribuyente(contribuyente);
        ctx.cmPorNumeroPadron.put(numeroPadron, cm);

        Deuda deuda = ctx.deudasPorCm.get(cm);
        if (deuda == null) {
            deuda = new Deuda();
            deuda.setPadron(padron);
            ctx.deudasPorCm.put(cm, deuda);
            ctx.deudasNuevas.add(deuda);
        } else {
            ctx.actualizadas++;
        }

        boolean convenio = "SI".equalsIgnoreCase(texto(fila.convenio()));
        deuda.setImporte(fila.importeDeuda());
        deuda.setDeudaDesde(fila.deudaDesde());
        deuda.setUltimoVencimiento(fila.ultimoVencimiento());
        deuda.setAniosDeuda(limitar(texto(fila.aniosDeuda()), 200));
        deuda.setConvenio(convenio);
        actualizarTributos(deuda, CodigosTributo.parsear(fila.tributosDeuda()), ctx);
        deuda.setEstado(ReglasEstadoDeuda.enPendientes(deuda.getEstado(), convenio));
        deuda.setSegmentoMora(ctx.reglas.segmentoPara(deuda.getDeudaDesde(), ctx.hoy));
        deuda.setFechaSincronizacion(ctx.ahora);
    }

    /** Solo modifica la colección si cambió: evita reescribir deuda_tributo en cada sincronización. */
    private void actualizarTributos(Deuda deuda, Set<String> codigos, Contexto ctx) {
        Set<String> actuales = deuda.getTributos().stream().map(Tributo::getCodigo).collect(Collectors.toSet());
        if (actuales.equals(codigos)) {
            return;
        }
        deuda.getTributos().clear();
        for (String codigo : codigos) {
            deuda.getTributos().add(ctx.tributosPorCodigo.computeIfAbsent(limitar(codigo, 20), nuevo -> {
                Tributo tributo = new Tributo();
                tributo.setCodigo(nuevo);
                ctx.tributosNuevos.add(tributo);
                return tributo;
            }));
        }
    }

    /** Una deuda que ya no aparece en pendientes dejó de estar vencida e impaga. */
    private void cancelarDeudasQueYaNoEstanPendientes(Contexto ctx) {
        ctx.deudasPorCm.forEach((cm, deuda) -> {
            if (!ctx.cmsProcesados.contains(cm) && deuda.getEstado() != EstadoDeuda.CANCELADA) {
                deuda.setEstado(EstadoDeuda.CANCELADA);
                deuda.setSegmentoMora(null);
                deuda.setFechaSincronizacion(ctx.ahora);
                ctx.canceladas++;
            }
        });
    }

    private void registrarCobros(List<GeoPagosFacturaCanceladaDto> canceladas, Contexto ctx) {
        for (GeoPagosFacturaCanceladaDto cobro : canceladas) {
            Deuda deuda = ctx.deudasPorCm.get(texto(cobro.cm()));
            if (deuda == null || cobro.fechaCobro() == null) {
                continue;
            }
            if (deuda.getFechaUltimoCobro() == null || !cobro.fechaCobro().isBefore(deuda.getFechaUltimoCobro())) {
                deuda.setFechaUltimoCobro(cobro.fechaCobro());
                deuda.setImporteUltimoCobro(cobro.importeTotal());
            }
        }
    }

    /** CM, padrón, documento, etc. pueden llegar como número o texto: siempre se guardan como texto. */
    static String texto(Object valor) {
        if (valor == null) {
            return null;
        }
        String texto = valor instanceof Number numero
                ? new BigDecimal(numero.toString()).stripTrailingZeros().toPlainString()
                : valor.toString().trim();
        return texto.isEmpty() ? null : texto;
    }

    private static String limitar(String valor, int largo) {
        return valor == null || valor.length() <= largo ? valor : valor.substring(0, largo);
    }

    /** Estado de una ejecución: índices en memoria, entidades nuevas y contadores. */
    private final class Contexto {
        final LocalDate hoy;
        final LocalDateTime ahora;
        final ReglasSegmentacion reglas = configuracionSegmentoService.reglasVigentes();
        final Map<String, Tributo> tributosPorCodigo = porClave(tributoRepository.findAll(), Tributo::getCodigo);
        final Map<String, Padron> padronesPorCm = porClave(padronRepository.findAll(), Padron::getCm);
        final Map<String, String> cmPorNumeroPadron = new HashMap<>();
        final Map<String, Deuda> deudasPorCm =
                porClave(deudaRepository.findAllConPadron(), deuda -> deuda.getPadron().getCm());
        final Set<String> cmsProcesados = new HashSet<>();
        final List<Tributo> tributosNuevos = new ArrayList<>();
        final List<Contribuyente> contribuyentesNuevos = new ArrayList<>();
        final List<Padron> padronesNuevos = new ArrayList<>();
        final List<Deuda> deudasNuevas = new ArrayList<>();
        final List<String> advertencias = new ArrayList<>();
        int actualizadas;
        int canceladas;
        int omitidos;

        Contexto(LocalDate hoy, LocalDateTime ahora) {
            this.hoy = hoy;
            this.ahora = ahora;
            padronesPorCm.values().forEach(padron -> cmPorNumeroPadron.put(padron.getNumeroPadron(), padron.getCm()));
        }

        void omitir(String motivo) {
            omitidos++;
            if (advertencias.size() < MAX_ADVERTENCIAS) {
                advertencias.add(motivo);
            }
        }

        private static <T> Map<String, T> porClave(List<T> elementos, Function<T, String> clave) {
            Map<String, T> mapa = new HashMap<>();
            elementos.forEach(elemento -> mapa.put(clave.apply(elemento), elemento));
            return mapa;
        }
    }
}
