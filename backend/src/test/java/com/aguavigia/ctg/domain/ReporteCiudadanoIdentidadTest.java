package com.aguavigia.ctg.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ReporteCiudadanoIdentidadTest {

    private static final Instant AHORA = Instant.parse("2026-10-01T15:00:00Z");

    private static ReporteCiudadano nuevo() {
        return new ReporteCiudadano(new ReporteId("r-1"), new SectorId("manga"), TipoReporte.SIN_AGUA, null,
                new HuellaDispositivo("h-1"), AHORA);
    }

    @Test
    void unReporteNuevoDebeNacerSinVerificacionNiRed() {
        ReporteCiudadano reporte = nuevo();

        assertThat(reporte.verificacion()).isEqualTo(NivelDeVerificacion.NINGUNA);
        assertThat(reporte.redHash()).isNull();
    }

    @Test
    void conIdentidadDebeFijarElNivelYLaRed() {
        ReporteCiudadano reporte = nuevo().conIdentidad(NivelDeVerificacion.UBICACION_VERIFICADA, "red-a");

        assertThat(reporte.verificacion()).isEqualTo(NivelDeVerificacion.UBICACION_VERIFICADA);
        assertThat(reporte.redHash()).isEqualTo("red-a");
    }

    @Test
    void unNivelNuloDebeTomarseComoNinguna() {
        assertThat(nuevo().conIdentidad(null, "red-a").verificacion()).isEqualTo(NivelDeVerificacion.NINGUNA);
    }

    /** Moderar, adjuntar una foto o confirmar no pueden borrar de dónde salió el reporte ni cuánto lo respaldaba. */
    @Test
    void lasTransformacionesDebenConservarLaVerificacionYLaRed() {
        ReporteCiudadano base = nuevo().conIdentidad(NivelDeVerificacion.CUENTA_VERIFICADA, "red-a");

        for (ReporteCiudadano derivado : new ReporteCiudadano[]{
                base.aprobar(), base.descartar(), base.conFoto("foto.jpg"),
                base.confirmar(new HuellaDispositivo("otro")), base.comoDeSensor()}) {
            assertThat(derivado.verificacion()).isEqualTo(NivelDeVerificacion.CUENTA_VERIFICADA);
            assertThat(derivado.redHash()).isEqualTo("red-a");
        }
    }
}
