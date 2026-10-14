package com.aguavigia.ctg.api;

import com.aguavigia.ctg.api.error.ManejadorGlobalDeErrores;
import com.aguavigia.ctg.domain.ClaveEnClaro;
import com.aguavigia.ctg.domain.CuentaBloqueadaException;
import com.aguavigia.ctg.domain.Permiso;
import com.aguavigia.ctg.domain.UsuarioId;
import com.aguavigia.ctg.domain.port.in.CambiarClaveUseCase;
import com.aguavigia.ctg.domain.port.out.RevocacionSesionPort;
import com.aguavigia.ctg.infrastructure.config.SecurityConfig;
import com.aguavigia.ctg.infrastructure.security.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CuentaPropiaController.class)
@Import({ManejadorGlobalDeErrores.class, SecurityConfig.class})
@TestPropertySource(properties = "aguavigia.rate-limit.reglas=")
class CuentaPropiaControllerTest {

    private static final String TOKEN = "token-de-prueba";
    private static final String CUERPO = """
            {"claveActual":"la-clave-de-hoy-123","claveNueva":"la-clave-nueva-456"}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CambiarClaveUseCase cambiarClave;

    @MockitoBean
    private JwtProvider jwtProvider;

    @MockitoBean
    private RevocacionSesionPort revocacion;

    @MockitoBean(name = "redisTemplate")
    private RedisTemplate<String, String> redisTemplateMock;

    @BeforeEach
    void sesion() {
        given(revocacion.revocadasAntesDe(any())).willReturn(Optional.empty());
    }

    private void autenticadoCon(Permiso... permisos) {
        given(jwtProvider.validar(TOKEN)).willReturn(Optional.of(AutenticacionDePrueba.sesionCon(permisos)));
    }

    @Test
    void sinTokenDebeResponder401() throws Exception {
        mockMvc.perform(post("/api/veedor/cuenta/clave").contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(cambiarClave);
    }

    @Test
    void unaSesionRestringidaAlAltaDelSegundoFactorNoDebePoderCambiarLaClave() throws Exception {
        autenticadoCon(Permiso.CONFIGURAR_SEGUNDO_FACTOR);

        mockMvc.perform(post("/api/veedor/cuenta/clave").header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isForbidden());

        verifyNoInteractions(cambiarClave);
    }

    @Test
    void debeCambiarLaClaveYResponder204() throws Exception {
        autenticadoCon(Permiso.VER_PANEL);

        mockMvc.perform(post("/api/veedor/cuenta/clave").header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isNoContent());

        verify(cambiarClave).cambiar(eq(new UsuarioId(AutenticacionDePrueba.USUARIO_ID)), eq("la-clave-de-hoy-123"),
                eq(new ClaveEnClaro("la-clave-nueva-456")), any());
    }

    @Test
    void unaClaveNuevaCortaDebeResponder400ConElMotivo() throws Exception {
        autenticadoCon(Permiso.VER_PANEL);

        mockMvc.perform(post("/api/veedor/cuenta/clave").header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"claveActual\":\"la-clave-de-hoy-123\",\"claveNueva\":\"corta\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(cambiarClave);
    }

    @Test
    void faltandoLaClaveActualDebeResponder400() throws Exception {
        autenticadoCon(Permiso.VER_PANEL);

        mockMvc.perform(post("/api/veedor/cuenta/clave").header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"claveNueva\":\"la-clave-nueva-456\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores").exists());
    }

    @Test
    void unaClaveActualIncorrectaDebeResponder400ConProblemaRfc7807() throws Exception {
        autenticadoCon(Permiso.VER_PANEL);
        willThrow(new IllegalArgumentException("La clave actual no es correcta."))
                .given(cambiarClave).cambiar(any(), any(), any(), any());

        mockMvc.perform(post("/api/veedor/cuenta/clave").header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("La clave actual no es correcta."));
    }

    @Test
    void unaCuentaBloqueadaDebeResponder423ConLosSegundosRestantes() throws Exception {
        autenticadoCon(Permiso.VER_PANEL);
        willThrow(new CuentaBloqueadaException(Duration.ofMinutes(9), "Demasiados intentos fallidos."))
                .given(cambiarClave).cambiar(any(), any(), any(), any());

        mockMvc.perform(post("/api/veedor/cuenta/clave").header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.segundosRestantes").value(540));
    }
}
