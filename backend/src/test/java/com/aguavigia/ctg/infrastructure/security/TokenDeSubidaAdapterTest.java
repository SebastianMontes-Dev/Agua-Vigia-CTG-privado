package com.aguavigia.ctg.infrastructure.security;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TokenDeSubidaAdapterTest {

    private final TokenDeSubidaAdapter adaptador = new TokenDeSubidaAdapter();

    @Test
    void cadaTokenDebeSerDistintoYImposibleDeAdivinar() {
        Set<String> vistos = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            assertThat(vistos.add(adaptador.nuevo())).isTrue();
        }
    }

    @Test
    void elTokenDebeSerSeguroParaUnaCabeceraHttp() {
        assertThat(adaptador.nuevo()).matches("[A-Za-z0-9_-]{40,}");
    }

    @Test
    void elHashDebeSerDeterministaYNoContenerElToken() {
        String token = adaptador.nuevo();

        assertThat(adaptador.hash(token)).isEqualTo(adaptador.hash(token)).hasSize(64).doesNotContain(token);
        assertThat(adaptador.hash(token)).isNotEqualTo(adaptador.hash(adaptador.nuevo()));
    }
}
