package com.aguavigia.ctg.infrastructure.ingest;

import com.aguavigia.ctg.domain.SaludDeColector;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class EstadoColectorRegistryTest {

    private static final Instant AHORA = Instant.parse("2026-09-29T12:00:00Z");

    private final EstadoColectorRegistry registro = new EstadoColectorRegistry(() -> AHORA);

    @Test
    void sinCiclosNoDebeHaberSaludNiColectoresCaidos() {
        assertThat(registro.salud()).isEmpty();
        assertThat(registro.hayAlgunColectorCaido()).isFalse();
    }

    @Test
    void saludDebeSerLaTelemetriaDeCadaColectorOrdenadaPorNombre() {
        registro.registrarExito("rss", 4);
        registro.registrarExito("acuacar", 7);
        registro.registrarFallo("rss", "sin red");

        assertThat(registro.salud()).containsExactly(
                new SaludDeColector("acuacar", AHORA, null, null, 7, 0.0, 0),
                new SaludDeColector("rss", AHORA, AHORA, "sin red", 4, 0.5, 1));
    }

    /** Una tasa de error histórica no basta: tres ciclos seguidos fallando sí reporta caído al colector. */
    @Test
    void unColectorSeReportaCaidoTrasTresFallosSeguidosYSeRecuperaConUnExito() {
        registro.registrarFallo("acuacar", "timeout");
        registro.registrarFallo("acuacar", "timeout");
        assertThat(registro.hayAlgunColectorCaido()).isFalse();

        registro.registrarFallo("acuacar", "timeout");
        assertThat(registro.hayAlgunColectorCaido()).isTrue();

        registro.registrarExito("acuacar", 2);
        assertThat(registro.hayAlgunColectorCaido()).isFalse();
        assertThat(registro.salud().get(0).fallosConsecutivos()).isZero();
    }
}
