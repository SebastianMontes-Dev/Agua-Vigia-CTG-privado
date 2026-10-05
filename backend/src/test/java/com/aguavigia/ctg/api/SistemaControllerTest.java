package com.aguavigia.ctg.api;

import com.aguavigia.ctg.api.error.ManejadorGlobalDeErrores;
import com.aguavigia.ctg.domain.ModoDelSistema;
import com.aguavigia.ctg.domain.port.in.ConsultarModoDelSistemaUseCase;
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

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SistemaController.class)
@Import({ManejadorGlobalDeErrores.class, SecurityConfig.class})
@TestPropertySource(properties = "aguavigia.rate-limit.reglas=")
class SistemaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConsultarModoDelSistemaUseCase consultar;

    @MockitoBean
    private JwtProvider jwtProvider;

    @MockitoBean
    private RevocacionSesionPort revocacion;

    @MockitoBean(name = "redisTemplate")
    private RedisTemplate<String, String> redisTemplateMock;

    /** Sin sesión: el banner de «simulación» y la nota de cuentas sintéticas los pinta cualquier visitante. */
    @Test
    void esPublicoYDeclaraElModoYLasCuentasSinteticas() throws Exception {
        given(consultar.consultar()).willReturn(new ModoDelSistema(ModoDelSistema.Modo.REAL, 30_000));

        mockMvc.perform(get("/api/sistema/modo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modo").value("REAL"))
                .andExpect(jsonPath("$.cuentasSinteticas").value(30_000));
    }

    @Test
    void enLaSimulacionLoDiceAsi() throws Exception {
        given(consultar.consultar()).willReturn(new ModoDelSistema(ModoDelSistema.Modo.SIMULACION, 0));

        mockMvc.perform(get("/api/sistema/modo"))
                .andExpect(jsonPath("$.modo").value("SIMULACION"))
                .andExpect(jsonPath("$.cuentasSinteticas").value(0));
    }

    @Test
    void soloSeLee() throws Exception {
        mockMvc.perform(post("/api/sistema/modo")).andExpect(status().is4xxClientError());
    }
}
