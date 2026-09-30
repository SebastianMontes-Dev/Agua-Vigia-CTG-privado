package com.aguavigia.ctg.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValidacionDeSecretosTest {

    private static final String TREINTA_Y_DOS = "0123456789abcdef0123456789abcdef";

    private static MockEnvironment entorno(String... perfiles) {
        MockEnvironment entorno = new MockEnvironment();
        entorno.setActiveProfiles(perfiles);
        return entorno;
    }

    @Test
    void debeAceptarSinClaveIotYSinJwtFueraDeProduccion() {
        assertThatCode(() -> new ValidacionDeSecretos(entorno("dev"), "", "")).doesNotThrowAnyException();
    }

    @Test
    void debeRechazarUnaClaveIotCorta() {
        assertThatThrownBy(() -> new ValidacionDeSecretos(entorno("dev"), "clave-corta", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("IOT_KEY");
    }

    @Test
    void debeAceptarUnaClaveIotDe32Caracteres() {
        assertThatCode(() -> new ValidacionDeSecretos(entorno("dev"), TREINTA_Y_DOS, "")).doesNotThrowAnyException();
    }

    @Test
    void enProduccionDebeExigirElSecretoJwt() {
        assertThatThrownBy(() -> new ValidacionDeSecretos(entorno("prod"), "", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void enProduccionDebeAceptarUnSecretoJwtDe32Bytes() {
        assertThatCode(() -> new ValidacionDeSecretos(entorno("prod"), "", TREINTA_Y_DOS)).doesNotThrowAnyException();
    }
}
