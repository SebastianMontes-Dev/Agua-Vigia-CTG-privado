package com.aguavigia.ctg.infrastructure.config;

import com.aguavigia.ctg.infrastructure.ratelimit.RateLimitProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADR-083: el perfil `carga` existe para la demo de carga y solo para ella. Estas pruebas fijan las dos mitades:
 * que con él el límite por IP queda vacío y hay margen de conexiones, y que sin él nada de eso cambia.
 */
class PerfilCargaTest {

    private static RateLimitProperties limites(Environment entorno) {
        return Binder.get(entorno).bind("aguavigia.rate-limit", RateLimitProperties.class).get();
    }

    private static ApplicationContextRunner conPerfiles(String perfiles) {
        return new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("spring.profiles.active=" + perfiles);
    }

    @Test
    void conElPerfilCargaElLimitePorIpDebeQuedarVacioYElTopeDeSseSubir() {
        conPerfiles("docker,carga").run(contexto -> {
            assertThat(limites(contexto.getEnvironment()).reglas()).isEmpty();
            assertThat(contexto.getEnvironment().getProperty("aguavigia.sse.max-conexiones", Integer.class)).isEqualTo(45000);
            assertThat(contexto.getEnvironment().getProperty("server.tomcat.max-connections", Integer.class)).isGreaterThan(45000);
        });
    }

    @Test
    void sinElPerfilCargaDebenConservarseElLimitePorIpYElTopeNormalDeSse() {
        conPerfiles("docker").run(contexto -> {
            assertThat(limites(contexto.getEnvironment()).reglas()).isNotEmpty();
            assertThat(contexto.getEnvironment().getProperty("aguavigia.sse.max-conexiones", Integer.class)).isEqualTo(20000);
        });
    }

    @Test
    void elPerfilCargaNoDebeConfiarEnLasCabecerasReenviadas() {
        conPerfiles("docker,carga").run(contexto -> assertThat(contexto.getEnvironment()
                .getProperty("server.forward-headers-strategy")).isNull());
    }

    @Test
    void elPerfilCargaNoDebeAflojarElCupoPorDispositivoDeRf006() {
        conPerfiles("docker,carga").run(contexto -> assertThat(contexto.getEnvironment()
                .getProperty("aguavigia.reportes.limite-por-dispositivo", Integer.class)).isEqualTo(3));
    }
}
