package com.intendencia.gestion_morosidad_api.modules.reporte;
import com.intendencia.gestion_morosidad_api.modules.contribuyente.entity.Contribuyente;
import com.intendencia.gestion_morosidad_api.modules.contribuyente.repository.ContribuyenteRepository;
import com.intendencia.gestion_morosidad_api.modules.padron.entity.Padron;
import com.intendencia.gestion_morosidad_api.modules.padron.repository.PadronRepository;
import com.intendencia.gestion_morosidad_api.modules.deuda.entity.*;
import com.intendencia.gestion_morosidad_api.modules.deuda.repository.DeudaRepository;
import com.intendencia.gestion_morosidad_api.modules.segmento.repository.SegmentoMoraRepository;
import com.intendencia.gestion_morosidad_api.shared.enums.OrigenDatos;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.io.ByteArrayInputStream;
import org.apache.poi.ss.usermodel.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="REPORTE_TEST_DB_URL",matches=".+")
@SpringBootTest(properties="sincronizacion.automatica.habilitada=false")
@Transactional
class ReportePersistenciaTest {
 @DynamicPropertySource static void database(DynamicPropertyRegistry r){
  r.add("spring.datasource.url",()->System.getenv("REPORTE_TEST_DB_URL"));
  r.add("spring.datasource.username",()->System.getenv("REPORTE_TEST_DB_USER"));
  r.add("spring.datasource.password",()->System.getenv("REPORTE_TEST_DB_PASSWORD"));
 }
 @Autowired ReporteService service;
 @Autowired ContribuyenteRepository personas;
 @Autowired PadronRepository padrones;
 @Autowired DeudaRepository deudas;
 @Autowired SegmentoMoraRepository segmentos;
 @Autowired EntityManager em;
 void fila(String cm,OrigenDatos origen,String doc,String tipo,String importe,EstadoDeuda estado,Integer orden){
  var c=new Contribuyente();c.setNombre("Persona de prueba");c.setDocumento(doc);c=personas.save(c);
  var p=new Padron();p.setCm(cm);p.setOrigen(origen);p.setNumeroPadron("00042");p.setTipoPadron(tipo);p.setContribuyente(c);p=padrones.save(p);
  var d=new Deuda();d.setPadron(p);d.setImporte(new BigDecimal(importe));d.setEstado(estado);
  if(orden!=null)d.setSegmentoMora(segmentos.findAllByOrderByOrdenAsc().get(orden-1));deudas.save(d);
 }
 @BeforeEach void datos(){
  fila("1",OrigenDatos.INTENDENCIA,"900"," CIU ","100.10",EstadoDeuda.PENDIENTE,1);
  fila("1",OrigenDatos.SUCIVE,"900","PAT","200.20",EstadoDeuda.PENDIENTE,3);
  fila("2",OrigenDatos.INTENDENCIA,"800","CIR","300",EstadoDeuda.PENDIENTE,2);
  fila("3",OrigenDatos.INTENDENCIA,null,null,"40",EstadoDeuda.PENDIENTE,null);
  fila("4",OrigenDatos.INTENDENCIA,"  ","  ","60",EstadoDeuda.PENDIENTE,null);
  fila("5",OrigenDatos.INTENDENCIA,"700","CIU","999",EstadoDeuda.CANCELADA,1);
  fila("6",OrigenDatos.INTENDENCIA,"600","CIU","0",EstadoDeuda.PENDIENTE,1);
  em.flush();
 }
 @Test void calculaTodaLaCarteraSinDuplicarPersonasYExcluyeCanceladasYCeros(){
  var r=service.consultar(null,null,null);
  assertEquals(0,new BigDecimal("700.30").compareTo(r.totales().importe()));
  assertEquals(4,r.totales().contribuyentes());assertEquals(5,r.totales().padrones());assertEquals(5,r.totales().deudas());
  assertEquals(0,r.porSegmento().stream().map(ReporteCartera.Segmento::importe).reduce(BigDecimal.ZERO,BigDecimal::add).compareTo(r.totales().importe()));
  assertEquals(0,r.resumen().stream().map(ReporteCartera.Grupo::importe).reduce(BigDecimal.ZERO,BigDecimal::add).compareTo(r.totales().importe()));
  assertTrue(r.porTipo().stream().anyMatch(t->t.tipo()==null && t.cantidad()==2 && t.importe().compareTo(new BigDecimal("100"))==0));
  assertTrue(r.porSegmento().stream().anyMatch(s->s.id()==null && s.cantidad()==2));
 }
 @Test void combinaFiltrosYRespetaProveedor(){
  Long id=segmentos.findAllByOrderByOrdenAsc().getFirst().getId();
  var r=service.consultar(OrigenDatos.INTENDENCIA," CIU ",id);
  assertEquals(1,r.totales().padrones());assertEquals(0,r.totales().importe().compareTo(new BigDecimal("100.10")));
  var sucive=service.consultar(OrigenDatos.SUCIVE,null,null);
  assertEquals(1,sucive.totales().padrones());assertEquals(0,sucive.totales().importe().compareTo(new BigDecimal("200.20")));
 }
 @Test void sinResultadosRetornaCerosYListasVacias(){
  var r=service.consultar(OrigenDatos.SUCIVE,"CIU",null);
  assertEquals(0,r.totales().importe().signum());assertEquals(0,r.totales().contribuyentes());
  assertTrue(r.porTipo().isEmpty());assertTrue(r.porSegmento().isEmpty());assertTrue(r.resumen().isEmpty());
 }
 @Test void noSeLimitaALaPaginaDeVeinte(){
  for(int i=0;i<30;i++)fila("EXTRA-"+i,OrigenDatos.INTENDENCIA,"NUEVO-"+i,"CIU","1",EstadoDeuda.PENDIENTE,1);
  em.flush(); var r=service.consultar(null,null,null);
  assertEquals(35,r.totales().padrones());assertEquals(0,new BigDecimal("730.30").compareTo(r.totales().importe()));
 }
 @Test void exportaExcelRealConLosMismosFiltrosYTotales() throws Exception {
  var bytes=service.exportar(OrigenDatos.SUCIVE,"PAT",null);
  try(var wb=WorkbookFactory.create(new ByteArrayInputStream(bytes))){
   assertEquals(5,wb.getNumberOfSheets());
   var resumen=wb.getSheet("Resumen");assertEquals(200.20,resumen.getRow(5).getCell(1).getNumericCellValue(),0.001);
   assertEquals("SUCIVE",wb.getSheet("Por origen y PadTipo").getRow(1).getCell(0).getStringCellValue());
   assertEquals(1,wb.getSheet("Por origen y PadTipo").getLastRowNum());
  }
 }
}
