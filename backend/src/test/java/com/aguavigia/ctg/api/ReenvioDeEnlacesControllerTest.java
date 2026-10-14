package com.aguavigia.ctg.api;

import com.aguavigia.ctg.api.error.ManejadorGlobalDeErrores;
import com.aguavigia.ctg.domain.CorreoElectronico;
import com.aguavigia.ctg.domain.EntidadNoEncontradaException;
import com.aguavigia.ctg.domain.Permiso;
import com.aguavigia.ctg.domain.UsuarioId;
import com.aguavigia.ctg.domain.port.in.ReenviarEnlaceDeCuentaUseCase;
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

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReenvioDeEnlacesController.class)
@Import({ManejadorGlobalDeErrores.class, SecurityConfig.class})
@TestPropertySource(properties = "aguavigia.rate-limit.reglas=")
class ReenvioDeEnlacesControllerTest {

    private static final String TOKEN = "token-de-prueba";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReenviarEnlaceDeCuentaUseCase reenviar;

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
    void reenviarLaVerificacionEsPublicoYSiempreResponde202() throws Exception {
        mockMvc.perform(post("/api/cuentas/verificacion/reenvio").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"ana@ejemplo.org\"}"))
                .andExpect(status().isAccepted());

        verify(reenviar).reenviarVerificacion(eq(new CorreoElectronico("ana@ejemplo.org")), any());
    }

    @Test
    void unCorreoMalFormadoDebeResponder400SinLlamarAlCasoDeUso() throws Exception {
        mockMvc.perform(post("/api/cuentas/verificacion/reenvio").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"no-es-un-correo\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(reenviar);
    }

    @Test
    void reenviarLaInvitacionExigeSesion() throws Exception {
        mockMvc.perform(post("/api/veedor/usuarios/u-2/invitacion/reenvio"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(reenviar);
    }

    @Test
    void reenviarLaInvitacionSinElPermisoDeGestionarUsuariosDebeSer403() throws Exception {
        autenticadoCon(Permiso.VER_PANEL);

        mockMvc.perform(post("/api/veedor/usuarios/u-2/invitacion/reenvio").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isForbidden());

        verifyNoInteractions(reenviar);
    }

    @Test
    void debeReenviarLaInvitacionCuandoElPermisoLoAutoriza() throws Exception {
        autenticadoCon(Permiso.GESTIONAR_USUARIOS);

        mockMvc.perform(post("/api/veedor/usuarios/u-2/invitacion/reenvio").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isAccepted());

        verify(reenviar).reenviarInvitacion(eq(new UsuarioId("u-2")), any());
    }

    @Test
    void unaCuentaInexistenteDebeResponder404() throws Exception {
        autenticadoCon(Permiso.GESTIONAR_USUARIOS);
        willThrow(new EntidadNoEncontradaException("No existe la cuenta 'u-9'"))
                .given(reenviar).reenviarInvitacion(any(), any());

        mockMvc.perform(post("/api/veedor/usuarios/u-9/invitacion/reenvio").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isNotFound());
    }

    @Test
    void unaCuentaQueYaAceptoLaInvitacionDebeResponder409() throws Exception {
        autenticadoCon(Permiso.GESTIONAR_USUARIOS);
        willThrow(new IllegalStateException("Solo se puede reenviar la invitación de una cuenta que aún no la aceptó"))
                .given(reenviar).reenviarInvitacion(any(), any());

        mockMvc.perform(post("/api/veedor/usuarios/u-1/invitacion/reenvio").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isConflict());
    }
}
