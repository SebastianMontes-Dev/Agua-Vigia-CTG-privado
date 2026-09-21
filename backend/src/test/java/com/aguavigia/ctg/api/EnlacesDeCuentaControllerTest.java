package com.aguavigia.ctg.api;

import com.aguavigia.ctg.api.error.ManejadorGlobalDeErrores;
import com.aguavigia.ctg.domain.ClaveEnClaro;
import com.aguavigia.ctg.domain.ContextoDeAccion;
import com.aguavigia.ctg.domain.port.in.AceptarInvitacionUseCase;
import com.aguavigia.ctg.domain.port.in.RestablecerClaveUseCase;
import com.aguavigia.ctg.domain.port.in.VerificarCorreoUseCase;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EnlacesDeCuentaController.class)
@Import({ManejadorGlobalDeErrores.class, SecurityConfig.class})
@TestPropertySource(properties = "aguavigia.rate-limit.reglas=")
class EnlacesDeCuentaControllerTest {

    private static final String TOKEN = "tok-123_abc";
    private static final String CLAVE_VALIDA = "ClaveSegura123#!";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VerificarCorreoUseCase verificar;

    @MockitoBean
    private AceptarInvitacionUseCase aceptarInvitacion;

    @MockitoBean
    private RestablecerClaveUseCase restablecer;

    // SecurityConfig los necesita aunque estas rutas sean públicas (ver CuentaPublicaControllerTest).
    @MockitoBean
    private JwtProvider jwtProvider;

    @MockitoBean
    private RevocacionSesionPort revocacion;

    @MockitoBean(name = "redisTemplate")
    private RedisTemplate<String, String> redisTemplateMock;

    @Test
    void abrirElEnlaceDeVerificacionDebeMostrarElFormularioSinConsumirElToken() throws Exception {
        mockMvc.perform(get("/api/cuentas/enlaces/verificar").param("token", TOKEN))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "action=\"/api/cuentas/enlaces/verificar\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "name=\"token\" value=\"" + TOKEN + "\"")));

        // Los antivirus de correo precargan los enlaces: abrirlo no puede gastar un token de un solo uso.
        verifyNoInteractions(verificar);
    }

    @Test
    void enviarElFormularioDeVerificacionDebeConfirmarElCorreo() throws Exception {
        mockMvc.perform(post("/api/cuentas/enlaces/verificar")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("token", TOKEN))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Correo confirmado")));

        verify(verificar).verificar(eq(TOKEN), any(ContextoDeAccion.class));
    }

    @Test
    void unTokenDeVerificacionInvalidoDebeResponder400ConUnaPaginaClara() throws Exception {
        willThrow(new IllegalArgumentException("El enlace no es válido o ya venció"))
                .given(verificar).verificar(eq("malo"), any(ContextoDeAccion.class));

        mockMvc.perform(post("/api/cuentas/enlaces/verificar")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("token", "malo"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "El enlace no es válido o ya venció")));
    }

    @Test
    void abrirElEnlaceDeInvitacionDebeMostrarElCampoDeClaveSinAceptarNada() throws Exception {
        mockMvc.perform(get("/api/cuentas/enlaces/invitacion").param("token", TOKEN))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("type=\"password\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "action=\"/api/cuentas/enlaces/invitacion\"")));

        verifyNoInteractions(aceptarInvitacion);
    }

    @Test
    void enviarLaInvitacionConUnaClaveValidaDebeActivarLaCuenta() throws Exception {
        mockMvc.perform(post("/api/cuentas/enlaces/invitacion")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("token", TOKEN)
                        .param("clave", CLAVE_VALIDA))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Cuenta activada")));

        verify(aceptarInvitacion).aceptar(eq(TOKEN), eq(new ClaveEnClaro(CLAVE_VALIDA)), any(ContextoDeAccion.class));
    }

    @Test
    void unaClaveDebilEnLaInvitacionDebeVolverAMostrarElFormularioConElMotivo() throws Exception {
        mockMvc.perform(post("/api/cuentas/enlaces/invitacion")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("token", TOKEN)
                        .param("clave", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("type=\"password\"")))
                // El token viaja de nuevo para que reintentar no obligue a pedir otro correo.
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "name=\"token\" value=\"" + TOKEN + "\"")));

        verifyNoInteractions(aceptarInvitacion);
    }

    @Test
    void abrirElEnlaceDeRestablecimientoDebeMostrarElFormularioSinCambiarLaClave() throws Exception {
        mockMvc.perform(get("/api/cuentas/enlaces/restablecer").param("token", TOKEN))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "action=\"/api/cuentas/enlaces/restablecer\"")));

        verifyNoInteractions(restablecer);
    }

    @Test
    void enviarElRestablecimientoConUnaClaveValidaDebeCambiarLaClave() throws Exception {
        mockMvc.perform(post("/api/cuentas/enlaces/restablecer")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("token", TOKEN)
                        .param("clave", CLAVE_VALIDA))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Clave cambiada")));

        verify(restablecer).restablecer(eq(TOKEN), eq(new ClaveEnClaro(CLAVE_VALIDA)), any(ContextoDeAccion.class));
    }

    @Test
    void unTokenDeRestablecimientoVencidoDebeResponder400() throws Exception {
        willThrow(new IllegalArgumentException("El enlace no es válido o ya venció"))
                .given(restablecer).restablecer(eq("viejo"), any(ClaveEnClaro.class), any(ContextoDeAccion.class));

        mockMvc.perform(post("/api/cuentas/enlaces/restablecer")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("token", "viejo")
                        .param("clave", CLAVE_VALIDA))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "El enlace no es válido o ya venció")));
    }

    @Test
    void unTokenConHtmlNoDebeInyectarseSinEscaparEnLaPagina() throws Exception {
        String malicioso = "\"><script>alert(1)</script>";

        mockMvc.perform(get("/api/cuentas/enlaces/restablecer").param("token", malicioso))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("<script>alert(1)"))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("&lt;script&gt;")));
    }

    @Test
    void elMensajeDeUnaExcepcionNoDebeInyectarseSinEscaparEnLaPagina() throws Exception {
        willThrow(new IllegalArgumentException("<img src=x onerror=alert(1)>"))
                .given(verificar).verificar(eq("t"), any(ContextoDeAccion.class));

        mockMvc.perform(post("/api/cuentas/enlaces/verificar")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("token", "t"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("<img src=x"))));
    }
}
