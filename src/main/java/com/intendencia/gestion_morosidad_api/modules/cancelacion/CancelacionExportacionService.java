package com.intendencia.gestion_morosidad_api.modules.cancelacion;

import com.intendencia.gestion_morosidad_api.shared.enums.OrigenDatos;
import com.intendencia.gestion_morosidad_api.shared.exportacion.Csv;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.SpreadsheetVersion;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CancelacionExportacionService {
    private static final String[] COLUMNAS = {
        "CM", "Padrón", "Origen", "Contribuyente", "Documento",
        "N.º de cobro", "Fecha del cobro", "Motivo", "Importe total del cobro"
    };

    private final CancelacionRepository cancelaciones;

    @Transactional(readOnly = true)
    public byte[] csv(FiltroCancelaciones filtro) throws IOException {
        try (var salida = new ByteArrayOutputStream();
             var escritor = new BufferedWriter(new OutputStreamWriter(salida, StandardCharsets.UTF_8));
             var registros = cancelaciones.recorrer(filtro)) {
            escritor.write('\uFEFF');
            escritor.write(Csv.linea((Object[]) COLUMNAS));
            var filas = registros.iterator();
            while (filas.hasNext()) escritor.write(Csv.linea(datos(filas.next())));
            escritor.flush();
            return salida.toByteArray();
        }
    }

    @Transactional(readOnly = true)
    public byte[] xlsx(FiltroCancelaciones filtro) throws IOException {
        try (var libro = new SXSSFWorkbook(100); var salida = new ByteArrayOutputStream();
             var registros = cancelaciones.recorrer(filtro)) {
            libro.setCompressTempFiles(true);
            var cabecera = libro.createCellStyle();
            cabecera.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            cabecera.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            var fuente = libro.createFont();
            fuente.setBold(true);
            fuente.setColor(IndexedColors.WHITE.getIndex());
            cabecera.setFont(fuente);

            var formato = libro.createDataFormat();
            var fecha = libro.createCellStyle();
            fecha.setDataFormat(formato.getFormat("dd/mm/yyyy"));
            var dinero = libro.createCellStyle();
            dinero.setDataFormat(formato.getFormat("#,##0.00"));

            var hoja = hoja(libro, cabecera);
            int fila = 1;
            var filas = registros.iterator();
            while (filas.hasNext()) {
                if (fila == SpreadsheetVersion.EXCEL2007.getMaxRows()) {
                    filtrar(hoja, fila);
                    hoja = hoja(libro, cabecera);
                    fila = 1;
                }
                var r = hoja.createRow(fila++);
                var valores = datos(filas.next());
                for (int i = 0; i < valores.length; i++) {
                    var celda = r.createCell(i);
                    switch (valores[i]) {
                        case LocalDate valor -> {
                            celda.setCellValue(valor);
                            celda.setCellStyle(fecha);
                        }
                        case BigDecimal valor -> {
                            celda.setCellValue(valor.doubleValue());
                            celda.setCellStyle(dinero);
                        }
                        case String valor -> celda.setCellValue(valor);
                        case null, default -> { }
                    }
                }
            }
            filtrar(hoja, fila);
            libro.write(salida);
            return salida.toByteArray();
        }
    }

    private static Object[] datos(Cancelacion c) {
        return new Object[]{c.cm(), c.numeroPadron(), c.origen() == OrigenDatos.SUCIVE ? "SUCIVE" : "Intendencia",
                c.persona(), c.documento(), c.numeroCobro(), c.fechaCobro(), c.motivo(), c.importeTotal()};
    }

    private static Sheet hoja(SXSSFWorkbook libro, CellStyle cabecera) {
        int numero = libro.getNumberOfSheets() + 1;
        var hoja = libro.createSheet(numero == 1 ? "Cancelaciones" : "Cancelaciones " + numero);
        var fila = hoja.createRow(0);
        for (int i = 0; i < COLUMNAS.length; i++) {
            var celda = fila.createCell(i);
            celda.setCellValue(COLUMNAS[i]);
            celda.setCellStyle(cabecera);
            hoja.setColumnWidth(i, (i == 3 ? 40 : Math.max(18, COLUMNAS[i].length() + 3)) * 256);
        }
        hoja.createFreezePane(0, 1);
        return hoja;
    }

    private static void filtrar(Sheet hoja, int siguienteFila) {
        hoja.setAutoFilter(new CellRangeAddress(0, siguienteFila - 1, 0, COLUMNAS.length - 1));
    }
}
