package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.EstadoModeracion;
import com.aguavigia.ctg.domain.ReporteCiudadano;
import com.aguavigia.ctg.domain.ReporteId;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.TipoReporte;
import com.aguavigia.ctg.domain.HuellaDispositivo;
import com.aguavigia.ctg.domain.port.in.RecalcularSectorUseCase;
import com.aguavigia.ctg.domain.port.out.ReporteCiudadanoRepository;
import com.aguavigia.ctg.domain.port.out.TransaccionPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

class ModerarReporteServiceTest {

    private static final Instant AHORA = Instant.parse("2026-08-09T15:30:00Z");

    private ReporteCiudadanoRepository reportes;
    private RecalcularSectorUseCase recalcular;
    private TransaccionPort transaccion;
    private ModerarReporteService servicio;

    @BeforeEach
    void montar() {
        reportes = mock(ReporteCiudadanoRepository.class);
        recalcular = mock(RecalcularSectorUseCase.class);
        transaccion = spy(new TransaccionPort() {
            @Override
            public <T> T ejecutar(java.util.function.Supplier<T> accion) {
                return accion.get();
            }
        });
        servicio = new ModerarReporteService(reportes, recalcular, transaccion);

        given(reportes.guardar(any(ReporteCiudadano.class))).willAnswer(invocacion -> invocacion.getArgument(0));
    }

    private ReporteCiudadano reportePendiente() {
        return new ReporteCiudadano(new ReporteId("r1"), new SectorId("manga"), TipoReporte.SIN_AGUA,
                null, new HuellaDispositivo("hash-1"), AHORA);
    }

    @Test
    void debeAprobarUnReportePendiente() {
        given(reportes.buscarPorId(new ReporteId("r1"))).willReturn(Optional.of(reportePendiente()));

        ReporteCiudadano aprobado = servicio.aprobar(new ReporteId("r1"));

        assertThat(aprobado.estadoModeracion()).isEqualTo(EstadoModeracion.APROBADO);
    }

    @Test
    void debeDescartarUnReportePendiente() {
        given(reportes.buscarPorId(new ReporteId("r1"))).willReturn(Optional.of(reportePendiente()));

        ReporteCiudadano descartado = servicio.descartar(new ReporteId("r1"));

        assertThat(descartado.estadoModeracion()).isEqualTo(EstadoModeracion.DESCARTADO);
    }

    /** Descartar los reportes de un abusador no puede dejar publicado un estado que solo ellos sostenían (D23). */
    @Test
    void alDescartarUnReporteReevaluaElBarrioDelReporte() {
        given(reportes.buscarPorId(new ReporteId("r1"))).willReturn(Optional.of(reportePendiente()));

        servicio.descartar(new ReporteId("r1"));

        verify(recalcular).reevaluarTrasDescarte(new SectorId("manga"));
    }

    @Test
    void elDescarteSeGuardaAntesDeReevaluar() {
        given(reportes.buscarPorId(new ReporteId("r1"))).willReturn(Optional.of(reportePendiente()));
        InOrder orden = inOrder(reportes, recalcular);

        servicio.descartar(new ReporteId("r1"));

        orden.verify(reportes).guardar(any(ReporteCiudadano.class));
        orden.verify(recalcular).reevaluarTrasDescarte(any());
    }

    /**
     * Si el descarte se confirmara y la reevaluación fallara, el reporte del abusador quedaría descartado pero el
     * estado que él sostenía seguiría publicado, y nada volvería a comprobarlo hasta que caduque. Van en una unidad.
     */
    @Test
    void elDescarteYLaReevaluacionVanEnUnaSolaTransaccion() {
        given(reportes.buscarPorId(new ReporteId("r1"))).willReturn(Optional.of(reportePendiente()));

        servicio.descartar(new ReporteId("r1"));

        verify(transaccion).ejecutar(any());
    }

    @Test
    void siLaReevaluacionFallaLaFallaSePropagaParaQueLaTransaccionRevierta() {
        given(reportes.buscarPorId(new ReporteId("r1"))).willReturn(Optional.of(reportePendiente()));
        given(recalcular.reevaluarTrasDescarte(any())).willThrow(new IllegalStateException("Mongo caído"));

        assertThatThrownBy(() -> servicio.descartar(new ReporteId("r1"))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aprobarUnReporteNoReevaluaElBarrio() {
        given(reportes.buscarPorId(new ReporteId("r1"))).willReturn(Optional.of(reportePendiente()));

        servicio.aprobar(new ReporteId("r1"));

        verify(recalcular, never()).reevaluarTrasDescarte(any());
    }

    @Test
    void debePermitirCambiarDeDecisionSinFallar() {
        given(reportes.buscarPorId(new ReporteId("r1"))).willReturn(Optional.of(reportePendiente().aprobar()));

        ReporteCiudadano descartado = servicio.descartar(new ReporteId("r1"));

        assertThat(descartado.estadoModeracion()).isEqualTo(EstadoModeracion.DESCARTADO);
    }

    @Test
    void debeRechazarModerarUnReporteQueNoExiste() {
        given(reportes.buscarPorId(new ReporteId("no-existe"))).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.aprobar(new ReporteId("no-existe")))
                .isInstanceOf(IllegalArgumentException.class);

        verify(reportes, never()).guardar(any());
    }
}
