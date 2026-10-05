package com.aguavigia.ctg.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La foto es evidencia para el veedor, no contenido público: solo se muestra si el reporte está aprobado y la foto no
 * se descartó, y nunca vota.
 */
class ReporteCiudadanoFotoTest {

    private static final Instant AHORA = Instant.parse("2026-10-01T15:00:00Z");
    private static final String SHA = "a".repeat(64);

    private static ReporteCiudadano nuevo() {
        return new ReporteCiudadano(new ReporteId("r-1"), new SectorId("manga"), TipoReporte.SIN_AGUA, null,
                new HuellaDispositivo("h-1"), AHORA);
    }

    @Test
    void unReporteNuevoNoTieneFotoNiHashNiFotoDescartada() {
        ReporteCiudadano reporte = nuevo();

        assertThat(reporte.fotoUrl()).isNull();
        assertThat(reporte.fotoSha256()).isNull();
        assertThat(reporte.fotoDescartada()).isFalse();
        assertThat(reporte.estadoDeFoto()).isEqualTo(EstadoDeFoto.SIN_FOTO);
    }

    @Test
    void conFotoDebeGuardarLaUrlYElHashDeLoQueSeGuardo() {
        ReporteCiudadano conFoto = nuevo().conFoto("/api/fotos/x.jpg", SHA);

        assertThat(conFoto.fotoUrl()).isEqualTo("/api/fotos/x.jpg");
        assertThat(conFoto.fotoSha256()).isEqualTo(SHA);
        assertThat(conFoto.estadoDeFoto()).isEqualTo(EstadoDeFoto.EN_REVISION);
    }

    @Test
    void descartarLaFotoDebeConservarLaUrlParaQueElPanelSepaQueHuboUna() {
        ReporteCiudadano descartada = nuevo().conFoto("/api/fotos/x.jpg", SHA).descartarFoto();

        assertThat(descartada.fotoUrl()).isEqualTo("/api/fotos/x.jpg");
        assertThat(descartada.fotoDescartada()).isTrue();
        assertThat(descartada.estadoDeFoto()).isEqualTo(EstadoDeFoto.DESCARTADA);
    }

    @Test
    void descartarLaFotoDeUnReporteSinFotoNoDebeHacerNada() {
        ReporteCiudadano reporte = nuevo();

        assertThat(reporte.descartarFoto()).isEqualTo(reporte);
    }

    @Test
    void laFotoSoloDebeSerPublicaSiElReporteEstaAprobadoYLaFotoNoSeDescarto() {
        ReporteCiudadano conFoto = nuevo().conFoto("/api/fotos/x.jpg", SHA);

        assertThat(conFoto.fotoEsPublica()).as("pendiente").isFalse();
        assertThat(conFoto.aprobar().fotoEsPublica()).as("aprobado").isTrue();
        assertThat(conFoto.descartar().fotoEsPublica()).as("reporte descartado").isFalse();
        assertThat(conFoto.aprobar().descartarFoto().fotoEsPublica()).as("foto descartada").isFalse();
        assertThat(nuevo().aprobar().fotoEsPublica()).as("sin foto").isFalse();
    }

    @Test
    void elNombreDeLaFotoDebeSalirDeLaUltimaParteDeLaUrlSeaLaViejaOLaNueva() {
        assertThat(nuevo().conFoto("/fotos/abc.jpg", SHA).nombreDeFoto()).contains("abc.jpg");
        assertThat(nuevo().conFoto("/api/fotos/abc.png", SHA).nombreDeFoto()).contains("abc.png");
        assertThat(nuevo().nombreDeFoto()).isEmpty();
    }

    /** Moderar, confirmar o cambiar la identidad no pueden perder la foto ni su estado. */
    @Test
    void lasTransformacionesDebenConservarLaFotoSuHashYSuDescarte() {
        ReporteCiudadano base = nuevo().conFoto("/api/fotos/x.jpg", SHA).descartarFoto();

        for (ReporteCiudadano derivado : new ReporteCiudadano[]{
                base.aprobar(), base.descartar(), base.confirmar(new HuellaDispositivo("h-2")),
                base.comoDeSensor(), base.conIdentidad(NivelDeVerificacion.CUENTA_VERIFICADA, "red")}) {
            assertThat(derivado.fotoUrl()).isEqualTo("/api/fotos/x.jpg");
            assertThat(derivado.fotoSha256()).isEqualTo(SHA);
            assertThat(derivado.fotoDescartada()).isTrue();
        }
    }
}
