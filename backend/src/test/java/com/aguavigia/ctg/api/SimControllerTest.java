package com.aguavigia.ctg.api;

import com.aguavigia.ctg.api.error.ManejadorGlobalDeErrores;
import com.aguavigia.ctg.domain.AlcanceSesion;
import com.aguavigia.ctg.domain.BoletinSimulado;
import com.aguavigia.ctg.domain.CorreoElectronico;
import com.aguavigia.ctg.domain.EstadoDelReloj;
import com.aguavigia.ctg.domain.Permiso;
import com.aguavigia.ctg.domain.RolVeedor;
import com.aguavigia.ctg.domain.SesionEmitida;
import com.aguavigia.ctg.domain.UsuarioId;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SimController.class)
@Import({ManejadorGlobalDeErrores.class, SecurityConfig.class, GuardiaDeSimulacion.class})
@TestPropertySource(properties = {
        "aguavigia.rate-limit.reglas=",
        "aguavigia.sim.habilitada=true",
        "aguavigia.sim.clave=clave-de-simulacion-de-prueba-0123"
})
class SimControllerTest {

    private static final String CLAVE = "clave-de-simulacion-de-prueba-0123";
    private static final Instant AHORA = Instant.parse("2026-11-02T13:00:00Z");

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

    // --- la clave ---

    @Test
    void sinCabeceraDeClaveResponde401ConRfc7807() throws Exception {
        mockMvc.perform(get("/api/sim/reloj"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        verify(reloj, never()).consultar();
    }

    @Test
    void conClaveIncorrectaResponde401() throws Exception {
        mockMvc.perform(get("/api/sim/reloj").header("X-Sim-Key", "otra"))
                .andExpect(status().isUnauthorized());
        verify(reloj, never()).consultar();
    }

    @Test
    void ningunaRutaDeSimulacionSeAtiendeSinLaClave() throws Exception {
        mockMvc.perform(post("/api/sim/reloj").contentType(MediaType.APPLICATION_JSON).content("{\"avanzarSegundos\":60}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/sim/boletines").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titulo\":\"t\",\"contenido\":\"<p>c</p>\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/sim/sesion-admin")).andExpect(status().isUnauthorized());
        verify(reloj, never()).avanzar(any());
        verify(boletines, never()).inyectar(any());
        verify(sesionAdmin, never()).iniciar(any());
    }

    // --- el reloj ---

    @Test
    void consultarElRelojDevuelveLaHoraYElDesfase() throws Exception {
        given(reloj.consultar()).willReturn(new EstadoDelReloj(AHORA, Duration.ofHours(2)));

        mockMvc.perform(get("/api/sim/reloj").header("X-Sim-Key", CLAVE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ahora").value("2026-11-02T13:00:00Z"))
                .andExpect(jsonPath("$.desfaseSegundos").value(7200));
    }

    @Test
    void fijarElRelojEnUnInstante() throws Exception {
        given(reloj.fijarEn(any())).willReturn(new EstadoDelReloj(AHORA, Duration.ZERO));

        mockMvc.perform(post("/api/sim/reloj").header("X-Sim-Key", CLAVE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"instante\":\"2026-11-02T13:00:00Z\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ahora").value("2026-11-02T13:00:00Z"));
        verify(reloj).fijarEn(AHORA);
    }

    @Test
    void avanzarElRelojPorSegundos() throws Exception {
        given(reloj.avanzar(any())).willReturn(new EstadoDelReloj(AHORA, Duration.ofHours(1)));

        mockMvc.perform(post("/api/sim/reloj").header("X-Sim-Key", CLAVE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"avanzarSegundos\":3600}"))
                .andExpect(status().isOk());
        verify(reloj).avanzar(Duration.ofSeconds(3600));
    }

    @Test
    void pedirInstanteYAvanceALaVezOninguno400() throws Exception {
        mockMvc.perform(post("/api/sim/reloj").header("X-Sim-Key", CLAVE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"instante\":\"2026-11-02T13:00:00Z\",\"avanzarSegundos\":60}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/sim/reloj").header("X-Sim-Key", CLAVE).contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        verify(reloj, never()).fijarEn(any());
        verify(reloj, never()).avanzar(any());
    }

    // --- los boletines ---

    @Test
    void inyectarUnBoletinLoPasaAlCasoDeUsoYDevuelveSuFechaResuelta() throws Exception {
        given(boletines.inyectar(any())).willAnswer(i -> ((BoletinSimulado) i.getArgument(0)).conFecha(AHORA));

        mockMvc.perform(post("/api/sim/boletines").header("X-Sim-Key", CLAVE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":7,\"titulo\":\"[SIMULACIÓN] Corte\",\"contenido\":\"<p>Manga</p>\","
                                + "\"enlace\":\"https://simulacion.local/boletin/7\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.fecha").value("2026-11-02T13:00:00Z"));
        verify(boletines).inyectar(new BoletinSimulado(7, null, "https://simulacion.local/boletin/7",
                "[SIMULACIÓN] Corte", "<p>Manga</p>", null));
    }

    /** El formato de WordPress trae la fecha como hora local de Cartagena, sin zona. */
    @Test
    void laFechaSinZonaSeEntiendeComoHoraDeCartagena() throws Exception {
        given(boletines.inyectar(any())).willAnswer(i -> i.getArgument(0));

        mockMvc.perform(post("/api/sim/boletines").header("X-Sim-Key", CLAVE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fecha\":\"2026-11-02T08:20:00\",\"titulo\":\"t\",\"contenido\":\"<p>c</p>\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fecha").value("2026-11-02T13:20:00Z"));
    }

    @Test
    void unBoletinSinTituloOSinContenidoEs400() throws Exception {
        mockMvc.perform(post("/api/sim/boletines").header("X-Sim-Key", CLAVE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contenido\":\"<p>c</p>\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/sim/boletines").header("X-Sim-Key", CLAVE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titulo\":\"t\"}"))
                .andExpect(status().isBadRequest());
        verify(boletines, never()).inyectar(any());
    }

    @Test
    void unaFechaIlegibleEs400() throws Exception {
        mockMvc.perform(post("/api/sim/boletines").header("X-Sim-Key", CLAVE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fecha\":\"ayer\",\"titulo\":\"t\",\"contenido\":\"<p>c</p>\"}"))
                .andExpect(status().isBadRequest());
        verify(boletines, never()).inyectar(any());
    }

    // --- el reinicio ---

    @Test
    void elReinicioExigeLaClaveYLlamaAlCasoDeUso() throws Exception {
        mockMvc.perform(post("/api/sim/reinicio")).andExpect(status().isUnauthorized());
        verify(reinicio, never()).reiniciar();

        mockMvc.perform(post("/api/sim/reinicio").header("X-Sim-Key", CLAVE)).andExpect(status().isNoContent());
        verify(reinicio).reiniciar();
    }

    // --- la sesión de admin ---

    @Test
    void laSesionDeAdminDevuelveLaSesionDelPanel() throws Exception {
        given(sesionAdmin.iniciar(any())).willReturn(new SesionEmitida("token-de-admin", new UsuarioId("a-1"), "Admin",
                new CorreoElectronico("admin@aguavigia.local"), RolVeedor.ADMIN, Set.of(Permiso.VER_PANEL), AlcanceSesion.COMPLETO));

        mockMvc.perform(post("/api/sim/sesion-admin").header("X-Sim-Key", CLAVE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("token-de-admin"))
                .andExpect(jsonPath("$.rol").value("ADMIN"))
                .andExpect(jsonPath("$.alcance").value("COMPLETO"));
        verify(sesionAdmin).iniciar(any());
    }
}
