package com.intendencia.gestion_morosidad_api.modules.reporte;
import com.intendencia.gestion_morosidad_api.shared.enums.OrigenDatos;
import java.time.*;
import java.io.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

@Service
@RequiredArgsConstructor
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class ReporteService {
    private final ReporteRepository repository;
    private final Clock clock;
    public ReporteCartera consultar(OrigenDatos origen,String tipoPadron,Long segmento) {
        return repository.consultar(origen,tipoPadron,segmento,LocalDateTime.now(clock));
    }
    public byte[] exportar(OrigenDatos origen,String tipoPadron,Long segmento) throws IOException {
        var r=consultar(origen,tipoPadron,segmento);
        try(var wb=new XSSFWorkbook();var out=new ByteArrayOutputStream()) {
            var cab=wb.createCellStyle(); cab.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());cab.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            var font=wb.createFont(); font.setBold(true);font.setColor(IndexedColors.WHITE.getIndex());cab.setFont(font);
            var monto=wb.createCellStyle(); monto.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));
            var porcentaje=wb.createCellStyle(); porcentaje.setDataFormat(wb.createDataFormat().getFormat("0.0%"));
            var resumen=hoja(wb,"Resumen",cab,"Indicador","Valor");
            var fecha=wb.createCellStyle(); fecha.setDataFormat(wb.createDataFormat().getFormat("dd/mm/yyyy hh:mm"));
            var celdaFecha=fila(resumen,monto,"Generado",r.generado().toString()).getCell(1);
            celdaFecha.setCellValue(r.generado()); celdaFecha.setCellStyle(fecha);
            fila(resumen,monto,"Origen",origen == null ? "Todos" : origen.name());
            fila(resumen,monto,"PadTipo",tipoPadron == null || tipoPadron.isBlank() ? "Todos" : tipoPadron.trim());
            String nombreSegmento = segmento == null ? "Todos" : r.porSegmento().stream()
                    .filter(s -> segmento.equals(s.id())).map(ReporteCartera.Segmento::nombre)
                    .filter(Objects::nonNull).findFirst().orElse("ID " + segmento);
            fila(resumen,monto,"Segmento de mora",nombreSegmento);
            fila(resumen,monto,"Deuda pendiente",r.totales().importe());
            fila(resumen,monto,"Contribuyentes distintos",r.totales().contribuyentes());
            fila(resumen,monto,"Padrones",r.totales().padrones());fila(resumen,monto,"Deudas",r.totales().deudas());
            var mora=hoja(wb,"Por mora",cab,"Segmento","Padrones","Importe","% del total");
            for(var s:r.porSegmento()) fila(mora,monto,s.nombre() == null ? "Sin segmento" : s.nombre(),s.cantidad(),s.importe(),proporcion(s.importe(),r.totales().importe())).getCell(3).setCellStyle(porcentaje);
            var tipos=hoja(wb,"Por PadTipo",cab,"PadTipo","Padrones","Importe","% del total");
            for(var t:r.porTipo()) fila(tipos,monto,t.tipo() == null ? "Sin PadTipo" : t.tipo(),t.cantidad(),t.importe(),proporcion(t.importe(),r.totales().importe())).getCell(3).setCellStyle(porcentaje);
            var origenes=hoja(wb,"Por origen",cab,"Origen","Padrones","Importe","% del total");
            var porOrigen = new TreeMap<String, List<ReporteCartera.Grupo>>();
            for (var g : r.resumen()) porOrigen.computeIfAbsent(g.origen(), k -> new ArrayList<>()).add(g);
            for (var entrada : porOrigen.entrySet()) {
                long cantidad = entrada.getValue().stream().mapToLong(ReporteCartera.Grupo::cantidad).sum();
                var importe = entrada.getValue().stream().map(ReporteCartera.Grupo::importe).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
                fila(origenes,monto,entrada.getKey().equals("SUCIVE") ? "SUCIVE" : "Intendencia",cantidad,importe,proporcion(importe,r.totales().importe())).getCell(3).setCellStyle(porcentaje);
            }
            var grupos=hoja(wb,"Por origen y PadTipo",cab,"Origen","PadTipo","Contribuyentes","Padrones","Importe","% del total");
            for(var g:r.resumen()) fila(grupos,monto,g.origen().equals("SUCIVE") ? "SUCIVE" : "Intendencia",g.tipo() == null ? "Sin PadTipo" : g.tipo(),g.personas(),g.cantidad(),g.importe(),proporcion(g.importe(),r.totales().importe())).getCell(5).setCellStyle(porcentaje);
            var bandas = new HashMap<Short,CellStyle>();
            for(var s:wb) {
                s.getRow(0).setHeightInPoints(26);
                int columnas=s.getRow(0).getLastCellNum();
                for(int i=1;i<=s.getLastRowNum();i++) {
                    var fila=s.getRow(i); fila.setHeightInPoints(22);
                    if(i%2==0) for(var celda:fila) {
                        var estilo=bandas.computeIfAbsent(celda.getCellStyle().getIndex(), k -> {
                            var nuevo=wb.createCellStyle(); nuevo.cloneStyleFrom(celda.getCellStyle());
                            nuevo.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
                            nuevo.setFillPattern(FillPatternType.SOLID_FOREGROUND); return nuevo;
                        });
                        celda.setCellStyle(estilo);
                    }
                }
                for(int i=0;i<columnas;i++) {
                    s.autoSizeColumn(i);
                    s.setColumnWidth(i,Math.min(56*256,Math.max(20*256,s.getColumnWidth(i)+3*256)));
                }
                s.setAutoFilter(new CellRangeAddress(0,s.getLastRowNum(),0,columnas-1));
                s.setRepeatingRows(new CellRangeAddress(0,0,-1,-1));
                s.getPrintSetup().setLandscape(true); s.getPrintSetup().setFitWidth((short)1);
                s.getPrintSetup().setFitHeight((short)0);s.setFitToPage(true);
            }
            wb.write(out); return out.toByteArray();
        }
    }
    private static Sheet hoja(Workbook wb,String nombre,CellStyle estilo,String... encabezados) {
        var s=wb.createSheet(nombre);var row=s.createRow(0);
        for(int i=0;i<encabezados.length;i++){row.createCell(i).setCellValue(encabezados[i]);row.getCell(i).setCellStyle(estilo);s.setColumnWidth(i,32*256);}
        s.createFreezePane(0,1);return s;
    }
    private static Row fila(Sheet s,CellStyle monto,Object... valores){
        var row=s.createRow(s.getLastRowNum()+1);
        for(int i=0;i<valores.length;i++){var c=row.createCell(i);var v=valores[i];if(v instanceof Number n){c.setCellValue(n.doubleValue());if(v instanceof java.math.BigDecimal)c.setCellStyle(monto);}else if(v!=null)c.setCellValue(v.toString());}
        return row;
    }
    private static double proporcion(java.math.BigDecimal importe,java.math.BigDecimal total) {
        return total.signum()==0 ? 0 : importe.divide(total,java.math.MathContext.DECIMAL64).doubleValue();
    }
}
