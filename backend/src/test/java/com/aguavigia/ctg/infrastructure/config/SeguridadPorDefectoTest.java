package com.aguavigia.ctg.infrastructure.config;

import com.aguavigia.ctg.domain.port.out.RevocacionSesionPort;
import com.aguavigia.ctg.infrastructure.security.JwtProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La regla por defecto de la cadena de seguridad es denegar: una ruta que nadie declaró pública no
 * queda abierta por descuido. Las cabeceras de seguridad viajan en las respuestas públicas.
 */
@WebMvcTest(controllers = ControladorDePruebaRateLimit.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "aguavigia.rate-limit.reglas=")
class SeguridadPorDefectoTest {

    @MockitoBean
    private RevocacionSesionPort revocacion;

    @MockitoBean
    private JwtProvider jwtProvider;

    // RateLimitConfig entra en el slice de MVC y pide este bean aunque no haya reglas.
    @MockitoBean(name = "redisTemplate")
    private org.springframework.data.redis.core.RedisTemplate<String, String> redisTemplateMock;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unaRutaNoDeclaradaComoPublicaDebeExigirAutenticacion() throws Exception {
        mockMvc.perform(get("/ruta-no-declarada")).andExpect(status().isUnauthorized());
    }

    @Test
    void unaRutaPublicaDeclaradaDebeResponderConLasCabecerasDeSeguridad() throws Exception {
        mockMvc.perform(get("/api/sectores/prueba-rate-limit/sin-proteger"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy",
                        org.hamcrest.Matchers.containsString("frame-ancestors 'none'")))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }
}
