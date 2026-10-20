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
            int solicitada = pagina;
            var respuesta = client.get().uri(uri -> {
                uri.path(ruta);
                parametros.forEach((clave, valor) -> uri.queryParam(clave, valor));
                return uri.queryParam("page", solicitada).queryParam("per_page", tamanioPagina).build();
            }).retrieve().body(tipo);
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
            if (pagina == 1 || pagina % 10 == 0 || pagina == ultima)
                log.info("API {}: pagina {}/{}, {} de {} registros descargados", ruta, pagina, ultima, filas.size(), total);
            if (pagina == ultima)
                return new IntendenciaApiResponse<>(filas, new IntendenciaApiMeta(total, parametrosRespuesta));
        }
        throw incompleta(ruta, MAX_PAGINAS, "se excedio el maximo de paginas");
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
