package com.aguavigia.ctg.infrastructure.ratelimit;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.core.io.ClassPathResource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Los demás tests de rate limit vacían `aguavigia.rate-limit.reglas` o inventan las suyas; ninguno lee las
 * reglas reales. Esta prueba fija las que protegen endpoints públicos que cuestan algo (correo, identidades,
 * intentos de clave) para que quitarlas o aflojarlas en `application.yml` no pase sin que nadie lo note.
 */
class ReglasDeLimiteDeApplicationYmlTest {

    private static RateLimitProperties reales;

    @BeforeAll
    static void cargarApplicationYml() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        reales = new Binder(new MapConfigurationPropertySource(yaml.getObject()))
                .bind("aguavigia.rate-limit", RateLimitProperties.class).get();
    }

    private static Optional<RateLimitProperties.Regla> regla(String ruta) {
        return reales.reglas().stream().filter(r -> ruta.equals(r.ruta())).findFirst();
    }

    /** D7: crear una identidad cuesta una petición, y esa petición es lo que se limita (10 por hora por IP). */
    @Test
    void laEmisionDeTokensDeDispositivoDebeLimitarseADiezPorHoraPorIp() {
        assertThat(regla("/api/dispositivos")).hasValueSatisfying(r -> {
            assertThat(r.limite()).isEqualTo(10);
            assertThat(r.ventanaSegundos()).isEqualTo(3600);
        });
    }

    /** El registro de vecinos cae bajo `/api/cuentas/**`: mismas reglas por IP que el alta del panel. */
    @Test
    void elRegistroDeVecinosDebeEstarBajoElLimiteDeCuentas() {
        assertThat(regla("/api/cuentas/**")).hasValueSatisfying(r -> {
            assertThat(r.limite()).isEqualTo(10);
            assertThat(r.ventanaSegundos()).isEqualTo(600);
        });
    }

    @Test
    void elIngresoDeVecinosDebeTenerSuPropioLimitePorIp() {
        assertThat(regla("/api/vecino/sesion")).hasValueSatisfying(r -> {
            assertThat(r.limite()).isEqualTo(10);
            assertThat(r.ventanaSegundos()).isEqualTo(600);
        });
    }

    @Test
    void elIngresoDelPanelDebeSeguirLimitado() {
        assertThat(regla("/api/veedor/sesion")).isPresent();
    }
}
