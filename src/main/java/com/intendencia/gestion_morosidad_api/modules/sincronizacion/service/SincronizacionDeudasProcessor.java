package com.intendencia.gestion_morosidad_api.modules.sincronizacion.service;

import com.intendencia.gestion_morosidad_api.integration.intendencia.geopagos.dto.GeoPagosContribuyenteDto;
import com.intendencia.gestion_morosidad_api.integration.intendencia.geopagos.dto.GeoPagosFacturaCanceladaDto;
import com.intendencia.gestion_morosidad_api.integration.intendencia.geopagos.dto.GeoPagosFacturaPendienteDto;
import com.intendencia.gestion_morosidad_api.modules.contacto.TipoContacto;
import com.intendencia.gestion_morosidad_api.modules.contacto.service.SincronizacionContactosService;
import com.intendencia.gestion_morosidad_api.modules.contacto.service.SincronizacionContactosService.Dato;
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
    private final SincronizacionContactosService sincronizacionContactosService;
    private final TributoRepository tributoRepository;
    private final ConfiguracionSegmentoService configuracionSegmentoService;

    @Transactional
    public ResultadoSincronizacion procesar(
            List<GeoPagosFacturaPendienteDto> pendientes,
            List<GeoPagosFacturaCanceladaDto> canceladas,
            List<GeoPagosContribuyenteDto> contribuyentes,
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
        aplicarContactosDeContribuyentes(contribuyentes, ctx);

        // Guardar lo nuevo respetando las dependencias (lo existente se actualiza solo al terminar la transacción)
        tributoRepository.saveAll(ctx.tributosNuevos);
        contribuyenteRepository.saveAll(ctx.contribuyentesNuevos);
        padronRepository.saveAll(ctx.padronesNuevos);
        deudaRepository.saveAll(ctx.deudasNuevas);
        sincronizacionContactosService.sincronizar(ctx.contactosPendientes);

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
        if (fila.importeDeuda() == null) {
            // Un importe desconocido no demuestra que se haya saldado una deuda existente.
            ctx.omitir("CM " + cm + ": importe de deuda no informado");
            return;
        }
        if (fila.importeDeuda().signum() <= 0) {
            // No crear contribuyentes ni padrones sin saldo vencido positivo.
            // Si existía una deuda, conservarla cancelada junto con sus gestiones.
            Deuda existente = ctx.deudasPorCm.get(cm);
            if (existente != null) {
                existente.setImporte(fila.importeDeuda());
                cancelarDeuda(existente, ctx);
            }
            ctx.omitir("CM " + cm + ": sin deuda vencida positiva");
            return;
        }
        Padron padron = ctx.padronesPorCm.get(cm);
        if (padron == null) {
            padron = new Padron();
            padron.setCm(cm);
            ctx.padronesPorCm.put(cm, padron);
            ctx.padronesNuevos.add(padron);
        }

        String documento = limitar(texto(fila.documento()), 50);
        String documentoClave = documento == null ? null : documento.trim();

        Contribuyente contribuyente = documentoClave == null
                ? null
                : ctx.contribuyentesPorDocumento.get(documentoClave);

        if (contribuyente == null) {
            Contribuyente actual = padron.getContribuyente();

            if (actual != null
                    && (documentoClave == null
                    || actual.getDocumento() == null
                    || actual.getDocumento().isBlank()
                    || actual.getDocumento().trim().equals(documentoClave))) {
                contribuyente = actual;
            } else {
                contribuyente = new Contribuyente();
                ctx.contribuyentesNuevos.add(contribuyente);
            }
        }

        String nombre = limitar(texto(fila.persona()), 255);

        if (nombre != null) {
            contribuyente.setNombre(nombre);
        } else if (contribuyente.getNombre() == null) {
            contribuyente.setNombre(SIN_NOMBRE);
        }

        if (documento != null) {
            contribuyente.setDocumento(documento);
            ctx.contribuyentesPorDocumento.putIfAbsent(
                    documentoClave, contribuyente
            );
        }

        ctx.contactosPendientes.add(new Dato(contribuyente, TipoContacto.TELEFONO,
                SincronizacionContactosService.PENDIENTES, texto(fila.telefono()), true));
        ctx.contactosPendientes.add(new Dato(contribuyente, TipoContacto.EMAIL,
                SincronizacionContactosService.PENDIENTES, fila.email(), true));
        String calle = texto(fila.direccion());
        String puerta = texto(fila.numeroPuerta());
        String domicilio = calle == null ? puerta : puerta == null ? calle : calle + " " + puerta;
        ctx.contactosPendientes.add(new Dato(contribuyente, TipoContacto.DOMICILIO,
                SincronizacionContactosService.PENDIENTES, domicilio, true));

        padron.setNumeroPadron(limitar(numeroPadron, 100));
        padron.setTipoPadron(limitar(texto(fila.padtipo()), 100));
        padron.setLocalidad(limitar(texto(fila.localidad()), 255));
        padron.setBlock(limitar(texto(fila.block()), 100));
        padron.setUnidad(limitar(texto(fila.unidad()), 100));
        padron.setContribuyente(contribuyente);
        ctx.cmsImportados.add(cm);

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

    /**
     * Contactos de contribuyentes-geopagos: teléfono y correo de Personas (del contribuyente) y de la
     * última transacción GeoPagos (puede ser de quien pagó, no del titular). Solo para los padrones
     * con deuda importados en esta corrida; el nombre y el documento vienen de facturas/pendientes,
     * que los trae juntos, y de acá solo se toma el nombre si faltaba.
     */
    private void aplicarContactosDeContribuyentes(List<GeoPagosContribuyenteDto> contribuyentes, Contexto ctx) {
        if (contribuyentes == null) {
            ctx.advertir("No se pudo consultar contribuyentes-geopagos: los contactos de Personas y GeoPagos "
                    + "se actualizarán en la próxima sincronización");
            return;
        }
        Map<String, GeoPagosContribuyenteDto> porCm = new HashMap<>();
        for (GeoPagosContribuyenteDto dto : contribuyentes) {
            String cm = texto(dto.cm());
            if (cm != null) {
                porCm.put(cm, dto);
            }
        }
        for (String cm : ctx.cmsImportados) {
            GeoPagosContribuyenteDto dto = porCm.get(cm);
            if (dto == null) {
                continue;
            }
            Contribuyente contribuyente = ctx.padronesPorCm.get(cm).getContribuyente();
            String nombrePersonas = limitar(texto(dto.nombrePersonas()), 255);
            if (SIN_NOMBRE.equals(contribuyente.getNombre()) && nombrePersonas != null) {
                contribuyente.setNombre(nombrePersonas);
            }
            ctx.contactosPendientes.add(new Dato(contribuyente, TipoContacto.TELEFONO,
                    SincronizacionContactosService.PERSONAS, texto(dto.telefonoPersonas()), true));
            ctx.contactosPendientes.add(new Dato(contribuyente, TipoContacto.EMAIL,
                    SincronizacionContactosService.PERSONAS, texto(dto.emailPersonas()), true));
            ctx.contactosPendientes.add(new Dato(contribuyente, TipoContacto.TELEFONO,
                    SincronizacionContactosService.GEOPAGOS, texto(dto.telefonoGeoPagos()), false));
            ctx.contactosPendientes.add(new Dato(contribuyente, TipoContacto.EMAIL,
                    SincronizacionContactosService.GEOPAGOS, texto(dto.emailGeoPagos()), false));
        }
    }

    /** Una deuda que ya no aparece en pendientes dejó de estar vencida e impaga. */
    private void cancelarDeudasQueYaNoEstanPendientes(Contexto ctx) {
        ctx.deudasPorCm.forEach((cm, deuda) -> {
            if (!ctx.cmsProcesados.contains(cm) && deuda.getEstado() != EstadoDeuda.CANCELADA) {
                cancelarDeuda(deuda, ctx);
            }
        });
    }

    private void cancelarDeuda(Deuda deuda, Contexto ctx) {
        if (deuda.getEstado() != EstadoDeuda.CANCELADA) {
            ctx.canceladas++;
        }
        deuda.setEstado(EstadoDeuda.CANCELADA);
        deuda.setSegmentoMora(null);
        deuda.setFechaSincronizacion(ctx.ahora);
    }

    private void registrarCobros(List<GeoPagosFacturaCanceladaDto> canceladas, Contexto ctx) {
        for (GeoPagosFacturaCanceladaDto cobro : canceladas) {
            String cm = texto(cobro.cm());
            // La consulta de contribuyentes no incluye DOCUMENTO. Un cobro reciente puede
            // completarlo para un padrón que ya no tiene facturas pendientes.
            if (!ctx.cmsProcesados.contains(cm)) {
                Padron padron = ctx.padronesPorCm.get(cm);
                if (padron != null && padron.getContribuyente() != null) {
                    Contribuyente contribuyente = padron.getContribuyente();
                    String nombre = texto(cobro.persona());
                    if (nombre != null) {
                        if (!nombre.equals(contribuyente.getNombre())) {
                            contribuyente.setDocumento(null);
                        }
                        contribuyente.setNombre(limitar(nombre, 255));
                    }
                    String documento = texto(cobro.documento());
                    if (documento != null) {
                        contribuyente.setDocumento(limitar(documento, 50));
                    }
                }
            }
            Deuda deuda = ctx.deudasPorCm.get(cm);
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

        final Map<String, Contribuyente> contribuyentesPorDocumento =
                contribuyenteRepository.findAll().stream()
                        .filter(c -> c.getDocumento() != null
                                && !c.getDocumento().isBlank())
                        .collect(Collectors.toMap(
                                c -> c.getDocumento().trim(),
                                Function.identity(),
                                (a, b) -> a
                        ));

        final Map<String, Deuda> deudasPorCm =
                porClave(deudaRepository.findAllConPadron(), deuda -> deuda.getPadron().getCm());
        final Set<String> cmsProcesados = new HashSet<>();
        /** CM con deuda positiva que quedaron guardados en esta corrida. */
        final Set<String> cmsImportados = new HashSet<>();
        final List<Tributo> tributosNuevos = new ArrayList<>();
        final List<Contribuyente> contribuyentesNuevos = new ArrayList<>();
        final List<Dato> contactosPendientes = new ArrayList<>();
        final List<Padron> padronesNuevos = new ArrayList<>();
        final List<Deuda> deudasNuevas = new ArrayList<>();
        final List<String> advertencias = new ArrayList<>();
        int actualizadas;
        int canceladas;
        int omitidos;

        Contexto(LocalDate hoy, LocalDateTime ahora) {
            this.hoy = hoy;
            this.ahora = ahora;
        }

        /** Aviso que no implica omitir un registro. */
        void advertir(String mensaje) {
            if (advertencias.size() < MAX_ADVERTENCIAS) {
                advertencias.add(mensaje);
            }
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
