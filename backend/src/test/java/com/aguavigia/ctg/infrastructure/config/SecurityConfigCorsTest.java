package com.aguavigia.ctg.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Un frontend en otro origen solo puede mandar las cabeceras que el preflight le permite. Si `X-Dispositivo` no está
 * en la lista, el navegador bloquea todo reporte antes de que salga: la identidad de D7 viaja en esa cabecera.
 */
class SecurityConfigCorsTest {

    private static CorsConfiguration configuracionDe(String ruta) {
        CorsConfigurationSource fuente = new SecurityConfig(new ObjectMapper())
                .fuenteDeConfiguracionCors(new CorsProperties(List.of("http://localhost:5173")));
        MockHttpServletRequest peticion = new MockHttpServletRequest("POST", ruta);
        peticion.setRequestURI(ruta);
        return fuente.getCorsConfiguration(peticion);
    }

    @Test
    void debePermitirLaCabeceraDeIdentidadDelDispositivo() {
        assertThat(configuracionDe("/api/reportes").getAllowedHeaders()).contains("X-Dispositivo");
    }

    /** La foto se sube con el token de un solo uso en `X-Subida`: sin esto el navegador la bloquea en el preflight. */
    @Test
    void debePermitirLaCabeceraDelTokenDeSubida() {
        assertThat(configuracionDe("/api/reportes/r1/foto").getAllowedHeaders()).contains("X-Subida");
    }

    @Test
    void debeSeguirPermitiendoLaSesionYElTipoDeContenido() {
        assertThat(configuracionDe("/api/reportes").getAllowedHeaders())
                .contains("Authorization", "Content-Type", "X-IoT-Key");
    }

    @Test
    void sinOrigenesDeclaradosNoDebeHaberConfiguracionCors() {
        CorsConfigurationSource fuente = new SecurityConfig(new ObjectMapper())
                .fuenteDeConfiguracionCors(new CorsProperties(List.of()));
        MockHttpServletRequest peticion = new MockHttpServletRequest("POST", "/api/reportes");
        peticion.setRequestURI("/api/reportes");

        assertThat(fuente.getCorsConfiguration(peticion)).isNull();
    }
}
