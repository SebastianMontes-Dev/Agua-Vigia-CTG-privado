package com.aguavigia.ctg.api;

import com.aguavigia.ctg.api.error.ManejadorGlobalDeErrores;
import com.aguavigia.ctg.domain.port.in.ControlarRelojDeSimulacionUseCase;
import com.aguavigia.ctg.domain.port.in.InyectarBoletinSimuladoUseCase;
import com.aguavigia.ctg.domain.port.in.ReiniciarSimulacionUseCase;
import com.aguavigia.ctg.domain.port.in.IniciarSesionDeAdminDeSimulacionUseCase;
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

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Con la simulación habilitada pero sin SIMULACION_CLAVE la ruta no atiende a nadie: ni con una cabecera vacía que coincida. */
@WebMvcTest(SimController.class)
@Import({ManejadorGlobalDeErrores.class, SecurityConfig.class, GuardiaDeSimulacion.class})
@TestPropertySource(properties = {
        "aguavigia.rate-limit.reglas=",
        "aguavigia.sim.habilitada=true",
        "aguavigia.sim.clave="
})
class SimControllerSinClaveTest {

    @MockitoBean
    private RevocacionSesionPort revocacion;
    @MockitoBean
    private JwtProvider jwtProvider;
    @MockitoBean(name = "redisTemplate")
    private RedisTemplate<String, String> redisTemplateMock;
    @MockitoBean
    private ControlarRelojDeSimulacionUseCase reloj;
    @MockitoBean
    private InyectarBoletinSimuladoUseCase boletines;
    @MockitoBean
    private IniciarSesionDeAdminDeSimulacionUseCase sesionAdmin;
    @MockitoBean
    private ReiniciarSimulacionUseCase reinicio;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void sinClaveConfiguradaResponde503ConRfc7807() throws Exception {
        mockMvc.perform(get("/api/sim/reloj").header("X-Sim-Key", ""))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        verify(reloj, never()).consultar();
    }
}
