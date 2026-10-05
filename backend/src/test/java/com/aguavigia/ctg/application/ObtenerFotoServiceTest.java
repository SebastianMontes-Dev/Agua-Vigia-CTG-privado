package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.FotoLeida;
import com.aguavigia.ctg.domain.HuellaDispositivo;
import com.aguavigia.ctg.domain.ReporteCiudadano;
import com.aguavigia.ctg.domain.ReporteId;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.TipoReporte;
import com.aguavigia.ctg.domain.port.out.AlmacenamientoPort;
import com.aguavigia.ctg.domain.port.out.ReporteCiudadanoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * La foto es evidencia para el veedor, no contenido público: al público solo se le sirve la de un reporte aprobado cuya
 * foto no se descartó. Todo lo demás responde igual que un archivo que no existe, para no revelar qué hay.
 */
class ObtenerFotoServiceTest {

    private static final Instant AHORA = Instant.parse("2026-10-01T15:00:00Z");
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1};
    private static final String SHA = "a".repeat(64);

    private ReporteCiudadanoRepository reportes;
    private AlmacenamientoPort almacenamiento;
    private ObtenerFotoService servicio;

    @BeforeEach
    void montar() {
        reportes = mock(ReporteCiudadanoRepository.class);
        almacenamiento = mock(AlmacenamientoPort.class);
        servicio = new ObtenerFotoService(reportes, almacenamiento);
        given(almacenamiento.leer("x.jpg")).willReturn(Optional.of(JPEG));
        given(almacenamiento.leer("x.png")).willReturn(Optional.of(JPEG));
    }

    private static ReporteCiudadano reporte() {
        return new ReporteCiudadano(new ReporteId("r1"), new SectorId("manga"), TipoReporte.SIN_AGUA, null,
                new HuellaDispositivo("h"), AHORA).conFoto("/api/fotos/x.jpg", SHA);
    }

    private void existe(ReporteCiudadano reporte) {
        given(reportes.buscarPorNombreDeFoto("x.jpg")).willReturn(Optional.of(reporte));
    }

    @Test
    void alPublicoLeSirveLaFotoDeUnReporteAprobado() {
        existe(reporte().aprobar());

        Optional<FotoLeida> foto = servicio.paraPublico("x.jpg");

        assertThat(foto).isPresent();
        assertThat(foto.get().contenido()).isEqualTo(JPEG);
        assertThat(foto.get().tipo()).isEqualTo("image/jpeg");
    }

    @Test
    void alPublicoNoLeSirveLaFotoDeUnReporteQueAunEstaEnRevision() {
        existe(reporte());

        assertThat(servicio.paraPublico("x.jpg")).isEmpty();
        verify(almacenamiento, never()).leer(any());
    }

    @Test
    void alPublicoNoLeSirveLaFotoDeUnReporteDescartado() {
        existe(reporte().descartar());

        assertThat(servicio.paraPublico("x.jpg")).isEmpty();
    }

    @Test
    void alPublicoNoLeSirveUnaFotoDescartadaAunqueElReporteEsteAprobado() {
        existe(reporte().aprobar().descartarFoto());

        assertThat(servicio.paraPublico("x.jpg")).isEmpty();
    }

    @Test
    void alPanelLeSirveLaFotoSiempre() {
        existe(reporte().descartar().descartarFoto());

        assertThat(servicio.paraPanel("x.jpg")).isPresent();
    }

    @Test
    void unaFotoQueNingunReporteReclamaNoSeSirveANadie() {
        given(reportes.buscarPorNombreDeFoto("huerfana.jpg")).willReturn(Optional.empty());

        assertThat(servicio.paraPublico("huerfana.jpg")).isEmpty();
        assertThat(servicio.paraPanel("huerfana.jpg")).isEmpty();
    }

    @Test
    void siElArchivoYaNoEstaEnDiscoNoSeSirveNada() {
        existe(reporte().aprobar());
        given(almacenamiento.leer("x.jpg")).willReturn(Optional.empty());

        assertThat(servicio.paraPublico("x.jpg")).isEmpty();
    }

    @Test
    void unPngDebeServirseConSuTipo() {
        given(reportes.buscarPorNombreDeFoto("x.png")).willReturn(Optional.of(
                new ReporteCiudadano(new ReporteId("r2"), new SectorId("manga"), TipoReporte.SIN_AGUA, null,
                        new HuellaDispositivo("h"), AHORA).conFoto("/api/fotos/x.png", SHA).aprobar()));

        assertThat(servicio.paraPublico("x.png")).get().extracting(FotoLeida::tipo).isEqualTo("image/png");
    }

    /** Un nombre que sale de la carpeta o trae barras no se busca ni se lee: se trata como inexistente. */
    @Test
    void unNombreQueIntentaEscaparDelDirectorioNoDebeBuscarseNiLeerse() {
        for (String malo : new String[]{"../secreto.jpg", "a/b.jpg", "a\\b.jpg", "..", "", "x.jpg%00", "x.svg"}) {
            assertThat(servicio.paraPublico(malo)).as(malo).isEmpty();
            assertThat(servicio.paraPanel(malo)).as(malo).isEmpty();
        }
        verify(reportes, never()).buscarPorNombreDeFoto(any());
        verify(almacenamiento, never()).leer(any());
    }
}
