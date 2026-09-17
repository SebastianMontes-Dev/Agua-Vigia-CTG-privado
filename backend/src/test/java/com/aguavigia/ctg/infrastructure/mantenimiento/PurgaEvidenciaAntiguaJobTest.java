package com.aguavigia.ctg.infrastructure.mantenimiento;

import com.aguavigia.ctg.domain.EvidenciaVencida;
import com.aguavigia.ctg.domain.ReporteId;
import com.aguavigia.ctg.domain.port.out.AlmacenamientoPort;
import com.aguavigia.ctg.domain.port.out.RelojPort;
import com.aguavigia.ctg.domain.port.out.ReporteCiudadanoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class PurgaEvidenciaAntiguaJobTest {

    private static final Instant AHORA = Instant.parse("2026-08-10T12:00:00Z");

    private AlmacenamientoPort almacenamiento;
    private ReporteCiudadanoRepository reportes;
    private PurgaEvidenciaAntiguaJob job;

    @BeforeEach
    void montar() {
        almacenamiento = mock(AlmacenamientoPort.class);
        reportes = mock(ReporteCiudadanoRepository.class);
    }

    private void conPropiedades(MantenimientoProperties.RetencionEvidencia retencion) {
        RelojPort reloj = () -> AHORA;
        job = new PurgaEvidenciaAntiguaJob(almacenamiento, reportes, reloj,
                new MantenimientoProperties(null, retencion));
    }

    @Test
    void debeBorrarElArchivoYLimpiarLaFotoUrlDeCadaReporteVencidoEnUnSoloLote() {
        conPropiedades(new MantenimientoProperties.RetencionEvidencia(true, 365));
        given(reportes.listarEvidenciaAnteriorA(AHORA.minus(Duration.ofDays(365))))
                .willReturn(List.of(new EvidenciaVencida(new ReporteId("r1"), "/fotos/vieja.jpg")));

        job.purgar();

        verify(almacenamiento).eliminar("vieja.jpg");
        var captor = forClass(List.class);
        verify(reportes).quitarFotosDe(captor.capture());
        @SuppressWarnings("unchecked")
        List<ReporteId> idsLimpiados = (List<ReporteId>) captor.getValue();
        assertThat(idsLimpiados).containsExactly(new ReporteId("r1"));
    }

    @Test
    void noDebeHacerNadaSiElJobEstaDeshabilitado() {
        conPropiedades(new MantenimientoProperties.RetencionEvidencia(false, 365));

        job.purgar();

        verify(reportes, never()).listarEvidenciaAnteriorA(any());
        verify(almacenamiento, never()).eliminar(any());
    }
}
