package com.aguavigia.ctg.api;

import com.aguavigia.ctg.api.error.ManejadorGlobalDeErrores;
import com.aguavigia.ctg.api.mapper.ReporteApiMapperImpl;
import com.aguavigia.ctg.domain.EnlaceDeRestablecimientoInvalidoException;
import com.aguavigia.ctg.domain.HuellaDispositivo;
import com.aguavigia.ctg.domain.ReporteCiudadano;
import com.aguavigia.ctg.domain.ReporteId;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.SuscripcionId;
import com.aguavigia.ctg.domain.TipoReporte;
import com.aguavigia.ctg.domain.LimiteReportesExcedidoException;
import com.aguavigia.ctg.domain.port.in.ConfirmarRestablecimientoPorEnlaceUseCase;
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

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RestablecimientoController.class)
@Import({ReporteApiMapperImpl.class, ManejadorGlobalDeErrores.class, SecurityConfig.class})
@TestPropertySource(properties = "aguavigia.rate-limit.reglas=")
class RestablecimientoControllerTest {

    private static final SectorId MANGA = new SectorId("manga");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConfirmarRestablecimientoPorEnlaceUseCase confirmar;

    @MockitoBean
    private JwtProvider jwtProvider;

    @MockitoBean
    private RevocacionSesionPort revocacion;

    @MockitoBean(name = "redisTemplate")
    private RedisTemplate<String, String> redisTemplateMock;

    private static ReporteCiudadano reporte() {
        return new ReporteCiudadano(new ReporteId("r-1"), MANGA, TipoReporte.SERVICIO_RESTABLECIDO, null,
                HuellaDispositivo.deSuscripcion(new SuscripcionId("s-1")), Instant.parse("2026-08-21T20:00:00Z"));
    }

    /** Sin sesión y sin cabecera de dispositivo: lo que identifica a quien toca es el token del enlace. */
    @Test
    void unToqueConUnEnlaceValidoDebeRegistrarElRestablecimiento() throws Exception {
        given(confirmar.confirmar(eq(MANGA), eq("token-firmado"), any())).willReturn(reporte());

        mockMvc.perform(post("/api/sectores/manga/restablecimiento").param("token", "token-firmado"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sectorId").value("manga"))
                .andExpect(jsonPath("$.tipo").value("SERVICIO_RESTABLECIDO"))
                .andExpect(jsonPath("$.subidaToken").doesNotExist());

        verify(confirmar).confirmar(eq(MANGA), eq("token-firmado"), any());
    }

    @Test
    void unEnlaceQueNoSirveDebeResponder403ConSuTipo() throws Exception {
        given(confirmar.confirmar(any(), any(), any()))
                .willThrow(new EnlaceDeRestablecimientoInvalidoException("Este enlace ya no sirve."));

        mockMvc.perform(post("/api/sectores/manga/restablecimiento").param("token", "viejo"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("https://aguavigia.example/errores/enlace-invalido"));
    }

    @Test
    void sinTokenDebeResponder400() throws Exception {
        mockMvc.perform(post("/api/sectores/manga/restablecimiento"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void elCupoDeReportesTambienAplicaAlToque() throws Exception {
        given(confirmar.confirmar(any(), any(), any()))
                .willThrow(new LimiteReportesExcedidoException("Ya reportaste 3 veces en 'manga'"));

        mockMvc.perform(post("/api/sectores/manga/restablecimiento").param("token", "t"))
                .andExpect(status().isTooManyRequests());
    }

    /** Abrir el enlace desde el correo es un GET: no debe votar. Para eso hace falta el POST, a propósito. */
    @Test
    void abrirElEnlaceConGetNoRegistraNada() throws Exception {
        mockMvc.perform(get("/api/sectores/manga/restablecimiento").param("token", "t"))
                .andExpect(status().isMethodNotAllowed());
    }
}
