package com.aguavigia.ctg.api;

import com.aguavigia.ctg.api.dto.IotCoordenada;
import com.aguavigia.ctg.api.dto.IotPresionRequest;
import com.aguavigia.ctg.api.error.ManejadorGlobalDeErrores;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.port.in.RegistrarLecturaDePresionUseCase;
import com.aguavigia.ctg.domain.port.out.RevocacionSesionPort;
import com.aguavigia.ctg.infrastructure.config.SecurityConfig;
import com.aguavigia.ctg.infrastructure.security.JwtProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IotController.class)
@Import({ManejadorGlobalDeErrores.class, SecurityConfig.class})
@TestPropertySource(properties = {
        "aguavigia.rate-limit.reglas=",
        "aguavigia.iot.key=clave-de-prueba"
})
class IotControllerTest {

    // SecurityConfig construye JwtAuthenticationFilter con este puerto: el filtro consulta la
    // revocacion en cada peticion con token (ADR-039). Sin el bean, el contexto del slice no carga.
    @MockitoBean
    private RevocacionSesionPort revocacion;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private RegistrarLecturaDePresionUseCase lecturas;

    @MockitoBean
    private JwtProvider jwtProvider;

    @MockitoBean(name = "redisTemplate")
    private RedisTemplate<String, String> redisTemplateMock;

    private String cuerpo(String sectorId, Double presion) throws Exception {
        return objectMapper.writeValueAsString(new IotPresionRequest("sensor-1", sectorId, presion, null));
    }

    @Test
    void debeRechazarSinHeaderDeClaveConRfc7807() throws Exception {
        mockMvc.perform(post("/api/iot/presion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("bocagrande", 10.0)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(401));

        verify(lecturas, never()).registrar(any(), any(), any(), any());
    }

    @Test
    void debeRechazarConClaveIncorrecta() throws Exception {
        mockMvc.perform(post("/api/iot/presion")
                        .header("X-IoT-Key", "otra-clave")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("bocagrande", 10.0)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));

        verify(lecturas, never()).registrar(any(), any(), any(), any());
    }

    @Test
    void debeResponder400ConRfc7807SiElCasoDeUsoRechazaElSector() throws Exception {
        willThrow(new IllegalArgumentException("No existe el sector 'no-existe'"))
                .given(lecturas).registrar(any(), eq(new SectorId("no-existe")), any(), any());

        mockMvc.perform(post("/api/iot/presion")
                        .header("X-IoT-Key", "clave-de-prueba")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("no-existe", 10.0)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.detail").value("No existe el sector 'no-existe'"));
    }

    @Test
    void debeResponder400ConRfc7807SiFaltaElSector() throws Exception {
        mockMvc.perform(post("/api/iot/presion")
                        .header("X-IoT-Key", "clave-de-prueba")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(null, 10.0)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));

        verify(lecturas, never()).registrar(any(), any(), any(), any());
    }

    @Test
    void debeResponder400SiLaCoordenadaEstaFueraDeRango() throws Exception {
        var conCoordenadaInvalida = new IotPresionRequest("sensor-1", "bocagrande", 10.0,
                new IotCoordenada(200.0, -75.5));

        mockMvc.perform(post("/api/iot/presion")
                        .header("X-IoT-Key", "clave-de-prueba")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(conCoordenadaInvalida)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void debeDelegarLaLecturaEnElCasoDeUsoYResponder200() throws Exception {
        mockMvc.perform(post("/api/iot/presion")
                        .header("X-IoT-Key", "clave-de-prueba")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("bocagrande", 12.0)))
                .andExpect(status().isOk());

        verify(lecturas).registrar(eq("sensor-1"), eq(new SectorId("bocagrande")), eq(12.0), any());
    }
}
