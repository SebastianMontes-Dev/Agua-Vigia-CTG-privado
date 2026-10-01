package com.aguavigia.ctg.api;

import com.aguavigia.ctg.api.mapper.CorteApiMapperImpl;
import com.aguavigia.ctg.domain.CorteAgua;
import com.aguavigia.ctg.domain.CorteId;
import com.aguavigia.ctg.domain.EstadoCorte;
import com.aguavigia.ctg.domain.OrigenCorte;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.port.in.GestionarCorteOficialUseCase;
import com.aguavigia.ctg.domain.port.in.ListarCortesVencidosUseCase;
import com.aguavigia.ctg.domain.port.out.CorteAguaRepository;
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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * `RateLimitConfig` no lleva reglas aquí (`REC-006`): sin esto, el interceptor llamaría a un
 * `RedisTemplate` mockeado y cada prueba fallaría con 500 sin relación con lo que se está probando.
 */
@WebMvcTest(CorteController.class)
@Import({CorteApiMapperImpl.class, SecurityConfig.class})
@TestPropertySource(properties = "aguavigia.rate-limit.reglas=")
class CorteControllerTest {

    private static final Instant INICIO = Instant.parse("2026-08-09T10:00:00Z");
    private static final String TOKEN = "Bearer token-de-veedor";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GestionarCorteOficialUseCase gestionarCorte;

    @MockitoBean
    private CorteAguaRepository cortes;

    @MockitoBean
    private ListarCortesVencidosUseCase listarVencidos;

    @MockitoBean
    private JwtProvider jwtProvider;

    @MockitoBean
    private com.aguavigia.ctg.domain.port.out.RevocacionSesionPort revocacion;

    // RateLimitConfig implementa WebMvcConfigurer y se instancia en cualquier @WebMvcTest aunque
    // no se importe (REC-006).
    @MockitoBean(name = "redisTemplate")
    private RedisTemplate<String, String> redisTemplateMock;

    private CorteAgua corteDePrueba(EstadoCorte estado, Instant finReal) {
        return CorteAgua.builder()
                .id(new CorteId("corte-1"))
                .sectoresAfectados(List.of(new SectorId("manga")))
                .inicio(INICIO)
                .finPrometido(INICIO.plus(6, ChronoUnit.HOURS))
                .finReal(finReal)
                .causa("Mantenimiento planta El Bosque")
                .origen(OrigenCorte.VEEDOR)
                .estado(estado)
                .build();
    }

    private void autenticarComoVeedor() {
        given(jwtProvider.validar("token-de-veedor"))
                .willReturn(Optional.of(AutenticacionDePrueba.sesionCon(Permiso.VER_PANEL, Permiso.GESTIONAR_CORTES)));
    }

