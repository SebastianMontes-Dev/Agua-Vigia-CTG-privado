package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.EntidadNoEncontradaException;
import com.aguavigia.ctg.domain.HuellaDispositivo;
import com.aguavigia.ctg.domain.ReporteCiudadano;
import com.aguavigia.ctg.domain.ReporteId;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.TipoReporte;
import com.aguavigia.ctg.domain.port.out.ReporteCiudadanoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class DescartarFotoServiceTest {

    private static final Instant AHORA = Instant.parse("2026-10-01T15:00:00Z");
    private static final ReporteId ID = new ReporteId("r1");

    private ReporteCiudadanoRepository reportes;
    private DescartarFotoService servicio;

    @BeforeEach
    void montar() {
        reportes = mock(ReporteCiudadanoRepository.class);
        servicio = new DescartarFotoService(reportes);
        given(reportes.marcarFotoDescartada(ID)).willReturn(true);
    }

    private static ReporteCiudadano reporte() {
        return new ReporteCiudadano(ID, new SectorId("manga"), TipoReporte.SIN_AGUA, null,
                new HuellaDispositivo("h"), AHORA);
    }

    @Test
    void debeDescartarLaFotoSinDescartarElReporte() {
        ReporteCiudadano conFoto = reporte().aprobar().conFoto("/api/fotos/x.jpg", "a".repeat(64));
        given(reportes.buscarPorId(ID)).willReturn(Optional.of(conFoto), Optional.of(conFoto.descartarFoto()));

        ReporteCiudadano resultado = servicio.descartarFoto(ID);

        assertThat(resultado.fotoDescartada()).isTrue();
        assertThat(resultado.fotoEsPublica()).isFalse();
        assertThat(resultado.estadoModeracion()).isEqualTo(com.aguavigia.ctg.domain.EstadoModeracion.APROBADO);
        // Escritura atómica del campo, no guardar el documento entero: no pisa una aprobación simultánea.
        verify(reportes).marcarFotoDescartada(ID);
        verify(reportes, never()).guardar(any());
    }

    @Test
    void unReporteInexistenteDebeSer404() {
        given(reportes.buscarPorId(ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.descartarFoto(ID)).isInstanceOf(EntidadNoEncontradaException.class);
    }

    @Test
    void unReporteSinFotoDebeSerConflicto() {
        given(reportes.buscarPorId(ID)).willReturn(Optional.of(reporte()));

        assertThatThrownBy(() -> servicio.descartarFoto(ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no tiene foto");
        verify(reportes, never()).marcarFotoDescartada(any());
    }
}
