package com.aguavigia.ctg.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RNF007: el detalle por colector de {@code /actuator/health} lo ve solo quien desarrolla. Con cualquier otro
 * perfil (incluido {@code docker}, que es como corre la demo) el endpoint público responde solo el estado global.
 */
class DetalleDeSaludPorPerfilTest {

    private static final String PROPIEDAD = "management.endpoint.health.show-details";

    private static ApplicationContextRunner conPerfiles(String perfiles) {
        return new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("spring.profiles.active=" + perfiles);
    }

    @Test
    void debeOcultarElDetalleDeSaludConElPerfilDocker() {
        conPerfiles("docker").run(contexto ->
                assertThat(contexto.getEnvironment().getProperty(PROPIEDAD)).isEqualTo("never"));
    }

    @Test
    void debeOcultarElDetalleDeSaludConElPerfilDeCarga() {
        conPerfiles("docker,carga").run(contexto ->
                assertThat(contexto.getEnvironment().getProperty(PROPIEDAD)).isEqualTo("never"));
    }

    @Test
    void debeMostrarElDetalleDeSaludSoloConElPerfilDev() {
        conPerfiles("dev").run(contexto ->
                assertThat(contexto.getEnvironment().getProperty(PROPIEDAD)).isEqualTo("always"));
    }
}
