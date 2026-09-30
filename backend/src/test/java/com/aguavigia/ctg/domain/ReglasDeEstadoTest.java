package com.aguavigia.ctg.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReglasDeEstadoTest {

    private final ReglasDeEstado reglas = ReglasDeEstado.porDefecto();

    /** Pasada la promesa basta la mitad del umbral, redondeada hacia arriba: confirmar que volvió el agua es lo escaso. */
    @Test
    void elQuorumReducidoEsLaMitadDelUmbralRedondeadaHaciaArriba() {
        assertThat(reglas.quorumReducido(6)).isEqualTo(3);
        assertThat(reglas.quorumReducido(15)).isEqualTo(8);
        assertThat(reglas.quorumReducido(11)).isEqualTo(6);
    }

    @Test
    void losPlazosDebenSerPositivos() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new ReglasDeEstado(
                java.time.Duration.ZERO, java.time.Duration.ofHours(6), java.time.Duration.ofHours(24), 2))
                .isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new ReglasDeEstado(
                java.time.Duration.ofHours(72), java.time.Duration.ofHours(6), java.time.Duration.ofHours(-1), 2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void elQuorumReducidoNuncaBajaDelMinimoConfigurado() {
        assertThat(reglas.quorumReducido(3)).isEqualTo(2);
        assertThat(reglas.quorumReducido(1)).isEqualTo(2);
    }
}
