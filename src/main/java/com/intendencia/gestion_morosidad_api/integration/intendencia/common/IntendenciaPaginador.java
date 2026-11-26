package com.intendencia.gestion_morosidad_api.integration.intendencia.common;

import com.intendencia.gestion_morosidad_api.shared.exception.IntegracionExternaException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;

/** Descarga y valida la consulta completa antes de entregar datos a la conciliacion. */
@Component
@Slf4j
public class IntendenciaPaginador {
    private static final int MAX_PAGINAS = 10_000;
    private final RestClient client;
    private final int tamanioPagina;

    public IntendenciaPaginador(RestClient client, @Value("${geopagos.page-size:1000}") int tamanioPagina) {
        if (tamanioPagina < 1 || tamanioPagina > 1000)
            throw new IllegalArgumentException("geopagos.page-size debe estar entre 1 y 1000");
        this.client = client;
        this.tamanioPagina = tamanioPagina;
    }

    public <T> IntendenciaApiResponse<T> obtenerTodo(String ruta, Map<String, ?> parametros,
            ParameterizedTypeReference<IntendenciaApiResponse<T>> tipo, Function<T, Object> identificadorUnico) {
        var filas = new ArrayList<T>();
        Set<String> identificadores = new HashSet<>();
        Long total = null;
        Integer tamanioReal = null;
        Object parametrosRespuesta = null;
        for (int pagina = 1; pagina <= MAX_PAGINAS; pagina++) {
            var respuesta = descargar(ruta,parametros,pagina,false,tipo,true);
            if (respuesta == null || respuesta.getData() == null || respuesta.getMeta() == null)
                throw incompleta(ruta, pagina, "faltan datos o metadatos de paginacion");
            var meta = respuesta.getMeta();
            if (meta.getTotal() == null || meta.getTotal() < 0 || meta.getTotal() > Integer.MAX_VALUE
                    || meta.getCurrentPage() == null || meta.getCurrentPage() != pagina
                    || meta.getPerPage() == null || meta.getPerPage() < 1 || meta.getPerPage() > tamanioPagina
                    || meta.getLastPage() == null || meta.getHasMore() == null)
                throw incompleta(ruta, pagina, "metadatos de paginacion invalidos");
            if (total == null) {
                total = meta.getTotal();
                tamanioReal = meta.getPerPage();
                parametrosRespuesta = meta.getParameters();
            }
            if (!total.equals(meta.getTotal()) || !tamanioReal.equals(meta.getPerPage()))
                throw incompleta(ruta, pagina, "cambio el total o el tamanio de pagina durante la descarga");
            long ultima = Math.max(1L, (total + tamanioReal - 1L) / tamanioReal);
            if (ultima > MAX_PAGINAS || meta.getLastPage() != ultima || pagina > ultima
                    || meta.getHasMore() != (pagina < ultima))
                throw incompleta(ruta, pagina, "el final de la paginacion no es consistente");
            long desplazamiento = (long) (pagina - 1) * tamanioReal;
            long esperadas = Math.min(tamanioReal, total - desplazamiento);
            if (respuesta.getData().size() != esperadas)
                throw incompleta(ruta, pagina, "la pagina no contiene los registros anunciados");
            if (total == 0 ? meta.getFrom() != null || meta.getTo() != null
                    : !Long.valueOf(desplazamiento + 1).equals(meta.getFrom())
                    || !Long.valueOf(desplazamiento + esperadas).equals(meta.getTo()))
                throw incompleta(ruta, pagina, "los limites from/to no corresponden a la pagina");
            for (T fila : respuesta.getData()) {
                if (fila == null) throw incompleta(ruta, pagina, "hay una fila vacia");
                if (identificadorUnico != null) {
                    String id = normalizar(identificadorUnico.apply(fila));
                    if (id == null || !identificadores.add(id))
                        throw incompleta(ruta, pagina, "hay identificadores ausentes o repetidos entre paginas");
                }
                filas.add(fila);
            }
            SeguimientoSincronizacion.pagina(pagina, filas.size(), total, Math.toIntExact(ultima), meta.getHasMore());
            if (pagina == 1 || pagina % 10 == 0 || pagina == ultima)
                log.info("API {}: pagina {}/{}, {} de {} registros descargados", ruta, pagina, ultima, filas.size(), total);
            if (pagina == ultima)
                return new IntendenciaApiResponse<>(filas, new IntendenciaApiMeta(total, parametrosRespuesta));
        }
        throw incompleta(ruta, MAX_PAGINAS, "se excedio el maximo de paginas");
    }

    /** La fuente puede omitir el COUNT costoso; has_more define el final. Nunca entrega páginas parciales al procesador. */
    public <T> IntendenciaApiResponse<T> obtenerSinTotal(String ruta, Map<String, ?> parametros,
            ParameterizedTypeReference<IntendenciaApiResponse<T>> tipo, Function<T, Object> identificadorUnico) {
        return obtenerSinTotal(ruta, parametros, tipo, identificadorUnico, null);
    }

    public <T> IntendenciaApiResponse<T> obtenerPorCm(String ruta, Map<String, String> parametros,
            ParameterizedTypeReference<IntendenciaApiResponse<T>> tipo, Function<T, Object> identificadorUnico) {
        String cm = java.util.Objects.requireNonNull(parametros.get("cm"));
        return obtenerSinTotal(ruta, parametros, tipo, identificadorUnico, cm);
    }

