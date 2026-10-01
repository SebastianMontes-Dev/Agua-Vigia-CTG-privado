package com.aguavigia.ctg.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class DispositivoTest {

    private static final Instant AHORA = Instant.parse("2026-10-01T15:00:00Z");

    @Test
    void debeGuardarSuIdYSusFechas() {
        Dispositivo dispositivo = new Dispositivo(new DispositivoId("d-1"), AHORA, AHORA.plusSeconds(60));

        assertThat(dispositivo.id().valor()).isEqualTo("d-1");
        assertThat(dispositivo.creadoEn()).isEqualTo(AHORA);
        assertThat(dispositivo.ultimoVisto()).isEqualTo(AHORA.plusSeconds(60));
    }

    @Test
    void debeRechazarUnIdVacio() {
        assertThatIllegalArgumentException().isThrownBy(() -> new DispositivoId(" "));
        assertThatIllegalArgumentException().isThrownBy(() -> new DispositivoId(null));
    }

    @Test
    void debeExigirIdYFechas() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Dispositivo(null, AHORA, AHORA));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new Dispositivo(new DispositivoId("d-1"), null, AHORA));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new Dispositivo(new DispositivoId("d-1"), AHORA, null));
    }

    /** Un dispositivo no puede haberse visto antes de existir: sería una fecha manipulada. */
    @Test
    void debeRechazarUnUltimoVistoAnteriorALaCreacion() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> new Dispositivo(new DispositivoId("d-1"), AHORA, AHORA.minusSeconds(1)));
    }
}
