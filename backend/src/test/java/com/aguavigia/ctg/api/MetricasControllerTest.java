package com.aguavigia.ctg.api;

import com.aguavigia.ctg.domain.MetricasDelSistema;
import com.aguavigia.ctg.domain.Permiso;
import com.aguavigia.ctg.domain.port.in.ConsultarMetricasDelSistemaUseCase;
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

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MetricasController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "aguavigia.rate-limit.reglas=")
class MetricasControllerTest {

    private static final String TOKEN = "Bearer token-de-veedor";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConsultarMetricasDelSistemaUseCase metricas;

    @MockitoBean
    private JwtProvider jwtProvider;

    @MockitoBean
    private com.aguavigia.ctg.domain.port.out.RevocacionSesionPort revocacion;

    @MockitoBean(name = "redisTemplate")
    private RedisTemplate<String, String> redisTemplateMock;

    private static MetricasDelSistema unaInstantanea() {
        return new MetricasDelSistema(Instant.parse("2026-10-06T15:00:00Z"),
                Map.of("SIN_SERVICIO/VECINOS", 4L), 2,
                Map.of("SIN_AGUA", 7L), Map.of("NINGUNA", 120L, "CUENTA_VERIFICADA", 30L), Map.of("acuacar", 1L),
                new MetricasDelSistema.TiempoHastaElCambio(4, 95, 240));
    }

    @Test
    void sinTokenResponde401() throws Exception {
        mockMvc.perform(get("/api/veedor/sistema/metricas")).andExpect(status().isUnauthorized());
    }

    /** Las métricas dicen cuántos reportes anónimos llegan y qué colectores fallan: no son públicas ni de cualquier cuenta. */
    @Test
    void unaSesionSinVerPanelResponde403() throws Exception {
        given(jwtProvider.validar("token-de-veedor"))
                .willReturn(Optional.of(AutenticacionDePrueba.sesionCon(Permiso.MODERAR_REPORTES)));

        mockMvc.perform(get("/api/veedor/sistema/metricas").header("Authorization", TOKEN)).andExpect(status().isForbidden());
    }

    @Test
    void conVerPanelDevuelveLasMetricasConSusNombresDeCampo() throws Exception {
        given(jwtProvider.validar("token-de-veedor"))
                .willReturn(Optional.of(AutenticacionDePrueba.sesionCon(Permiso.VER_PANEL)));
        given(metricas.consultar()).willReturn(unaInstantanea());

        mockMvc.perform(get("/api/veedor/sistema/metricas").header("Authorization", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.desde").value("2026-10-06T15:00:00Z"))
                .andExpect(jsonPath("$.cambiosDeEstado['SIN_SERVICIO/VECINOS']").value(4))
                .andExpect(jsonPath("$.disputasAbiertas").value(2))
                .andExpect(jsonPath("$.quorumsRechazadosPorComposicion.SIN_AGUA").value(7))
                .andExpect(jsonPath("$.reportesPorNivelDeVerificacion.NINGUNA").value(120))
                .andExpect(jsonPath("$.reportesPorNivelDeVerificacion.CUENTA_VERIFICADA").value(30))
                .andExpect(jsonPath("$.fallosDeColectores.acuacar").value(1))
                .andExpect(jsonPath("$.tiempoHastaElCambioDeEstado.cambios").value(4))
                .andExpect(jsonPath("$.tiempoHastaElCambioDeEstado.promedioSegundos").value(95))
                .andExpect(jsonPath("$.tiempoHastaElCambioDeEstado.maximoSegundos").value(240));
    }
}
