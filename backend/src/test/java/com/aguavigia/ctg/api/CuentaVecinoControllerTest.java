package com.aguavigia.ctg.api;

import com.aguavigia.ctg.api.error.ManejadorGlobalDeErrores;
import com.aguavigia.ctg.domain.ConsentimientosAceptados;
import com.aguavigia.ctg.domain.CorreoElectronico;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.port.in.RegistrarVecinoUseCase;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CuentaVecinoController.class)
@Import({ManejadorGlobalDeErrores.class, SecurityConfig.class})
@TestPropertySource(properties = "aguavigia.rate-limit.reglas=")
class CuentaVecinoControllerTest {

    private static final String RUTA = "/api/cuentas/vecino";

    private static final String VALIDO = """
            {
                "correo": "vecina@ejemplo.com",
                "nombre": "Vecina de Manga",
                "barrioId": "manga",
                "consentimiento": {"privacidad": true, "avisos": true}
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegistrarVecinoUseCase registrar;

    // SecurityConfig los necesita aunque esta ruta sea pública (ver CuentaPublicaControllerTest).
    @MockitoBean
    private JwtProvider jwtProvider;

    @MockitoBean
    private RevocacionSesionPort revocacion;

    @MockitoBean(name = "redisTemplate")
    private RedisTemplate<String, String> redisTemplateMock;

    @Test
    void registrarseSinSesionDebeResponder202SinCuerpo() throws Exception {
        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(VALIDO))
                .andExpect(status().isAccepted())
                .andExpect(content().string(""));
    }

    @Test
    void debePasarAlCasoDeUsoElBarrioYLasDosCasillas() throws Exception {
        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(VALIDO))
                .andExpect(status().isAccepted());

        verify(registrar).registrar(eq(new CorreoElectronico("vecina@ejemplo.com")), eq("Vecina de Manga"),
                eq(new SectorId("manga")), eq(new ConsentimientosAceptados(true, true)), any());
    }

    @Test
    void laCasillaDeAvisosAusenteDebeTomarseComoNoAceptada() throws Exception {
        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content("""
                        {"correo": "vecina@ejemplo.com", "nombre": "Vecina",
                         "barrioId": "manga", "consentimiento": {"privacidad": true}}"""))
                .andExpect(status().isAccepted());

        verify(registrar).registrar(any(), any(), any(), eq(new ConsentimientosAceptados(true, false)), any());
    }

    @Test
    void sinBarrioDebeResponder400() throws Exception {
        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content("""
                        {"correo": "vecina@ejemplo.com", "nombre": "Vecina",
                         "consentimiento": {"privacidad": true, "avisos": false}}"""))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(registrar);
    }

    @Test
    void sinElBloqueDeConsentimientoDebeResponder400() throws Exception {
        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content("""
                        {"correo": "vecina@ejemplo.com", "nombre": "Vecina",
                         "barrioId": "manga"}"""))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(registrar);
    }

    /**
     * La clave no viaja en el registro: se elige desde el enlace del correo. Si un cliente viejo la sigue mandando
     * se ignora, sin llegar al caso de uso.
     */
    @Test
    void unaClaveEnElCuerpoDebeIgnorarseSinAfectarElRegistro() throws Exception {
        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content("""
                        {"correo": "vecina@ejemplo.com", "nombre": "Vecina", "clave": "ClaveSegura123#!",
                         "barrioId": "manga", "consentimiento": {"privacidad": true}}"""))
                .andExpect(status().isAccepted());

        verify(registrar).registrar(any(), any(), any(), any(), any());
    }

    @Test
    void unCorreoMalFormadoDebeResponder400() throws Exception {
        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content("""
                        {"correo": "no-es-un-correo", "nombre": "Vecina",
                         "barrioId": "manga", "consentimiento": {"privacidad": true}}"""))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(registrar);
    }

    @Test
    void siElCasoDeUsoRechazaElRegistroDebeResponder400() throws Exception {
        willThrow(new IllegalArgumentException("Para registrarte debes aceptar el aviso de privacidad"))
                .given(registrar).registrar(any(), any(), any(), any(), any());

        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(VALIDO))
                .andExpect(status().isBadRequest());
    }
}
