package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.HuellaDispositivo;
import com.aguavigia.ctg.domain.Pagina;
import com.aguavigia.ctg.domain.RedEnRafaga;
import com.aguavigia.ctg.domain.ReporteCiudadano;
import com.aguavigia.ctg.domain.ReporteId;
import com.aguavigia.ctg.domain.ReportesPendientes;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.TipoReporte;
import com.aguavigia.ctg.domain.port.out.ReporteCiudadanoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * La señal de red de la cola de moderación (D9): muchos reportes desde una sola red en un barrio es lo que distingue una
 * ráfaga de spam de una avería real. No bloquea nada —sin tope duro por IP: el CGNAT móvil lo haría inalcanzable para gente
 * legítima—: solo le dice al veedor dónde mirar primero.
 */
class ListarReportesPendientesServiceTest {

    private static final Instant AHORA = Instant.parse("2026-10-02T08:00:00Z");
    private static final SectorId CRESPO = new SectorId("crespo");

    private ReporteCiudadanoRepository reportes;
    private ListarReportesPendientesService servicio;

    @BeforeEach
    void montar() {
        reportes = mock(ReporteCiudadanoRepository.class);
        servicio = new ListarReportesPendientesService(reportes, () -> AHORA, Duration.ofMinutes(30), 5);
        given(reportes.redesEnRafaga(any(), any(), anyInt())).willReturn(Set.of());
    }

    private static ReporteCiudadano reporte(String id, SectorId sector, String red) {
        return new ReporteCiudadano(new ReporteId(id), sector, TipoReporte.SIN_AGUA, null,
                new HuellaDispositivo("h-" + id), AHORA, com.aguavigia.ctg.domain.EstadoModeracion.PENDIENTE, null,
                Set.of(), false, com.aguavigia.ctg.domain.NivelDeVerificacion.NINGUNA, red);
    }

    @Test
    void marcaLosReportesDeUnaRedQueHizoUnaRafagaEnEseBarrio() {
        given(reportes.listarPendientes(0, 50)).willReturn(new Pagina<>(
                List.of(reporte("a", CRESPO, "red-1"), reporte("b", CRESPO, "red-2"), reporte("c", new SectorId("manga"), "red-1")),
                0, 50, 3));
        given(reportes.redesEnRafaga(any(), any(), anyInt())).willReturn(Set.of(new RedEnRafaga(CRESPO, "red-1")));

        ReportesPendientes pendientes = servicio.listar(0, 50);

        assertThat(pendientes.enRafaga(pendientes.pagina().contenido().get(0))).isTrue();
        assertThat(pendientes.enRafaga(pendientes.pagina().contenido().get(1))).as("otra red").isFalse();
        // La misma red en otro barrio no es una ráfaga allí: la señal es por barrio.
        assertThat(pendientes.enRafaga(pendientes.pagina().contenido().get(2))).isFalse();
    }

    @Test
    void pideSoloLosBarriosDeLaPaginaDentroDeLaVentanaConElMinimoConfigurado() {
        given(reportes.listarPendientes(0, 50)).willReturn(new Pagina<>(
                List.of(reporte("a", CRESPO, "r"), reporte("b", new SectorId("manga"), "r")), 0, 50, 2));

        servicio.listar(0, 50);

        ArgumentCaptor<Collection<SectorId>> sectores = ArgumentCaptor.forClass(Collection.class);
        verify(reportes).redesEnRafaga(sectores.capture(), eq(AHORA.minus(Duration.ofMinutes(30))), eq(5));
        assertThat(sectores.getValue()).containsExactlyInAnyOrder(CRESPO, new SectorId("manga"));
    }

    @Test
    void unaColaVaciaNoConsultaNada() {
        given(reportes.listarPendientes(0, 50)).willReturn(new Pagina<>(List.of(), 0, 50, 0));

        assertThat(servicio.listar(0, 50).pagina().contenido()).isEmpty();
        verify(reportes, never()).redesEnRafaga(any(), any(), anyInt());
    }

    @Test
    void unReporteSinRedConocidaNuncaEsRafaga() {
        given(reportes.listarPendientes(0, 50)).willReturn(new Pagina<>(List.of(reporte("a", CRESPO, null)), 0, 50, 1));
        given(reportes.redesEnRafaga(any(), any(), anyInt())).willReturn(Set.of(new RedEnRafaga(CRESPO, "red-1")));

        ReportesPendientes pendientes = servicio.listar(0, 50);

        assertThat(pendientes.enRafaga(pendientes.pagina().contenido().get(0))).isFalse();
    }
}
