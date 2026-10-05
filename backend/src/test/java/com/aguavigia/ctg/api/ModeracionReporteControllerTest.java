package com.aguavigia.ctg.api;

import com.aguavigia.ctg.api.error.ManejadorGlobalDeErrores;
import com.aguavigia.ctg.api.mapper.ReporteModeracionApiMapperImpl;
import com.aguavigia.ctg.domain.EstadoModeracion;
import com.aguavigia.ctg.domain.HuellaDispositivo;
import com.aguavigia.ctg.domain.Pagina;
import com.aguavigia.ctg.domain.ReporteCiudadano;
import com.aguavigia.ctg.domain.ReporteId;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.TipoReporte;
import com.aguavigia.ctg.domain.port.in.DescartarFotoUseCase;
import com.aguavigia.ctg.domain.port.in.ModerarReporteUseCase;
import com.aguavigia.ctg.domain.RedEnRafaga;
import com.aguavigia.ctg.domain.ReportesPendientes;
import com.aguavigia.ctg.domain.port.in.ListarReportesPendientesUseCase;
import com.aguavigia.ctg.infrastructure.config.SecurityConfig;
import com.aguavigia.ctg.domain.Permiso;
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
import java.util.Set;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ModeracionReporteController.class)
@Import({ReporteModeracionApiMapperImpl.class, ManejadorGlobalDeErrores.class, SecurityConfig.class})
@TestPropertySource(properties = "aguavigia.rate-limit.reglas=")
class ModeracionReporteControllerTest {

    private static final Instant TIMESTAMP = Instant.parse("2026-08-09T20:00:00Z");
    private static final String TOKEN = "Bearer token-de-veedor";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ModerarReporteUseCase moderarReporte;

    @MockitoBean
    private DescartarFotoUseCase descartarFoto;

    @MockitoBean
    private ListarReportesPendientesUseCase listarPendientes;

    @MockitoBean
    private JwtProvider jwtProvider;

    @MockitoBean
    private com.aguavigia.ctg.domain.port.out.RevocacionSesionPort revocacion;

    // RateLimitConfig implementa WebMvcConfigurer y se instancia en cualquier @WebMvcTest aunque
    // no se importe (REC-006).
    @MockitoBean(name = "redisTemplate")
    private RedisTemplate<String, String> redisTemplateMock;

    private void autenticarComoVeedor() {
        given(jwtProvider.validar("token-de-veedor"))
                .willReturn(Optional.of(AutenticacionDePrueba.sesionCon(Permiso.VER_PANEL, Permiso.MODERAR_REPORTES)));
    }

    private ReporteCiudadano reporte(EstadoModeracion estado) {
        return new ReporteCiudadano(new ReporteId("r1"), new SectorId("manga"), TipoReporte.SIN_AGUA,
                null, new HuellaDispositivo("hash-1"), TIMESTAMP, estado);
    }

