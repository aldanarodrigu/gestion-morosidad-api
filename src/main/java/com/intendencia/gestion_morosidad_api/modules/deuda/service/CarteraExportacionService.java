package com.intendencia.gestion_morosidad_api.modules.deuda.service;

import com.intendencia.gestion_morosidad_api.modules.deuda.dto.*;
import com.intendencia.gestion_morosidad_api.shared.enums.OrigenDatos;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CarteraExportacionService {
    private final DeudaService deudaService;
    private final CarteraAgrupadaService agrupadaService;
    private static final int LOTE = 500;
    private static final Pattern FORMULA_CSV = Pattern.compile("^[\\s\\p{Z}\\p{Cc}]*[=+@-]");
    private static final String[] COLUMNAS = {"CM", "N.º padrón", "Contribuyente", "Documento",
            "Origen", "PadTipo", "Tributo", "Segmento", "Días vencido", "Deuda", "Gestiones",
            "Acción sugerida", "Convenio", "Contacto", "Matrícula", "Facturas vencidas consideradas", "Facturas con importe positivo", "Año desde", "Año hasta", "Cantidad de años con deuda", "Años con deuda"};

    // Todas las páginas se consultan sobre la misma instantánea de la base local.
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public byte[] exportar(DeudaFiltro filtro, Sort orden, boolean agrupar) throws IOException {
        try (var libro = new SXSSFWorkbook(100); var salida = new ByteArrayOutputStream()) {
            libro.setCompressTempFiles(true);
            var cabecera = libro.createCellStyle();
            cabecera.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            cabecera.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            var fuente = libro.createFont();
            fuente.setBold(true);
            fuente.setColor(IndexedColors.WHITE.getIndex());
            cabecera.setFont(fuente);
            var dinero = libro.createCellStyle();
            dinero.setDataFormat(libro.createDataFormat().getFormat("#,##0.00"));
            Sheet resumen = agrupar ? hoja(libro, "Contribuyentes", cabecera,
                    "Documento", "Contribuyente", "Deudas", "Segmento más avanzado",
                    "Días máx. vencido", "Deuda total") : null;
            Sheet detalle = hoja(libro, "Cartera", cabecera, COLUMNAS);
            Sort estable = orden.isSorted() ? orden : Sort.by("deudaDesde");
            if (!agrupar && estable.getOrderFor("id") == null) estable = estable.and(Sort.by("id"));
            int pagina = 0;
            int fila = 1;
            int filaResumen = 1;
            int totalPaginas;
            do {
                var pageable = PageRequest.of(pagina, LOTE, estable);
                if (agrupar) {
                    var datos = agrupadaService.listar(filtro, pageable);
                    totalPaginas = datos.totalPaginas();
                    for (var grupo : datos.contenido()) {
                        var r = resumen.createRow(filaResumen++);
                        valores(r, grupo.documento(), String.join(" / ", grupo.nombres()),
                                grupo.deudas().size(), grupo.segmentoMora() == null ? "Sin segmento" : grupo.segmentoMora().nombre(),
                                grupo.diasAtrasoMaximos(), grupo.importeTotal());
                        r.getCell(5).setCellStyle(dinero);
                        for (var deuda : grupo.deudas()) escribir(detalle.createRow(fila++), deuda, dinero);
                    }
                } else {
                    var datos = deudaService.listar(filtro, pageable);
                    totalPaginas = datos.totalPaginas();
                    for (var deuda : datos.contenido()) escribir(detalle.createRow(fila++), deuda, dinero);
                }
                pagina++;
            } while (pagina < totalPaginas);
            filtrar(detalle, fila, COLUMNAS.length);
            if (resumen != null) filtrar(resumen, filaResumen, 6);
            libro.write(salida);
            return salida.toByteArray();
        }
    }

    private static Sheet hoja(Workbook libro, String nombre, CellStyle estilo, String... columnas) {
        var hoja = libro.createSheet(nombre);
        var fila = hoja.createRow(0);
        for (int i = 0; i < columnas.length; i++) {
            var celda = fila.createCell(i);
            celda.setCellValue(columnas[i]);
            celda.setCellStyle(estilo);
            hoja.setColumnWidth(i, Math.min(45, Math.max(18, columnas[i].length() + 3)) * 256);
        }
        hoja.createFreezePane(0, 1);
        return hoja;
    }

    private static void filtrar(Sheet hoja, int filas, int columnas) {
        hoja.setAutoFilter(new CellRangeAddress(0, Math.max(0, filas - 1), 0, columnas - 1));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public byte[] exportarCsv(DeudaFiltro filtro, Sort orden, boolean agrupar) throws IOException {
        try (var salida = new ByteArrayOutputStream();
             var escritor = new OutputStreamWriter(salida, StandardCharsets.UTF_8)) {
            escritor.write('\uFEFF');
            escritor.write(lineaCsv((Object[]) COLUMNAS));
            Sort estable = orden.isSorted() ? orden : Sort.by("deudaDesde");
            if (!agrupar && estable.getOrderFor("id") == null) estable = estable.and(Sort.by("id"));
            int pagina = 0;
            int totalPaginas;
            do {
                var pageable = PageRequest.of(pagina++, LOTE, estable);
                List<DeudaResponse> deudas;
                if (agrupar) {
                    var datos = agrupadaService.listar(filtro, pageable);
                    totalPaginas = datos.totalPaginas();
                    // CSV tiene una sola tabla: conserva el detalle completo en el orden de los grupos.
                    deudas = datos.contenido().stream().flatMap(g -> g.deudas().stream()).toList();
                } else {
                    var datos = deudaService.listar(filtro, pageable);
                    totalPaginas = datos.totalPaginas();
                    deudas = datos.contenido();
                }
                for (var deuda : deudas) escritor.write(lineaCsv(datosFila(deuda)));
            } while (pagina < totalPaginas);
            escritor.flush();
            return salida.toByteArray();
        }
    }

    private static String lineaCsv(Object... valores) {
        return Arrays.stream(valores).map(valor -> {
            String texto = valor == null ? "" : valor.toString();
            if (valor instanceof String && FORMULA_CSV.matcher(texto).find()) texto = "'" + texto;
            return "\"" + texto.replace("\"", "\"\"") + "\"";
        }).collect(Collectors.joining(";", "", "\r\n"));
    }

    private static void escribir(Row fila, DeudaResponse d, CellStyle dinero) {
        valores(fila, datosFila(d));
        fila.getCell(9).setCellStyle(dinero);
    }

    private static Object[] datosFila(DeudaResponse d) {
        return new Object[]{d.padron().cm(), d.padron().numeroPadron(), d.contribuyente().nombre(),
                d.contribuyente().documento(), d.padron().origen() == OrigenDatos.SUCIVE ? "SUCIVE" : "Intendencia",
                d.padron().tipoPadron(), unir(d.tributos(), t -> t.descripcion() == null || t.descripcion().isBlank() ? t.codigo() : t.descripcion()),
                d.segmentoMora() == null ? "Sin segmento" : d.segmentoMora().nombre(),
                d.diasAtraso() == null ? 0L : d.diasAtraso(), d.importe(), d.cantidadGestiones(),
                unir(d.accionesSugeridas(), a -> a.nombre()), d.convenio() ? "Sí" : "No",
<<<<<<< Updated upstream
                d.conContacto() ? "Con datos" : "Sin datos", d.padron().matricula(),d.cantidadFacturasVencidas(),d.cantidadFacturasConDeuda(),d.anioDesde(),d.anioHasta(),d.cantidadAniosDeuda(),d.aniosDeuda());
        fila.getCell(9).setCellStyle(dinero);
=======
                d.conContacto() ? "Con datos" : "Sin datos"};
>>>>>>> Stashed changes
    }

    private static <T> String unir(List<T> elementos, Function<T, String> texto) {
        return elementos == null ? "" : elementos.stream().map(texto).collect(Collectors.joining(", "));
    }

    private static void valores(Row fila, Object... valores) {
        for (int i = 0; i < valores.length; i++) {
            var celda = fila.createCell(i);
            Object valor = valores[i];
            if (valor instanceof Number numero) celda.setCellValue(numero.doubleValue());
            // Los identificadores conservan ceros iniciales; el texto nunca se convierte en fórmula.
            else if (valor != null) celda.setCellValue(valor.toString());
        }
    }
}