    private <T> IntendenciaApiResponse<T> obtenerSinTotal(String ruta, Map<String, ?> parametros,
            ParameterizedTypeReference<IntendenciaApiResponse<T>> tipo, Function<T, Object> identificadorUnico,
            String cmEsperado) {
        boolean consultaIndividual = cmEsperado != null;
        var filas = new ArrayList<T>();
        Set<String> ids = new HashSet<>();
        Integer tamanioReal = null;
        Object parametrosRespuesta = null;
        for (int pagina=1; pagina<=MAX_PAGINAS; pagina++) {
            var respuesta = descargar(ruta,parametros,pagina,true,tipo,!consultaIndividual);
            if (respuesta == null || respuesta.getData() == null || respuesta.getMeta() == null)
                throw incompleta(ruta,pagina,"faltan datos o metadatos");
            var m = respuesta.getMeta(); int cantidad = respuesta.getData().size();
            if (m.getCurrentPage() == null || m.getCurrentPage() != pagina || m.getPerPage() == null
                || m.getPerPage() < 1 || m.getPerPage() > tamanioPagina || m.getHasMore() == null)
                throw incompleta(ruta,pagina,"metadatos inválidos");
            if (consultaIndividual && (cantidad > 1 || m.getHasMore()))
                throw incompleta(ruta,pagina,"la consulta por CM devolvió más de una cuenta");
            if (tamanioReal == null) { tamanioReal=m.getPerPage(); parametrosRespuesta=m.getParameters(); }
            if (!tamanioReal.equals(m.getPerPage()) || cantidad>tamanioReal
                || (m.getHasMore() && cantidad!=tamanioReal) || (pagina>1 && cantidad==0))
                throw incompleta(ruta,pagina,"página incompleta o tamaño inestable");
            long inicio = filas.size()+1L;
            if (cantidad==0 ? m.getFrom()!=null || m.getTo()!=null
                : !Long.valueOf(inicio).equals(m.getFrom()) || !Long.valueOf(inicio+cantidad-1).equals(m.getTo()))
                throw incompleta(ruta,pagina,"límites from/to inconsistentes");
            for (T fila:respuesta.getData()) {
                if (fila == null) throw incompleta(ruta,pagina,"fila vacía");
                if (identificadorUnico != null) {
                    String id = normalizar(identificadorUnico.apply(fila));
                    if (consultaIndividual && !cmEsperado.equals(id))
                        throw incompleta(ruta,pagina,"la consulta devolvió un CM distinto al solicitado");
                    if (id == null || !ids.add(id)) throw incompleta(ruta,pagina,"identificador ausente o repetido");
                }
                filas.add(fila);
            }
            if (!m.getHasMore() && m.getTotal()!=null && m.getTotal()!=filas.size())
                throw incompleta(ruta,pagina,"total inconsistente");
            if (!consultaIndividual) {
                SeguimientoSincronizacion.pagina(pagina,filas.size(),m.getTotal(),m.getLastPage(),m.getHasMore());
                if (pagina==1 || pagina%10==0 || !m.getHasMore())
                    log.info("API {}: página {}, {} registros descargados, quedan páginas: {}",ruta,pagina,filas.size(),m.getHasMore());
            }
            if (!m.getHasMore()) {
                return new IntendenciaApiResponse<>(filas,new IntendenciaApiMeta((long)filas.size(),parametrosRespuesta));
            }
        }
        throw incompleta(ruta,MAX_PAGINAS,"se excedió el máximo de páginas");
    }

    private <T> IntendenciaApiResponse<T> descargar(String ruta, Map<String, ?> parametros, int pagina,
            boolean omitirTotal, ParameterizedTypeReference<IntendenciaApiResponse<T>> tipo, boolean publicarAvance) {
        if (publicarAvance) SeguimientoSincronizacion.solicitando(pagina);
        for (int intento=1; ; intento++) {
            SeguimientoSincronizacion.comprobarCancelacion();
            try {
                var respuesta = client.get().uri(uri -> {
                    uri.path(ruta); parametros.forEach((k,v) -> uri.queryParam(k,v));
                    uri.queryParam("page",pagina).queryParam("per_page",tamanioPagina);
                    if (omitirTotal) uri.queryParam("include_total",0);
                    return uri.build();
                }).retrieve().body(tipo);
                SeguimientoSincronizacion.comprobarCancelacion();
                return respuesta;
            } catch (RestClientResponseException e) {
                SeguimientoSincronizacion.comprobarCancelacion();
                int status=e.getStatusCode().value();
                if (intento>=3 || (status!=502 && status!=503 && status!=504)) throw e;
                log.warn("API {}: HTTP {} en página {}, reintento {}/2",ruta,status,pagina,intento);
            } catch (ResourceAccessException e) {
                SeguimientoSincronizacion.comprobarCancelacion();
                if (intento>=3) throw e;
                log.warn("API {}: fallo de conexión en página {}, reintento {}/2",ruta,pagina,intento);
            }
            if (publicarAvance) SeguimientoSincronizacion.reintentando(pagina,intento+1);
            try { Thread.sleep(1000L*intento); }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                SeguimientoSincronizacion.comprobarCancelacion();
                throw incompleta(ruta,pagina,"se interrumpió la descarga");
            }
        }
    }

    private static String normalizar(Object valor) {
        if (valor == null) return null;
        String texto = valor instanceof Number n ? new BigDecimal(n.toString()).stripTrailingZeros().toPlainString()
                : valor.toString().trim();
        return texto.isBlank() ? null : texto;
    }

    private static IntegracionExternaException incompleta(String ruta, int pagina, String motivo) {
        return new IntegracionExternaException("Consulta incompleta de " + ruta + " (pagina " + pagina + "): " + motivo
                + ". No se conciliaron deudas con esta respuesta.");
    }
}
