package com.aguavigia.ctg.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ConsentimientoTest {

    private static final Instant AHORA = Instant.parse("2026-10-01T15:00:00Z");

    @Test
    void debeGuardarElTipoLaVersionYLaFechaDeLoQueLaPersonaAcepto() {
        Consentimiento consentimiento =
                new Consentimiento(TipoConsentimiento.PRIVACIDAD, "2026-10-v1", AHORA);

        assertThat(consentimiento.tipo()).isEqualTo(TipoConsentimiento.PRIVACIDAD);
        assertThat(consentimiento.version()).isEqualTo("2026-10-v1");
        assertThat(consentimiento.fecha()).isEqualTo(AHORA);
    }

    @Test
    void debeRechazarUnConsentimientoSinVersion() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> new Consentimiento(TipoConsentimiento.AVISOS, " ", AHORA));
    }

    @Test
    void debeRechazarUnConsentimientoSinTipoNiFecha() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> new Consentimiento(null, "v1", AHORA));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new Consentimiento(TipoConsentimiento.AVISOS, "v1", null));
    }
}
