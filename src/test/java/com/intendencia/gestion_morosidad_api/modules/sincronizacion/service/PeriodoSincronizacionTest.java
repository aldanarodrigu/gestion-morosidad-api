package com.intendencia.gestion_morosidad_api.modules.sincronizacion.service;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class PeriodoSincronizacionTest {
 @Test void guardaSoloAnosValidos(){
  var jdbc=mock(JdbcTemplate.class);var s=new PeriodoSincronizacionService(jdbc,Clock.fixed(Instant.parse("2026-11-11T12:00:00Z"),ZoneOffset.UTC));
  s.guardar(2013);verify(jdbc).update("UPDATE configuracion_sincronizacion SET anio_minimo=? WHERE id=1",2013);
  for(int a:new int[]{0,1899,2027}) assertThatThrownBy(()->s.guardar(a)).isInstanceOf(com.intendencia.gestion_morosidad_api.shared.exception.ReglaNegocioException.class);
  verifyNoMoreInteractions(jdbc);
 }
}
