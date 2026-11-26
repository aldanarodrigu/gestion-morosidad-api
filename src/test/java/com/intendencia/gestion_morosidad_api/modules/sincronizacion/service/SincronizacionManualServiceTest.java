package com.intendencia.gestion_morosidad_api.modules.sincronizacion.service;
import org.junit.jupiter.api.Test;
import com.intendencia.gestion_morosidad_api.integration.intendencia.common.SeguimientoSincronizacion;
import java.time.Clock;
import java.util.concurrent.*;
import com.intendencia.gestion_morosidad_api.shared.enums.OrigenDatos;
import com.intendencia.gestion_morosidad_api.shared.exception.OperacionEnCursoException;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class SincronizacionManualServiceTest {
 @Test void unaCancelacionObsoletaConservaLaNuevaEjecucion() throws Exception {
  var sync = mock(SincronizacionDeudasService.class);
  var periodo = mock(PeriodoSincronizacionService.class);
  when(periodo.anioMinimo()).thenReturn(2013);
  var empezoSucive = new CountDownLatch(1);
  var liberarSucive = new CountDownLatch(1);
  when(sync.sincronizar(OrigenDatos.SUCIVE, 2013)).thenAnswer(invocacion -> {
   SeguimientoSincronizacion.etapa("DEUDAS", 0);
   empezoSucive.countDown();
   liberarSucive.await(5, TimeUnit.SECONDS);
   SeguimientoSincronizacion.comprobarCancelacion();
   return null;
  });

  var manual = new SincronizacionManualService(sync, periodo, Clock.systemUTC());
  try {
   var anterior = manual.iniciar(OrigenDatos.INTENDENCIA);
   for (int i = 0; i < 200 && manual.estado().estado().equals("EN_CURSO"); i++) Thread.sleep(10);
   assertThat(manual.estado().estado()).isEqualTo("COMPLETADA");
   assertThat(manual.estado().id()).isEqualTo(anterior.id());

   var nueva = manual.iniciar(OrigenDatos.SUCIVE);
   assertThat(empezoSucive.await(2, TimeUnit.SECONDS)).isTrue();
   assertThat(nueva.id()).isNotEqualTo(anterior.id());
   assertThatThrownBy(() -> manual.cancelar(anterior.id())).isInstanceOf(OperacionEnCursoException.class);
   assertThat(manual.estado().id()).isEqualTo(nueva.id());
   assertThat(manual.estado().estado()).isEqualTo("EN_CURSO");

   assertThat(manual.cancelar(nueva.id()).estado()).isEqualTo("CANCELANDO");
   liberarSucive.countDown();
   for (int i = 0; i < 200 && manual.estado().estado().equals("CANCELANDO"); i++) Thread.sleep(10);
   assertThat(manual.estado().estado()).isEqualTo("CANCELADA");
   assertThat(manual.estado().id()).isEqualTo(nueva.id());
  } finally {
   liberarSucive.countDown();
   manual.cerrar();
  }
 }

 @Test void usaPeriodoGuardadoImpideSolapamientoYComunicaElResultado() throws Exception {
  var sync=mock(SincronizacionDeudasService.class);var periodo=mock(PeriodoSincronizacionService.class);
  when(periodo.anioMinimo()).thenReturn(2013);
  var inicio=new CountDownLatch(1);var liberar=new CountDownLatch(1);
  when(sync.sincronizar(OrigenDatos.SUCIVE,2013)).thenAnswer(i->{SeguimientoSincronizacion.etapa("DEUDAS",0);SeguimientoSincronizacion.pagina(1,1000,null,null,true);SeguimientoSincronizacion.reintentando(2,2);inicio.countDown();liberar.await(5,TimeUnit.SECONDS);throw new com.intendencia.gestion_morosidad_api.shared.exception.IntegracionExternaException("API respondió HTTP 504");});
  var manual=new SincronizacionManualService(sync,periodo,Clock.systemUTC());
  try {
   assertThat(manual.iniciar(OrigenDatos.SUCIVE).anioMinimo()).isEqualTo(2013);assertThat(inicio.await(2,TimeUnit.SECONDS)).isTrue();
   assertThat(manual.estado().progreso().registrosDescargados()).isEqualTo(1000);
   assertThat(manual.estado().progreso().intento()).isEqualTo(2);
   assertThatThrownBy(()->manual.iniciar(OrigenDatos.INTENDENCIA)).isInstanceOf(com.intendencia.gestion_morosidad_api.shared.exception.OperacionEnCursoException.class);
   assertThatThrownBy(()->manual.guardar(2015)).isInstanceOf(com.intendencia.gestion_morosidad_api.shared.exception.OperacionEnCursoException.class);
   liberar.countDown();for(int i=0;i<200 && manual.estado().estado().equals("EN_CURSO");i++)Thread.sleep(10);
   assertThat(manual.estado().estado()).isEqualTo("ERROR");assertThat(manual.estado().mensaje()).contains("504");
   assertThat(manual.estado().progreso().registrosDescargados()).isEqualTo(1000);
   manual.guardar(2015);verify(sync).guardarAnio(2015);
  } finally{liberar.countDown();manual.cerrar();}
 }
 @Test void cancelarEsCooperativoYNoLiberaLaTareaMientrasLaPeticionSigueActiva() throws Exception {
  var sync=mock(SincronizacionDeudasService.class);var periodo=mock(PeriodoSincronizacionService.class);when(periodo.anioMinimo()).thenReturn(2013);
  var entro=new CountDownLatch(1);var liberar=new CountDownLatch(1);
  when(sync.sincronizar(OrigenDatos.SUCIVE,2013)).thenAnswer(i->{SeguimientoSincronizacion.etapa("DEUDAS",0);entro.countDown();liberar.await(3,TimeUnit.SECONDS);SeguimientoSincronizacion.comprobarCancelacion();return null;});
  var manual=new SincronizacionManualService(sync,periodo,Clock.systemUTC());
  try {
   var id = manual.iniciar(OrigenDatos.SUCIVE).id();
   assertThat(entro.await(2,TimeUnit.SECONDS)).isTrue();
   assertThat(manual.cancelar(id).estado()).isEqualTo("CANCELANDO");
   assertThat(manual.estado().id()).isEqualTo(id);
   assertThatThrownBy(()->manual.iniciar(OrigenDatos.INTENDENCIA)).isInstanceOf(com.intendencia.gestion_morosidad_api.shared.exception.OperacionEnCursoException.class);
   liberar.countDown();for(int i=0;i<200&&!manual.estado().estado().equals("CANCELADA");i++)Thread.sleep(10);
   assertThat(manual.estado().estado()).isEqualTo("CANCELADA");assertThat(manual.estado().resultado()).isNull();
   assertThat(manual.cancelar(id).estado()).isEqualTo("CANCELADA");manual.guardar(2015);verify(sync).guardarAnio(2015);
  } finally{liberar.countDown();manual.cerrar();}
 }
 @Test void noAceptaCancelacionCuandoElGuardadoYaComenzo() throws Exception {
  var sync=mock(SincronizacionDeudasService.class);var periodo=mock(PeriodoSincronizacionService.class);when(periodo.anioMinimo()).thenReturn(2013);
  var entro=new CountDownLatch(1);var liberar=new CountDownLatch(1);
  when(sync.sincronizar(OrigenDatos.INTENDENCIA,2013)).thenAnswer(i->{SeguimientoSincronizacion.etapa("GUARDANDO",10);entro.countDown();liberar.await(3,TimeUnit.SECONDS);return null;});
  var manual=new SincronizacionManualService(sync,periodo,Clock.systemUTC());
  try {
   var id = manual.iniciar(OrigenDatos.INTENDENCIA).id();
   assertThat(entro.await(2,TimeUnit.SECONDS)).isTrue();
   assertThatThrownBy(() -> manual.cancelar(id)).isInstanceOf(com.intendencia.gestion_morosidad_api.shared.exception.ReglaNegocioException.class);
   liberar.countDown();for(int i=0;i<200&&manual.estado().estado().equals("EN_CURSO");i++)Thread.sleep(10);
   assertThat(manual.estado().estado()).isEqualTo("COMPLETADA");
  } finally{liberar.countDown();manual.cerrar();}
 }

}
