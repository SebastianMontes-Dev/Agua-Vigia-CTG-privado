package com.aguavigia.ctg.infrastructure.config;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BUG-120 (ADR-080): sin un proxy de confianza delante, `server.forward-headers-strategy` haría que
 * Spring tomara la IP del cliente de un X-Forwarded-For que escribe el propio cliente, y cambiarlo en
 * cada petición bastaría para saltarse el rate limit por IP y falsificar la IP de la auditoría.
 */
class SinProxyDeConfianzaTest {

    @ParameterizedTest
    @ValueSource(strings = {"dev", "docker"})
    void ningunPerfilLocalDebeConfiarEnLasCabecerasReenviadas(String perfil) {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("SPRING_PROFILES_ACTIVE=" + perfil)
                .run(contexto -> assertThat(contexto.getEnvironment()
                        .getProperty("server.forward-headers-strategy")).isNull());
    }
}