    @Test
    void debeRechazarUnaPeticionSinTokenCon401() throws Exception {
        mockMvc.perform(get("/api/veedor/cortes").param("sectorId", "manga"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void debeRegistrarUnCorteYResponder201() throws Exception {
        autenticarComoVeedor();
        given(gestionarCorte.registrar(any(CorteAgua.class))).willReturn(corteDePrueba(EstadoCorte.ANUNCIADO, null));

        mockMvc.perform(post("/api/veedor/cortes")
                        .header("Authorization", TOKEN)
                        .contentType("application/json")
                        .content("""
                                {"sectoresAfectados":["manga"],"inicio":"2026-08-09T10:00:00Z",
                                 "finPrometido":"2026-08-09T16:00:00Z","causa":"Mantenimiento planta El Bosque"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("corte-1"))
                .andExpect(jsonPath("$.estado").value("ANUNCIADO"))
                .andExpect(jsonPath("$.origen").value("VEEDOR"))
                .andExpect(jsonPath("$.sectoresAfectados[0]").value("manga"));
    }

    @Test
    void debeResponder400ConFormatoRfc7807SiElSectorNoExiste() throws Exception {
        autenticarComoVeedor();
        given(gestionarCorte.registrar(any(CorteAgua.class)))
                .willThrow(new IllegalArgumentException("No existe el sector 'no-existe'"));

        mockMvc.perform(post("/api/veedor/cortes")
                        .header("Authorization", TOKEN)
                        .contentType("application/json")
                        .content("""
                                {"sectoresAfectados":["no-existe"],"inicio":"2026-08-09T10:00:00Z",
                                 "finPrometido":"2026-08-09T16:00:00Z","causa":"Mantenimiento"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("No existe el sector 'no-existe'"));
    }

    @Test
    void debeResponder400SiFaltaLaCausa() throws Exception {
        autenticarComoVeedor();

        mockMvc.perform(post("/api/veedor/cortes")
                        .header("Authorization", TOKEN)
                        .contentType("application/json")
                        .content("""
                                {"sectoresAfectados":["manga"],"inicio":"2026-08-09T10:00:00Z",
                                 "finPrometido":"2026-08-09T16:00:00Z"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void debeCerrarUnCorteYResponder200() throws Exception {
        autenticarComoVeedor();
        Instant finReal = INICIO.plus(5, ChronoUnit.HOURS);
        given(gestionarCorte.cerrar(eq(new CorteId("corte-1")), any(Instant.class)))
                .willReturn(corteDePrueba(EstadoCorte.RESTABLECIDO, finReal));

        mockMvc.perform(patch("/api/veedor/cortes/corte-1/cierre")
                        .header("Authorization", TOKEN)
                        .contentType("application/json")
                        .content("""
                                {"horaReal":"2026-08-09T15:00:00Z"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RESTABLECIDO"))
                .andExpect(jsonPath("$.cierres[0].sectorId").value("manga"))
                .andExpect(jsonPath("$.cierres[0].fuente").value("VEEDOR"))
                .andExpect(jsonPath("$.cierres[0].provisional").value(false))
                .andExpect(jsonPath("$.finReal").doesNotExist());
    }

    @Test
    void debeResponder409SiElCorteYaEstabaCerrado() throws Exception {
        autenticarComoVeedor();
        given(gestionarCorte.cerrar(eq(new CorteId("corte-1")), any(Instant.class)))
                .willThrow(new IllegalStateException("El corte 'corte-1' ya está cerrado"));

        mockMvc.perform(patch("/api/veedor/cortes/corte-1/cierre")
                        .header("Authorization", TOKEN)
                        .contentType("application/json")
                        .content("""
                                {"horaReal":"2026-08-09T15:00:00Z"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("El corte 'corte-1' ya está cerrado"));
    }

    @Test
    void debeConsultarUnCortePorId() throws Exception {
        autenticarComoVeedor();
        given(cortes.buscarPorId(new CorteId("corte-1")))
                .willReturn(Optional.of(corteDePrueba(EstadoCorte.ANUNCIADO, null)));

        mockMvc.perform(get("/api/veedor/cortes/corte-1").header("Authorization", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("corte-1"));
    }

    @Test
    void debeResponder404ConFormatoRfc7807SiElCorteNoExiste() throws Exception {
        autenticarComoVeedor();
        given(cortes.buscarPorId(new CorteId("no-existe"))).willReturn(Optional.empty());

        mockMvc.perform(get("/api/veedor/cortes/no-existe").header("Authorization", TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No existe el corte 'no-existe'"));
    }

    @Test
    void debeListarLosCortesDeUnSector() throws Exception {
        autenticarComoVeedor();
        given(cortes.listarPorSector(new SectorId("manga")))
                .willReturn(List.of(corteDePrueba(EstadoCorte.ANUNCIADO, null)));

        mockMvc.perform(get("/api/veedor/cortes").param("sectorId", "manga").header("Authorization", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("corte-1"));
    }

    @Test
    void debeRegistrarUnCorteConCaducidadOpcionalYExponerla() throws Exception {
        autenticarComoVeedor();
        org.mockito.ArgumentCaptor<CorteAgua> captor = org.mockito.ArgumentCaptor.forClass(CorteAgua.class);
        given(gestionarCorte.registrar(captor.capture())).willAnswer(i -> i.getArgument(0));

        mockMvc.perform(post("/api/veedor/cortes")
                        .header("Authorization", TOKEN)
                        .contentType("application/json")
                        .content("""
                                {"sectoresAfectados":["manga"],"inicio":"2026-08-09T10:00:00Z",
                                 "finPrometido":"2026-08-09T16:00:00Z","caducaEn":"2026-08-09T14:00:00Z",
                                 "causa":"Reparación"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.caducaEn").value("2026-08-09T14:00:00Z"));

        org.assertj.core.api.Assertions.assertThat(captor.getValue().caducaEn()).isEqualTo(Instant.parse("2026-08-09T14:00:00Z"));
    }

    @Test
    void debeCerrarUnSoloBarrioDelCorte() throws Exception {
        autenticarComoVeedor();
        given(gestionarCorte.cerrarSector(eq(new CorteId("corte-1")), eq(new SectorId("manga")), any(Instant.class)))
                .willReturn(corteDePrueba(EstadoCorte.RESTABLECIDO, INICIO.plus(5, ChronoUnit.HOURS)));

        mockMvc.perform(patch("/api/veedor/cortes/corte-1/sectores/manga/cierre")
                        .header("Authorization", TOKEN)
                        .contentType("application/json")
                        .content("""
                                {"horaReal":"2026-08-09T15:00:00Z"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("corte-1"));
    }

    @Test
    void debeConfirmarElCierreProvisionalDeUnBarrio() throws Exception {
        autenticarComoVeedor();
        given(gestionarCorte.confirmarCierre(eq(new CorteId("corte-1")), eq(new SectorId("manga")), any(Instant.class)))
                .willReturn(corteDePrueba(EstadoCorte.RESTABLECIDO, INICIO.plus(5, ChronoUnit.HOURS)));

        mockMvc.perform(patch("/api/veedor/cortes/corte-1/sectores/manga/confirmacion")
                        .header("Authorization", TOKEN)
                        .contentType("application/json")
                        .content("""
                                {"horaReal":"2026-08-09T15:05:00Z"}"""))
                .andExpect(status().isOk());

        org.mockito.Mockito.verify(gestionarCorte).confirmarCierre(new CorteId("corte-1"), new SectorId("manga"),
                Instant.parse("2026-08-09T15:05:00Z"));
    }

    @Test
    void unCierreOConfirmacionSinHoraEsUnaPeticionInvalida() throws Exception {
        autenticarComoVeedor();

        mockMvc.perform(patch("/api/veedor/cortes/corte-1/sectores/manga/cierre")
                        .header("Authorization", TOKEN).contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/veedor/cortes/corte-1/sectores/manga/confirmacion")
                        .header("Authorization", TOKEN).contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void debeResponder409SiElCierreDelBarrioYaEstabaConfirmado() throws Exception {
        autenticarComoVeedor();
        given(gestionarCorte.confirmarCierre(any(), any(), any()))
                .willThrow(new IllegalStateException("El cierre de 'manga' ya está confirmado"));

        mockMvc.perform(patch("/api/veedor/cortes/corte-1/sectores/manga/confirmacion")
                        .header("Authorization", TOKEN)
                        .contentType("application/json")
                        .content("""
                                {"horaReal":"2026-08-09T15:05:00Z"}"""))
                .andExpect(status().isConflict());
    }

    @Test
    void debeAnularUnCorteConSuMotivoYDejarloExpuesto() throws Exception {
        autenticarComoVeedor();
        CorteAgua anulado = corteDePrueba(EstadoCorte.ANUNCIADO, null).anular("El boletín hablaba de otro barrio");
        given(gestionarCorte.anular(eq(new CorteId("corte-1")), eq("El boletín hablaba de otro barrio"), any()))
                .willReturn(anulado);

        mockMvc.perform(patch("/api/veedor/cortes/corte-1/anulacion")
                        .header("Authorization", TOKEN)
                        .contentType("application/json")
                        .content("""
                                {"motivo":"El boletín hablaba de otro barrio"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ANULADO"))
                .andExpect(jsonPath("$.motivoAnulacion").value("El boletín hablaba de otro barrio"));
    }

    @Test
    void anularSinMotivoEsUnaPeticionInvalida() throws Exception {
        autenticarComoVeedor();

        mockMvc.perform(patch("/api/veedor/cortes/corte-1/anulacion")
                        .header("Authorization", TOKEN).contentType("application/json").content("""
                                {"motivo":"  "}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anularUnCorteQueYaEstabaAnuladoResponde409() throws Exception {
        autenticarComoVeedor();
        given(gestionarCorte.anular(any(), any(), any()))
                .willThrow(new IllegalStateException("El corte 'corte-1' ya está anulado"));

        mockMvc.perform(patch("/api/veedor/cortes/corte-1/anulacion")
                        .header("Authorization", TOKEN).contentType("application/json").content("""
                                {"motivo":"Otra vez"}"""))
                .andExpect(status().isConflict());
    }

    @Test
    void anularExigeElPermisoDeGestionarCortes() throws Exception {
        given(jwtProvider.validar("token-de-veedor"))
                .willReturn(Optional.of(AutenticacionDePrueba.sesionCon(Permiso.VER_PANEL)));

        mockMvc.perform(patch("/api/veedor/cortes/corte-1/anulacion")
                        .header("Authorization", TOKEN).contentType("application/json").content("""
                                {"motivo":"Por error"}"""))
                .andExpect(status().isForbidden());
    }

    @Test
    void debeListarLaColaDeCortesVencidos() throws Exception {
        autenticarComoVeedor();
        given(listarVencidos.listar()).willReturn(List.of(corteDePrueba(EstadoCorte.ANUNCIADO, null)));

        mockMvc.perform(get("/api/veedor/cortes/vencidos").header("Authorization", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("corte-1"));
    }
}
