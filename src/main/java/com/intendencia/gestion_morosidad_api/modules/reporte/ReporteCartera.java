package com.intendencia.gestion_morosidad_api.modules.reporte;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
public record ReporteCartera(LocalDateTime generado, Totales totales, List<Segmento> porSegmento,
        List<Tipo> porTipo, List<Grupo> resumen) {
    public record Totales(BigDecimal importe, long contribuyentes, long padrones, long deudas) {}
    public record Segmento(Long id, String nombre, Integer orden, long cantidad, BigDecimal importe) {}
    public record Tipo(String tipo, long cantidad, BigDecimal importe) {}
    public record Grupo(String origen, String tipo, long personas, long cantidad, BigDecimal importe) {}
}
