package com.aguavigia.ctg.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PropuestaIngestaTest {

    private static final Instant AHORA = Instant.parse("2026-08-09T15:30:00Z");

    private PropuestaIngesta propuesta() {
        return new PropuestaIngesta(
                new PropuestaId("p-1"), new SectorId("manga"), EstadoServicio.SIN_SERVICIO,
                "acuacar", "https://acuacar.com/x", "cita", 0.6, AHORA);
    }

    @Test
    void debeNacerPendienteDeRevision() {
        assertThat(propuesta().estadoRevision()).isEqualTo(EstadoRevision.PENDIENTE);
    }

    @Test
    void aprobarYDescartarNoDebenMutarLaOriginal() {
        PropuestaIngesta original = propuesta();

        assertThat(original.aprobar().estadoRevision()).isEqualTo(EstadoRevision.APROBADA);
        assertThat(original.descartar().estadoRevision()).isEqualTo(EstadoRevision.DESCARTADA);
        assertThat(original.estadoRevision()).isEqualTo(EstadoRevision.PENDIENTE);
    }

    @Test
    void aprobarDebeSerIdempotente() {
        assertThat(propuesta().aprobar().aprobar().estadoRevision()).isEqualTo(EstadoRevision.APROBADA);
    }

    @Test
    void descartarDebeSerIdempotente() {
        assertThat(propuesta().descartar().descartar().estadoRevision()).isEqualTo(EstadoRevision.DESCARTADA);
    }

    @Test
    void noDebeDescartarUnaPropuestaYaAprobada() {
        PropuestaIngesta aprobada = propuesta().aprobar();

        assertThatThrownBy(aprobada::descartar)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ya fue aprobada");
    }

    @Test
    void noDebeAprobarUnaPropuestaYaDescartada() {
        PropuestaIngesta descartada = propuesta().descartar();

        assertThatThrownBy(descartada::aprobar)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ya fue descartada");
    }

    @Test
    void debeExigirSectorEstadoFuenteYFecha() {
        assertThatThrownBy(() -> new PropuestaIngesta(new PropuestaId("p-1"), null,
                EstadoServicio.SIN_SERVICIO, "acuacar", null, "cita", 0.6, AHORA))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new PropuestaIngesta(new PropuestaId("p-1"), new SectorId("manga"),
                null, "acuacar", null, "cita", 0.6, AHORA))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new PropuestaIngesta(new PropuestaId("p-1"), new SectorId("manga"),
                EstadoServicio.SIN_SERVICIO, "  ", null, "cita", 0.6, AHORA))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new PropuestaIngesta(new PropuestaId("p-1"), new SectorId("manga"),
                EstadoServicio.SIN_SERVICIO, "acuacar", null, "cita", 0.6, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** La confianza decide si algo puede publicarse solo: fuera de [0,1] no significa nada. */
    @Test
    void debeRechazarUnaConfianzaFueraDeRango() {
        assertThatThrownBy(() -> new PropuestaIngesta(new PropuestaId("p-1"), new SectorId("manga"),
                EstadoServicio.SIN_SERVICIO, "acuacar", null, "cita", 1.4, AHORA))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new PropuestaIngesta(new PropuestaId("p-1"), new SectorId("manga"),
                EstadoServicio.SIN_SERVICIO, "acuacar", null, "cita", -0.1, AHORA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unaPropuestaAprobadaSePuedeAnularConSuMotivo() {
        PropuestaIngesta anulada = propuesta().aprobar().anular("El extractor leyó mal el barrio");

        assertThat(anulada.estadoRevision()).isEqualTo(EstadoRevision.ANULADA);
        assertThat(anulada.motivoAnulacion()).isEqualTo("El extractor leyó mal el barrio");
    }

    @Test
    void anularExigeUnMotivo() {
        assertThatThrownBy(() -> propuesta().aprobar().anular(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> propuesta().aprobar().anular(null)).isInstanceOf(IllegalArgumentException.class);
    }

    /** Solo se anula lo que ya movió el mapa: una pendiente o descartada nunca lo movió. */
    @Test
    void soloSeAnulaUnaPropuestaAprobada() {
        assertThatThrownBy(() -> propuesta().anular("x")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> propuesta().descartar().anular("x")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> propuesta().aprobar().anular("x").anular("otra vez"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void unaPropuestaAnuladaNoSePuedeAprobarNiDescartar() {
        PropuestaIngesta anulada = propuesta().aprobar().anular("x");

        assertThatThrownBy(anulada::aprobar).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(anulada::descartar).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void elMotivoSoloVaConElEstadoAnulada() {
        PropuestaIngesta base = propuesta();

        assertThatThrownBy(() -> new PropuestaIngesta(base.id(), base.sectorId(), base.estadoPropuesto(), base.fuente(),
                base.urlOriginal(), base.citaTextual(), base.confianza(), base.detectadaEn(), EstadoRevision.ANULADA,
                null, null, null, null, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PropuestaIngesta(base.id(), base.sectorId(), base.estadoPropuesto(), base.fuente(),
                base.urlOriginal(), base.citaTextual(), base.confianza(), base.detectadaEn(), EstadoRevision.APROBADA,
                null, null, null, null, null, "sin estar anulada")).isInstanceOf(IllegalArgumentException.class);
    }

    private PropuestaIngesta conVentana(String sector, String url, Instant inicio, Instant fin) {
        return new PropuestaIngesta(new PropuestaId("p-" + sector), new SectorId(sector), EstadoServicio.SIN_SERVICIO,
                "acuacar", url, "cita", 0.85, AHORA, inicio, fin);
    }

    /** Un boletín nombra muchos barrios y genera una propuesta por cada uno: todas caen en el mismo corte. */
    @Test
    void lasPropuestasDeUnMismoBoletinComparteElCorte() {
        Instant inicio = Instant.parse("2026-08-21T14:00:00Z");
        Instant fin = Instant.parse("2026-08-21T23:00:00Z");

        CorteId deManga = conVentana("manga", "https://acuacar.com/2854", inicio, fin).idDelCorte();
        CorteId deBocagrande = conVentana("bocagrande", "https://acuacar.com/2854", inicio, fin).idDelCorte();

        assertThat(deManga).isEqualTo(deBocagrande);
    }

    @Test
    void otroBoletinOOtraVentanaDaOtroCorte() {
        Instant inicio = Instant.parse("2026-08-21T14:00:00Z");
        Instant fin = Instant.parse("2026-08-21T23:00:00Z");
        CorteId base = conVentana("manga", "https://acuacar.com/2854", inicio, fin).idDelCorte();

        assertThat(conVentana("manga", "https://acuacar.com/2900", inicio, fin).idDelCorte()).isNotEqualTo(base);
        assertThat(conVentana("manga", "https://acuacar.com/2854", inicio, fin.plusSeconds(3600)).idDelCorte())
                .isNotEqualTo(base);
    }

    /** El id debe seguir siendo el que ya se guardó en los cortes existentes: cambiarlo los dejaría huérfanos. */
    @Test
    void elIdDelCorteSeDerivaDeLaUrlYLaVentanaComoSiempre() {
        Instant inicio = Instant.parse("2026-08-21T14:00:00Z");
        Instant fin = Instant.parse("2026-08-21T23:00:00Z");
        String semilla = "https://acuacar.com/2854|" + inicio + "|" + fin;

        assertThat(conVentana("manga", "https://acuacar.com/2854", inicio, fin).idDelCorte().valor())
                .isEqualTo(java.util.UUID.nameUUIDFromBytes(semilla.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString());
    }

    @Test
    void unaPropuestaSinVentanaNoTieneCorte() {
        assertThatThrownBy(() -> propuesta().idDelCorte()).isInstanceOf(IllegalStateException.class);
    }
}
