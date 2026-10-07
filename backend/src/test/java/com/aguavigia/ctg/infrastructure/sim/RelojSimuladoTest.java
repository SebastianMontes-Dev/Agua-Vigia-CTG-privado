package com.aguavigia.ctg.infrastructure.sim;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El reloj de la instancia de simulación (D33): sigue al reloj real con un desfase que el simulador mueve, para comprimir horas en
 * minutos. Sin desfase es el reloj de siempre.
 */
class RelojSimuladoTest {

    private static final Instant REAL = Instant.parse("2026-10-02T12:00:00Z");

    private Instant ahoraReal;
    private RelojSimulado reloj;

    @BeforeEach
    void montar() {
        ahoraReal = REAL;
        reloj = new RelojSimulado(new Clock() {
            @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return ahoraReal; }
        });
    }

    @Test
    void sinTocarloEsElRelojReal() {
        assertThat(reloj.ahora()).isEqualTo(REAL);
        assertThat(reloj.desfase()).isEqualTo(Duration.ZERO);
    }

    @Test
    void fijarlaEnUnInstanteLoDejaAhiYSigueAndandoConElRelojReal() {
        Instant manana = Instant.parse("2026-10-03T08:00:00Z");

        reloj.fijarEn(manana);
        assertThat(reloj.ahora()).isEqualTo(manana);

        ahoraReal = REAL.plusSeconds(90);
        assertThat(reloj.ahora()).as("el tiempo real sigue corriendo").isEqualTo(manana.plusSeconds(90));
    }

    @Test
    void sePuedeFijarEnUnInstanteAnteriorParaReiniciarLaSimulacion() {
        reloj.fijarEn(REAL.plus(Duration.ofDays(3)));
        reloj.fijarEn(REAL.minus(Duration.ofHours(5)));

        assertThat(reloj.ahora()).isEqualTo(REAL.minus(Duration.ofHours(5)));
    }

    @Test
    void avanzarSumaAlDesfaseYAcumula() {
        reloj.avanzar(Duration.ofHours(2));
        reloj.avanzar(Duration.ofMinutes(30));

        assertThat(reloj.ahora()).isEqualTo(REAL.plus(Duration.ofMinutes(150)));
        assertThat(reloj.desfase()).isEqualTo(Duration.ofMinutes(150));
    }

    @Test
    void noSeRetrocedeAvanzandoUnaDuracionNegativaNiNula() {
        assertThatThrownBy(() -> reloj.avanzar(Duration.ofSeconds(-1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reloj.avanzar(Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void volverAlRelojRealQuitaElDesfase() {
        reloj.fijarEn(REAL.plus(Duration.ofDays(1)));

        reloj.volverAlRelojReal();

        assertThat(reloj.ahora()).isEqualTo(REAL);
    }
}