    @Test
    void debeRechazarUnaPeticionSinTokenCon401() throws Exception {
        mockMvc.perform(get("/api/veedor/reportes/pendientes"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void debeListarLosReportesPendientes() throws Exception {
        autenticarComoVeedor();
        given(listarPendientes.listar(anyInt(), anyInt())).willReturn(new ReportesPendientes(
                new Pagina<>(List.of(reporte(EstadoModeracion.PENDIENTE)), 0, 50, 1), Set.of()));

        mockMvc.perform(get("/api/veedor/reportes/pendientes").header("Authorization", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("r1"))
                .andExpect(jsonPath("$[0].estadoModeracion").value("PENDIENTE"));
    }

    /** D16: el veedor ve cuánto respalda el servidor a cada reporte, sin la red de origen (que no sale de la base). */
    @Test
    void laColaDebeMostrarElNivelDeVerificacionSinLaRed() throws Exception {
        autenticarComoVeedor();
        given(listarPendientes.listar(anyInt(), anyInt())).willReturn(new ReportesPendientes(new Pagina<>(List.of(
                reporte(EstadoModeracion.PENDIENTE)
                        .conIdentidad(com.aguavigia.ctg.domain.NivelDeVerificacion.CUENTA_VERIFICADA, "red-secreta")),
                0, 50, 1), Set.of()));

        mockMvc.perform(get("/api/veedor/reportes/pendientes").header("Authorization", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].verificacion").value("CUENTA_VERIFICADA"))
                .andExpect(jsonPath("$[0].redHash").doesNotExist());
    }

    /** D9: la cola dice qué reportes vienen de una red que hizo ráfaga en su barrio, sin revelar la red. */
    @Test
    void laColaDebeMarcarLosReportesQueVienenDeUnaRafagaDeUnaRed() throws Exception {
        autenticarComoVeedor();
        ReporteCiudadano deLaRafaga = reporte(EstadoModeracion.PENDIENTE)
                .conIdentidad(com.aguavigia.ctg.domain.NivelDeVerificacion.NINGUNA, "red-1");
        ReporteCiudadano deOtraRed = new ReporteCiudadano(new ReporteId("r2"), new SectorId("manga"), TipoReporte.SIN_AGUA,
                null, new HuellaDispositivo("hash-2"), TIMESTAMP, EstadoModeracion.PENDIENTE)
                .conIdentidad(com.aguavigia.ctg.domain.NivelDeVerificacion.NINGUNA, "red-2");
        given(listarPendientes.listar(anyInt(), anyInt())).willReturn(new ReportesPendientes(
                new Pagina<>(List.of(deLaRafaga, deOtraRed), 0, 50, 2),
                Set.of(new RedEnRafaga(new SectorId("manga"), "red-1"))));

        mockMvc.perform(get("/api/veedor/reportes/pendientes").header("Authorization", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].senalRed").value(true))
                .andExpect(jsonPath("$[1].senalRed").value(false))
                .andExpect(jsonPath("$[0].redHash").doesNotExist());
    }

    @Test
    void debeAprobarUnReporte() throws Exception {
        autenticarComoVeedor();
        given(moderarReporte.aprobar(new ReporteId("r1"))).willReturn(reporte(EstadoModeracion.APROBADO));

        mockMvc.perform(patch("/api/veedor/reportes/r1/aprobar").header("Authorization", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoModeracion").value("APROBADO"));
    }

    @Test
    void debeDescartarUnReporte() throws Exception {
        autenticarComoVeedor();
        given(moderarReporte.descartar(new ReporteId("r1"))).willReturn(reporte(EstadoModeracion.DESCARTADO));

        mockMvc.perform(patch("/api/veedor/reportes/r1/descartar").header("Authorization", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoModeracion").value("DESCARTADO"));
    }

    @Test
    void debeResponder404ConFormatoRfc7807SiElReporteNoExiste() throws Exception {
        autenticarComoVeedor();
        given(moderarReporte.aprobar(new ReporteId("no-existe")))
                .willThrow(new com.aguavigia.ctg.domain.EntidadNoEncontradaException("No existe el reporte 'no-existe'"));

        mockMvc.perform(patch("/api/veedor/reportes/no-existe/aprobar").header("Authorization", TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No existe el reporte 'no-existe'"));
    }

    // --- fotos (F3) ---

    /** La cola muestra la foto con la ruta del panel: la pública responde 404 hasta que el reporte se apruebe. */
    @Test
    void laColaDebeMostrarElEstadoDeLaFotoYSuRutaDelPanel() throws Exception {
        autenticarComoVeedor();
        given(listarPendientes.listar(anyInt(), anyInt())).willReturn(new ReportesPendientes(new Pagina<>(List.of(
                reporte(EstadoModeracion.PENDIENTE).conFoto("/api/fotos/abc.jpg", "sha")), 0, 50, 1), Set.of()));

        mockMvc.perform(get("/api/veedor/reportes/pendientes").header("Authorization", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fotoEstado").value("EN_REVISION"))
                .andExpect(jsonPath("$[0].fotoUrl").value("/api/veedor/fotos/abc.jpg"));
    }

    @Test
    void unReporteSinFotoDebeMostrarSinFotoYSinRuta() throws Exception {
        autenticarComoVeedor();
        given(listarPendientes.listar(anyInt(), anyInt())).willReturn(new ReportesPendientes(
                new Pagina<>(List.of(reporte(EstadoModeracion.PENDIENTE)), 0, 50, 1), Set.of()));

        mockMvc.perform(get("/api/veedor/reportes/pendientes").header("Authorization", TOKEN))
                .andExpect(jsonPath("$[0].fotoEstado").value("SIN_FOTO"))
                .andExpect(jsonPath("$[0].fotoUrl").doesNotExist());
    }

    @Test
    void debeDescartarSoloLaFotoDeUnReporte() throws Exception {
        autenticarComoVeedor();
        given(descartarFoto.descartarFoto(new ReporteId("r1"))).willReturn(
                reporte(EstadoModeracion.APROBADO).conFoto("/api/fotos/abc.jpg", "sha").descartarFoto());

        mockMvc.perform(patch("/api/veedor/reportes/r1/foto/descartar").header("Authorization", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoModeracion").value("APROBADO"))
                .andExpect(jsonPath("$.fotoEstado").value("DESCARTADA"));
    }

    @Test
    void descartarLaFotoDeUnReporteQueNoExisteDebeResponder404() throws Exception {
        autenticarComoVeedor();
        given(descartarFoto.descartarFoto(new ReporteId("no-existe")))
                .willThrow(new com.aguavigia.ctg.domain.EntidadNoEncontradaException("No existe el reporte 'no-existe'"));

        mockMvc.perform(patch("/api/veedor/reportes/no-existe/foto/descartar").header("Authorization", TOKEN))
                .andExpect(status().isNotFound());
    }

    @Test
    void descartarLaFotoDeUnReporteSinFotoDebeResponder409() throws Exception {
        autenticarComoVeedor();
        given(descartarFoto.descartarFoto(new ReporteId("r1")))
                .willThrow(new IllegalStateException("El reporte 'r1' no tiene foto."));

        mockMvc.perform(patch("/api/veedor/reportes/r1/foto/descartar").header("Authorization", TOKEN))
                .andExpect(status().isConflict());
    }

    @Test
    void descartarUnaFotoExigeElPermisoDeModerar() throws Exception {
        given(jwtProvider.validar("token-de-veedor"))
                .willReturn(Optional.of(AutenticacionDePrueba.sesionCon(Permiso.VER_PANEL)));

        mockMvc.perform(patch("/api/veedor/reportes/r1/foto/descartar").header("Authorization", TOKEN))
                .andExpect(status().isForbidden());
    }
}
