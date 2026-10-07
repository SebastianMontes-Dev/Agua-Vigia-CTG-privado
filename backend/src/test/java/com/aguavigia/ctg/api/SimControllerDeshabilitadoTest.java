package com.aguavigia.ctg.api;

import com.aguavigia.ctg.api.error.ManejadorGlobalDeErrores;
import com.aguavigia.ctg.domain.port.out.RevocacionSesionPort;
import com.aguavigia.ctg.infrastructure.config.SecurityConfig;
import com.aguavigia.ctg.infrastructure.security.JwtProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** En la instancia real (sin aguavigia.sim.habilitada) las rutas de simulación no existen: 404, ni siquiera con clave. */
@WebMvcTest(SimController.class)
@Import({ManejadorGlobalDeErrores.class, SecurityConfig.class})
@TestPropertySource(properties = {"aguavigia.rate-limit.reglas=", "aguavigia.sim.clave=una-clave-puesta-por-error"})
class SimControllerDeshabilitadoTest {

    @MockitoBean
    private RevocacionSesionPort revocacion;
    @MockitoBean
    private JwtProvider jwtProvider;
    @MockitoBean(name = "redisTemplate")
    private RedisTemplate<String, String> redisTemplateMock;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void enLaInstanciaRealLasRutasDeSimulacionDanNotFound() throws Exception {
        mockMvc.perform(get("/api/sim/reloj").header("X-Sim-Key", "una-clave-puesta-por-error")).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/sim/sesion-admin").header("X-Sim-Key", "una-clave-puesta-por-error")).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/sim/boletines").header("X-Sim-Key", "x")).andExpect(status().isNotFound());
    }
}
