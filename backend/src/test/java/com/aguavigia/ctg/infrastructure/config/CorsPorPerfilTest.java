package com.aguavigia.ctg.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fase 5 de plan-validacion-backend.md: el frontend se sirve desde su propio dev server, así que el
 * perfil que corre dentro de docker-compose (el que usa quien levanta el entorno local) tiene que
 * dejarlo pasar; y producción, que va detrás del mismo proxy, no.
 *
 * El perfil se elige con SPRING_PROFILES_ACTIVE y no con @ActiveProfiles porque application.yml lo
 * fija con `spring.profiles.active: ${SPRING_PROFILES_ACTIVE:dev}` y ese valor gana.
 */
class CorsPorPerfilTest {

    private static ApplicationContextRunner conPerfil(String perfil, String... propiedades) {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(Configuracion.class)
                .withPropertyValues("SPRING_PROFILES_ACTIVE=" + perfil);
        return propiedades.length == 0 ? runner : runner.withPropertyValues(propiedades);
    }

    @Test
    void elPerfilDockerDebePermitirLosOrigenesHabitualesDeUnDevServerLocal() {
        conPerfil("docker").run(contexto -> {
            CorsProperties cors = contexto.getBean(CorsProperties.class);
            assertThat(cors.habilitado()).isTrue();
            assertThat(cors.origenesPermitidos()).containsExactlyInAnyOrder(
                    "http://localhost:5173", "http://localhost:3000", "http://localhost:4200");
        });
    }

    @Test
    void unOrigenDeclaradoPorEntornoDebeReemplazarALosHabituales() {
        conPerfil("docker", "CORS_ORIGENES=http://localhost:8000,http://localhost:9000").run(contexto ->
                assertThat(contexto.getBean(CorsProperties.class).origenesPermitidos())
                        .containsExactly("http://localhost:8000", "http://localhost:9000"));
    }

    @Test
    void produccionDebeSeguirCerrado() {
        conPerfil("prod").run(contexto ->
                assertThat(contexto.getBean(CorsProperties.class).habilitado()).isFalse());
    }

    @EnableConfigurationProperties(CorsProperties.class)
    static class Configuracion {
    }
}
