package com.aguavigia.ctg.api;

import com.aguavigia.ctg.api.error.ManejadorGlobalDeErrores;
import com.aguavigia.ctg.domain.port.in.EmitirTokenDeDispositivoUseCase;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DispositivoController.class)
@Import({ManejadorGlobalDeErrores.class, SecurityConfig.class})
@TestPropertySource(properties = "aguavigia.rate-limit.reglas=")
class DispositivoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmitirTokenDeDispositivoUseCase emitir;

    @MockitoBean
    private JwtProvider jwtProvider;

    @MockitoBean
    private RevocacionSesionPort revocacion;

    @MockitoBean(name = "redisTemplate")
    private RedisTemplate<String, String> redisTemplateMock;

    @Test
    void pedirUnTokenSinSesionDebeResponder201ConElToken() throws Exception {
        given(emitir.emitir()).willReturn("d-123.firma");

        mockMvc.perform(post("/api/dispositivos"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("d-123.firma"));
    }

    /**
     * El token es una credencial: ni el navegador ni un proxy deben guardarla en caché. Hoy lo
     * garantiza Spring Security para toda respuesta; esta prueba avisa si alguien lo desactiva.
     */
    @Test
    void laRespuestaNoDebePoderGuardarseEnCache() throws Exception {
        given(emitir.emitir()).willReturn("d-123.firma");

        mockMvc.perform(post("/api/dispositivos"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
    }

    @Test
    void soloDebeAceptarPost() throws Exception {
        mockMvc.perform(get("/api/dispositivos")).andExpect(status().isMethodNotAllowed());
    }
}
