package com.aguavigia.ctg.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResultadoDeCupoTest {

    @Test
    void unCupoConcedidoNoDebeTraerTiempoDeRenovacion() {
        assertThat(ResultadoDeCupo.conCupo().concedido()).isTrue();
        assertThat(ResultadoDeCupo.conCupo().hastaRenovar()).isNull();
    }

    @Test
    void unCupoAgotadoDebeConservarCuantoFaltaParaRenovarse() {
        ResultadoDeCupo agotado = ResultadoDeCupo.agotado(Duration.ofHours(5));

        assertThat(agotado.concedido()).isFalse();
        assertThat(agotado.hastaRenovar()).isEqualTo(Duration.ofHours(5));
    }

    @Test
    void debeRechazarUnCupoAgotadoSinTiempoDeRenovacion() {
        assertThatThrownBy(() -> new ResultadoDeCupo(false, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ResultadoDeCupo.agotado(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void debeRechazarUnCupoAgotadoConTiempoNegativo() {
        assertThatThrownBy(() -> ResultadoDeCupo.agotado(Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
