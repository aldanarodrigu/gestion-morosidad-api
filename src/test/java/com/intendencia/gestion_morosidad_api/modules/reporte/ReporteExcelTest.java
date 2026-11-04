package com.intendencia.gestion_morosidad_api.modules.reporte;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.*;
import java.math.BigDecimal;
import java.util.List;
import java.io.ByteArrayInputStream;
import org.apache.poi.ss.usermodel.*;
import org.junit.jupiter.api.Test;
class ReporteExcelTest {
 @Test void conservaNumerosPorcentajesYTextoSinFormulas() throws Exception {
  var repo=mock(ReporteRepository.class);var service=new ReporteService(repo,Clock.systemUTC());
  when(repo.consultar(any(),any(),any(),any())).thenReturn(new ReporteCartera(LocalDateTime.of(2026,11,3,12,0),
   new ReporteCartera.Totales(new BigDecimal("100"),2,2,2),
   List.of(new ReporteCartera.Segmento(1L,"Mora Temprana",1,1,new BigDecimal("25"))),
   List.of(new ReporteCartera.Tipo("=SUM(A1:A2)",1,new BigDecimal("25"))),
   List.of(new ReporteCartera.Grupo("SUCIVE","PAT",2,2,new BigDecimal("100")))));
  try(var wb=WorkbookFactory.create(new ByteArrayInputStream(service.exportar(null,null,null)))){
   assertEquals(5,wb.getNumberOfSheets());
   assertEquals(0.25,wb.getSheet("Por mora").getRow(1).getCell(3).getNumericCellValue(),0.0001);
   assertEquals(1,wb.getSheet("Por origen y PadTipo").getRow(1).getCell(5).getNumericCellValue(),0.0001);
   assertEquals(CellType.STRING,wb.getSheet("Por PadTipo").getRow(1).getCell(0).getCellType());
   assertEquals(CellType.NUMERIC,wb.getSheet("Por PadTipo").getRow(1).getCell(2).getCellType());
  }
 }
 @Test void exportaReporteVacioSinFilasInventadas() throws Exception {
  var repo=mock(ReporteRepository.class);var service=new ReporteService(repo,Clock.systemUTC());
  when(repo.consultar(any(),any(),any(),any())).thenReturn(new ReporteCartera(LocalDateTime.now(),new ReporteCartera.Totales(BigDecimal.ZERO,0,0,0),List.of(),List.of(),List.of()));
  try(var wb=WorkbookFactory.create(new ByteArrayInputStream(service.exportar(null,null,null)))){
   assertEquals(0,wb.getSheet("Por mora").getLastRowNum());assertEquals(0,wb.getSheet("Por PadTipo").getLastRowNum());
  }
 }
 @Test void origenAgrupaTiposSinSumarContribuyentesRepetidos() throws Exception {
  var repo=mock(ReporteRepository.class);var service=new ReporteService(repo,Clock.systemUTC());
  when(repo.consultar(any(),any(),any(),any())).thenReturn(new ReporteCartera(LocalDateTime.now(),
   new ReporteCartera.Totales(new BigDecimal("100"),1,5,5),List.of(),List.of(),
   List.of(new ReporteCartera.Grupo("INTENDENCIA","CIU",1,2,new BigDecimal("25")),
    new ReporteCartera.Grupo("INTENDENCIA","CIR",1,1,new BigDecimal("35")),
    new ReporteCartera.Grupo("SUCIVE","PAT",1,2,new BigDecimal("40")))));
  try(var wb=WorkbookFactory.create(new ByteArrayInputStream(service.exportar(null,null,null)))) {
   var hoja=wb.getSheet("Por origen");
   assertEquals(2,hoja.getLastRowNum());
   assertEquals("Intendencia",hoja.getRow(1).getCell(0).getStringCellValue());
   assertEquals(3,hoja.getRow(1).getCell(1).getNumericCellValue());
   assertEquals(60,hoja.getRow(1).getCell(2).getNumericCellValue());
   assertEquals(0.6,hoja.getRow(1).getCell(3).getNumericCellValue(),0.0001);
   assertEquals("SUCIVE",hoja.getRow(2).getCell(0).getStringCellValue());
   assertEquals(40,hoja.getRow(2).getCell(2).getNumericCellValue());
  }
 }
}
