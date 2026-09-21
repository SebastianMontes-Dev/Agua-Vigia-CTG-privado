package com.aguavigia.ctg.infrastructure.mail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValidacionDeUrlPublicaProdTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhost:8080",
            "https://localhost",
            "http://127.0.0.1:8081",
            "http://0.0.0.0",
            "http://[::1]:8080",
            "http://LOCALHOST"
    })
    void debeRechazarUnaUrlQueApuntaALaMaquinaLocal(String url) {
        assertThatThrownBy(() -> new ValidacionDeUrlPublicaProd(url))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_URL_PUBLICA");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "no es una url", "aguavigia.example.com"})
    void debeRechazarUnaUrlVaciaOSinEsquemaNiHost(String url) {
        assertThatThrownBy(() -> new ValidacionDeUrlPublicaProd(url))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_URL_PUBLICA");
    }

    @Test
    void debeAceptarUnDominioPublico() {
        assertThatCode(() -> new ValidacionDeUrlPublicaProd("https://aguavigia.example.com"))
                .doesNotThrowAnyException();
    }

    @Test
    void debeAceptarUnDominioPublicoConPuertoYRuta() {
        assertThatCode(() -> new ValidacionDeUrlPublicaProd("http://aguavigia.example.com:8443/base"))
                .doesNotThrowAnyException();
    }
}
