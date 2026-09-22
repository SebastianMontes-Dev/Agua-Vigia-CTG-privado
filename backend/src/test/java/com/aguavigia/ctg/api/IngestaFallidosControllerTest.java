package com.aguavigia.ctg.api;

import com.aguavigia.ctg.api.error.ManejadorGlobalDeErrores;
import com.aguavigia.ctg.domain.Permiso;
import com.aguavigia.ctg.infrastructure.config.SecurityConfig;
import com.aguavigia.ctg.infrastructure.persistence.mongo.DocumentoFallidoDocumento;
import com.aguavigia.ctg.infrastructure.persistence.mongo.DocumentoFallidoMongoRepository;
import com.aguavigia.ctg.infrastructure.security.JwtProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IngestaFallidosController.class)
@Import({ManejadorGlobalDeErrores.class, SecurityConfig.class})
@TestPropertySource(properties = "aguavigia.rate-limit.reglas=")
class IngestaFallidosControllerTest {

    private static final String TOKEN = "Bearer token-de-veedor";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocumentoFallidoMongoRepository fallidos;

    @MockitoBean
    private JwtProvider jwtProvider;

    @MockitoBean
    private com.aguavigia.ctg.domain.port.out.RevocacionSesionPort revocacion;

    @MockitoBean(name = "redisTemplate")
    private RedisTemplate<String, String> redisTemplateMock;

    /** Qué falla y por qué es información operativa, no pública. */
    @Test
    void debeExigirTokenDeVeedor() throws Exception {
        mockMvc.perform(get("/api/veedor/ingesta/fallidos"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void debeExponerLosDocumentosFallidosAlVeedor() throws Exception {
        given(jwtProvider.validar("token-de-veedor"))
                .willReturn(Optional.of(AutenticacionDePrueba.sesionCon(Permiso.VER_PANEL)));
        Instant ahora = Instant.parse("2026-08-09T15:30:00Z");
        given(fallidos.findTop200ByOrderByUltimoIntentoDesc()).willReturn(List.of(
                new DocumentoFallidoDocumento("hash-1", "acuacar", "https://acuacar.com/x", "Titulo",
                        "java.lang.RuntimeException: Mongo caído", ahora, ahora, 3)));

        mockMvc.perform(get("/api/veedor/ingesta/fallidos").header("Authorization", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fuente").value("acuacar"))
                .andExpect(jsonPath("$[0].motivo").value("java.lang.RuntimeException: Mongo caído"))
                .andExpect(jsonPath("$[0].reintentos").value(3))
                .andExpect(jsonPath("$[0].ultimoIntento").value("2026-08-09T15:30:00Z"));
    }
}
