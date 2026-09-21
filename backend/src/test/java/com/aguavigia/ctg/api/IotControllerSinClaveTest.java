package com.aguavigia.ctg.api;

import com.aguavigia.ctg.api.error.ManejadorGlobalDeErrores;
import com.aguavigia.ctg.domain.port.in.RegistrarLecturaDePresionUseCase;
import com.aguavigia.ctg.domain.port.out.RevocacionSesionPort;
import com.aguavigia.ctg.infrastructure.config.SecurityConfig;
import com.aguavigia.ctg.infrastructure.security.JwtProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sin `IOT_KEY` el endpoint no atiende a nadie: ni siquiera con una clave que «coincida» con la vacía. */
@WebMvcTest(IotController.class)
@Import({ManejadorGlobalDeErrores.class, SecurityConfig.class})
@TestPropertySource(properties = {
        "aguavigia.rate-limit.reglas=",
        "aguavigia.iot.key="
})
class IotControllerSinClaveTest {

    @MockitoBean
    private RevocacionSesionPort revocacion;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegistrarLecturaDePresionUseCase lecturas;

    @MockitoBean
    private JwtProvider jwtProvider;

    @MockitoBean(name = "redisTemplate")
    private RedisTemplate<String, String> redisTemplateMock;

    @Test
    void debeResponder503ConRfc7807SiElServidorNoTieneClaveConfigurada() throws Exception {
        mockMvc.perform(post("/api/iot/presion")
                        .header("X-IoT-Key", "")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sensorId\":\"s\",\"sectorId\":\"bocagrande\",\"presionPsi\":10.0}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(503));

        verify(lecturas, never()).registrar(any(), any(), any(), any());
    }
}
