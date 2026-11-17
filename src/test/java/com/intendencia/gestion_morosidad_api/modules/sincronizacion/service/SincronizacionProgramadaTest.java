package com.intendencia.gestion_morosidad_api.modules.sincronizacion.service;

import com.intendencia.gestion_morosidad_api.shared.enums.OrigenDatos;
import com.intendencia.gestion_morosidad_api.shared.exception.IntegracionExternaException;
import com.intendencia.gestion_morosidad_api.shared.exception.OperacionEnCursoException;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.support.CronExpression;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

class SincronizacionProgramadaTest {

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withUserConfiguration(SincronizacionProgramada.class)
            .withBean(SincronizacionDeudasService.class, () -> mock(SincronizacionDeudasService.class));

    @Test
    void sincronizaTodosLosOrigenesEnOrden() {
        var servicio = mock(SincronizacionDeudasService.class);

        new SincronizacionProgramada(servicio).ejecutar();

        InOrder orden = inOrder(servicio);
        orden.verify(servicio).sincronizar(OrigenDatos.INTENDENCIA);
        orden.verify(servicio).sincronizar(OrigenDatos.SUCIVE);
        verifyNoMoreInteractions(servicio);
    }

    @Test
    void siFallaUnOrigenIgualSincronizaElOtroYNoPropagaElError() {
        var servicio = mock(SincronizacionDeudasService.class);
        when(servicio.sincronizar(OrigenDatos.INTENDENCIA))
                .thenThrow(new IntegracionExternaException("GeoPagos respondió HTTP 504"));

        assertThatCode(() -> new SincronizacionProgramada(servicio).ejecutar()).doesNotThrowAnyException();

        verify(servicio).sincronizar(OrigenDatos.SUCIVE);
    }

    @Test
    void conUnaSincronizacionManualEnCursoNoArrancaOtraYTerminaSinError() {
        var servicio = mock(SincronizacionDeudasService.class);
        when(servicio.sincronizar(any(OrigenDatos.class)))
                .thenThrow(new OperacionEnCursoException("Ya hay una sincronización en curso"));

        assertThatCode(() -> new SincronizacionProgramada(servicio).ejecutar()).doesNotThrowAnyException();

        // Solo intenta una vez por origen: no reintenta ni espera a que termine la manual.
        verify(servicio, times(1)).sincronizar(OrigenDatos.INTENDENCIA);
        verify(servicio, times(1)).sincronizar(OrigenDatos.SUCIVE);
    }

    @Test
    void seRegistraSoloSiEstaHabilitada() {
        contexto.withPropertyValues("sincronizacion.automatica.habilitada=true")
                .run(ctx -> assertThat(ctx).hasSingleBean(SincronizacionProgramada.class));
        contexto.withPropertyValues("sincronizacion.automatica.habilitada=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(SincronizacionProgramada.class));
        contexto.run(ctx -> assertThat(ctx).doesNotHaveBean(SincronizacionProgramada.class));
    }

    @Test
    void programaLaTareaConElCronConfigurado() {
        contexto.withPropertyValues(
                        "sincronizacion.automatica.habilitada=true",
                        "sincronizacion.automatica.cron=0 30 4 * * *")
                .run(ctx -> assertThat(cronesProgramados(ctx.getBean(ScheduledAnnotationBeanPostProcessor.class)))
                        .containsExactly("0 30 4 * * *"));
    }

    @Test
    void sinCronConfiguradoCorreALasTresDeLaMananaDeUruguay() throws Exception {
        contexto.withPropertyValues("sincronizacion.automatica.habilitada=true")
                .run(ctx -> assertThat(cronesProgramados(ctx.getBean(ScheduledAnnotationBeanPostProcessor.class)))
                        .containsExactly("0 0 3 * * *"));

        Scheduled anotacion = SincronizacionProgramada.class.getMethod("ejecutar").getAnnotation(Scheduled.class);
        assertThat(anotacion.zone()).isEqualTo("America/Montevideo");

        var zona = ZoneId.of(anotacion.zone());
        var despuesDeLasTres = ZonedDateTime.of(LocalDateTime.of(2026, 11, 17, 3, 0, 1), zona);
        assertThat(CronExpression.parse("0 0 3 * * *").next(despuesDeLasTres))
                .isEqualTo(ZonedDateTime.of(LocalDateTime.of(2026, 11, 18, 3, 0), zona));
    }

    private static java.util.List<String> cronesProgramados(ScheduledAnnotationBeanPostProcessor procesador) {
        return procesador.getScheduledTasks().stream()
                .map(ScheduledTask::getTask)
                .filter(CronTask.class::isInstance)
                .map(t -> ((CronTask) t).getExpression())
                .toList();
    }
}
