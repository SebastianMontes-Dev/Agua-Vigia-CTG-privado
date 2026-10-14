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
        // El factor se escribe como ${RATE_LIMIT_FACTOR:1}: el resolutor lo lleva a su valor por defecto, que es lo que vale en uso normal.
        reales = new Binder(java.util.List.of(new MapConfigurationPropertySource(yaml.getObject())), 
                new org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver(
                        new org.springframework.core.env.StandardEnvironment()))
                .bind("aguavigia.rate-limit", RateLimitProperties.class).get();
    }

    private static Optional<RateLimitProperties.Regla> regla(String ruta) {
        return reales.reglas().stream().filter(r -> ruta.equals(r.ruta())).findFirst();
    }

    /** Sin RATE_LIMIT_FACTOR los topes son los del application.yml tal cual: el factor solo sube los de otras instancias. */
    @Test
    void sinDefinirElFactorLosTopesNoSeMultiplican() {
        assertThat(regla("/api/dispositivos")).hasValueSatisfying(r -> assertThat(r.limite()).isEqualTo(10));
        assertThat(reales.factor()).isEqualTo(1.0);
    }

    /**
     * M1 de la auditoría: subir `RATE_LIMIT_FACTOR` para una prueba de carga no puede aflojar las claves ni los correos. Cada ruta
     * de ese tipo que declara el yml debe estar en RUTAS_DE_CUENTAS (solo la mueve `factor-cuentas`).
     */
    @Test
    void lasRutasDeClavesYCorreosDelYmlNoLasMueveElFactorDeCarga() {
        java.util.List<String> credenciales = java.util.List.of("/api/veedor/sesion", "/api/vecino/sesion", "/api/cuentas/**",
                "/api/suscripciones/**", "/api/veedor/segundo-factor/**", "/api/veedor/cuenta/**");

        assertThat(credenciales).allSatisfy(ruta -> {
            assertThat(regla(ruta)).as("el yml declara %s", ruta).isPresent();
            assertThat(RateLimitProperties.RUTAS_DE_CUENTAS).contains(ruta);
        });
        assertThat(reales.factorCuentas()).isEqualTo(1.0);
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

    /** Cada lectura de una foto hace una consulta: sin tope, un barrido de nombres al azar cuesta una consulta por intento. */
    @Test
    void laLecturaDeFotosDebeTenerTopePorIp() {
        assertThat(regla("/api/fotos/**")).hasValueSatisfying(r -> {
            assertThat(r.limite()).isEqualTo(120);
            assertThat(r.ventanaSegundos()).isEqualTo(60);
        });
    }

    /** M2 de la auditoría: es público, lo pide cada pantalla y por debajo puede contar documentos: tiene su tope por IP. */
    @Test
    void laConsultaDelModoDelSistemaDebeTenerTopePorIp() {
        assertThat(regla("/api/sistema/modo")).hasValueSatisfying(r -> {
            assertThat(r.limite()).isEqualTo(60);
            assertThat(r.ventanaSegundos()).isEqualTo(60);
        });
    }

    /** El toque de «¿ya volvió el agua?» es público y escribe un voto: tiene su tope por IP, como cualquier reporte. */
    @Test
    void elToqueDeRestablecimientoDebeTenerSuTopePorIp() {
        assertThat(regla("/api/sectores/*/restablecimiento")).hasValueSatisfying(r -> {
            assertThat(r.limite()).isEqualTo(10);
            assertThat(r.ventanaSegundos()).isEqualTo(600);
        });
    }

    /**
     * Subir una foto cuesta decodificarla y guardarla (hasta 10 MB): tiene su propio tope por IP, mas estrecho que el
     * de `/api/reportes/**`, que sigue contando tambien estas peticiones.
     */
    @Test
    void laSubidaDeFotosDebeTenerUnTopeMasEstrechoQueElDeReportes() {
        assertThat(regla("/api/reportes/*/foto")).hasValueSatisfying(r -> {
            assertThat(r.limite()).isEqualTo(10);
            assertThat(r.ventanaSegundos()).isEqualTo(600);
        });
    }
}
