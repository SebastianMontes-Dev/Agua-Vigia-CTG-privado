package com.aguavigia.ctg.infrastructure.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Los factores existen para las instancias que no son la real (la simulación y las pruebas de carga, que crean cientos de vecinos y
 * dispositivos desde una sola IP). `factor` sube los topes de lo que no es una credencial ni un correo; `factorCuentas` es un
 * mando aparte, a propósito: quien sube el factor para una prueba de carga no debe aflojar por descuido el freno a las claves
 * ni a los correos salientes. Con 1, que es el valor por defecto, no cambia nada.
 */
class RateLimitPropertiesTest {

    private static final List<RateLimitProperties.Regla> REGLAS = List.of(
            new RateLimitProperties.Regla("/api/dispositivos", 10, 3600),
            new RateLimitProperties.Regla("/api/reportes/**", 30, 60));

    private static final List<RateLimitProperties.Regla> REGLAS_DE_CUENTAS = List.of(
            new RateLimitProperties.Regla("/api/veedor/sesion", 10, 300),
            new RateLimitProperties.Regla("/api/vecino/sesion", 10, 600),
            new RateLimitProperties.Regla("/api/cuentas/**", 10, 600),
            new RateLimitProperties.Regla("/api/suscripciones/**", 10, 600),
            new RateLimitProperties.Regla("/api/veedor/segundo-factor/**", 10, 300),
            new RateLimitProperties.Regla("/api/veedor/cuenta/**", 10, 300));

    private static int limiteDe(RateLimitProperties propiedades, String ruta) {
        return propiedades.reglas().stream().filter(r -> ruta.equals(r.ruta())).findFirst().orElseThrow().limite();
    }

    @Test
    void sinFactorLosTopesQuedanComoEstan() {
        RateLimitProperties propiedades = new RateLimitProperties(REGLAS, 1, 1);

        assertThat(propiedades.reglas()).containsExactlyElementsOf(REGLAS);
    }

    @Test
    void elFactorMultiplicaElLimiteDeCadaReglaYNoLaVentana() {
        RateLimitProperties propiedades = new RateLimitProperties(REGLAS, 100, 1);

        assertThat(propiedades.reglas()).containsExactly(
                new RateLimitProperties.Regla("/api/dispositivos", 1000, 3600),
                new RateLimitProperties.Regla("/api/reportes/**", 3000, 60));
    }

    @Test
    void elFactorNoEscalaLasRutasDeClavesNiDeCorreo() {
        List<RateLimitProperties.Regla> todas = new java.util.ArrayList<>(REGLAS);
        todas.addAll(REGLAS_DE_CUENTAS);

        RateLimitProperties propiedades = new RateLimitProperties(todas, 100, 1);

        assertThat(limiteDe(propiedades, "/api/dispositivos")).isEqualTo(1000);
        for (RateLimitProperties.Regla original : REGLAS_DE_CUENTAS) {
            assertThat(limiteDe(propiedades, original.ruta()))
                    .as("la ruta %s sigue con su tope de producción", original.ruta())
                    .isEqualTo(original.limite());
        }
    }

    @Test
    void elFactorDeCuentasEscalaSoloLasRutasDeClavesYCorreo() {
        List<RateLimitProperties.Regla> todas = new java.util.ArrayList<>(REGLAS);
        todas.addAll(REGLAS_DE_CUENTAS);

        RateLimitProperties propiedades = new RateLimitProperties(todas, 1, 50);

        assertThat(limiteDe(propiedades, "/api/dispositivos")).isEqualTo(10);
        assertThat(limiteDe(propiedades, "/api/reportes/**")).isEqualTo(30);
        assertThat(limiteDe(propiedades, "/api/cuentas/**")).isEqualTo(500);
        assertThat(limiteDe(propiedades, "/api/vecino/sesion")).isEqualTo(500);
        assertThat(limiteDe(propiedades, "/api/veedor/sesion")).isEqualTo(500);
    }

    @Test
    void unFactorMenorQueUnoSeRechazaPorqueApretariaLosTopes() {
        assertThatThrownBy(() -> new RateLimitProperties(REGLAS, 0.5, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RateLimitProperties(REGLAS, 1, 0.5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unFactorPorEncimaDelTopeSeRechazaPorqueDejariaLaRutaSinLimite() {
        assertThatThrownBy(() -> new RateLimitProperties(REGLAS, RateLimitProperties.FACTOR_MAXIMO + 1, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("factor");
        assertThatThrownBy(() -> new RateLimitProperties(REGLAS, 1, RateLimitProperties.FACTOR_MAXIMO + 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new RateLimitProperties(REGLAS, RateLimitProperties.FACTOR_MAXIMO, 1).reglas()).isNotEmpty();
    }

    @Test
    void sabeSiAlgunTopeEstaMultiplicado() {
        assertThat(new RateLimitProperties(REGLAS, 1, 1).escalado()).isFalse();
        assertThat(new RateLimitProperties(REGLAS, 2, 1).escalado()).isTrue();
        assertThat(new RateLimitProperties(REGLAS, 1, 2).escalado()).isTrue();
    }

    @Test
    void sinDeclararElFactorSeLeeUno() {
        RateLimitProperties propiedades = new Binder(new MapConfigurationPropertySource(Map.of(
                "aguavigia.rate-limit.reglas[0].ruta", "/api/dispositivos",
                "aguavigia.rate-limit.reglas[0].limite", "10",
                "aguavigia.rate-limit.reglas[0].ventana-segundos", "3600")))
                .bind("aguavigia.rate-limit", RateLimitProperties.class).get();

        assertThat(propiedades.reglas()).extracting(RateLimitProperties.Regla::limite).containsExactly(10);
        assertThat(propiedades.factor()).isEqualTo(1);
        assertThat(propiedades.factorCuentas()).isEqualTo(1);
    }

    @Test
    void losFactoresSeLeenDeLaConfiguracion() {
        RateLimitProperties propiedades = new Binder(new MapConfigurationPropertySource(Map.of(
                "aguavigia.rate-limit.factor", "50",
                "aguavigia.rate-limit.factor-cuentas", "20",
                "aguavigia.rate-limit.reglas[0].ruta", "/api/dispositivos",
                "aguavigia.rate-limit.reglas[0].limite", "10",
                "aguavigia.rate-limit.reglas[0].ventana-segundos", "3600",
                "aguavigia.rate-limit.reglas[1].ruta", "/api/cuentas/**",
                "aguavigia.rate-limit.reglas[1].limite", "10",
                "aguavigia.rate-limit.reglas[1].ventana-segundos", "600")))
                .bind("aguavigia.rate-limit", RateLimitProperties.class).get();

        assertThat(propiedades.reglas()).extracting(RateLimitProperties.Regla::limite).containsExactly(500, 200);
    }
}
