package com.aguavigia.ctg.api;

import com.aguavigia.ctg.infrastructure.sse.SseSectoresBroadcaster;
import com.aguavigia.ctg.api.error.ManejadorGlobalDeErrores;
import com.aguavigia.ctg.api.mapper.SectorApiMapperImpl;
import com.aguavigia.ctg.domain.EstadoServicio;
import com.aguavigia.ctg.domain.Sector;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.GeometriaSector;
import com.aguavigia.ctg.domain.port.out.GeometriaSectoresPort;
import com.aguavigia.ctg.domain.port.out.RelojPort;
import com.aguavigia.ctg.domain.port.out.SectorRepository;
import com.aguavigia.ctg.infrastructure.config.SecurityConfig;
import com.aguavigia.ctg.infrastructure.security.JwtProvider;
import com.aguavigia.ctg.domain.port.out.RevocacionSesionPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SectorController.class)
@Import({SectorApiMapperImpl.class, ManejadorGlobalDeErrores.class, SecurityConfig.class})
class SectorControllerTest {

    // SecurityConfig construye JwtAuthenticationFilter con este puerto: el filtro consulta la
    // revocacion en cada peticion con token (ADR-039). Sin el bean, el contexto del slice no carga.
    @MockitoBean
    private RevocacionSesionPort revocacion;

    private static final Instant INSTANTE_FIJO = Instant.parse("2026-08-08T15:30:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SectorRepository sectores;

    @MockitoBean
    private RelojPort reloj;

    @MockitoBean
    private GeometriaSectoresPort geometrias;

    @MockitoBean
    private JwtProvider jwtProvider;

    // RateLimitConfig implementa WebMvcConfigurer: @WebMvcTest lo detecta e instancia en
    // cualquier slice del proyecto, aunque no se importe aqui — necesita este bean para construirse.
    // name="redisTemplate" porque RateLimitConfig lo pide con @Qualifier("redisTemplate").
    @MockitoBean(name = "redisTemplate")
    private RedisTemplate<String, String> redisTemplateMock;

    @MockitoBean
    private SseSectoresBroadcaster sseBroadcaster;

    /** El frontend nuevo no tiene el GeoJSON: lo pide aquí, ya con el id que usa el resto de la API. */
    @Test
    void geometriaDebeDevolverUnFeatureCollectionConElIdDeCadaSector() throws Exception {
        given(geometrias.listar()).willReturn(List.of(new GeometriaSector(
                new SectorId("bocagrande"), "BOCAGRANDE",
                java.util.Map.of("type", "Polygon", "coordinates", List.of()))));

        mockMvc.perform(get("/api/sectores/geometria"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/geo+json"))
                .andExpect(jsonPath("$.type").value("FeatureCollection"))
                .andExpect(jsonPath("$.features[0].type").value("Feature"))
                .andExpect(jsonPath("$.features[0].id").value("bocagrande"))
                .andExpect(jsonPath("$.features[0].properties.nombre").value("BOCAGRANDE"))
                .andExpect(jsonPath("$.features[0].geometry.type").value("Polygon"));
    }

    /** Los polígonos no cambian entre siembras: un día de caché evita reenviar ~3 MB a cada visita. */
    @Test
    void geometriaDebeSerCacheableEnPublico() throws Exception {
        given(geometrias.listar()).willReturn(List.of());

        mockMvc.perform(get("/api/sectores/geometria"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Cache-Control", org.hamcrest.Matchers.containsString("public")));
    }

    @Test
    void debeDevolverElListadoConLaHoraEnQueSeGenero() throws Exception {
        given(reloj.ahora()).willReturn(INSTANTE_FIJO);
        given(sectores.listarTodos()).willReturn(List.of(
                new Sector(new SectorId("bocagrande"), "BOCAGRANDE", 12000, EstadoServicio.SIN_SERVICIO)));

        mockMvc.perform(get("/api/sectores"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generadoEn").value("2026-08-08T15:30:00Z"))
                .andExpect(jsonPath("$.sectores[0].id").value("bocagrande"))
                .andExpect(jsonPath("$.sectores[0].nombre").value("BOCAGRANDE"))
                .andExpect(jsonPath("$.sectores[0].estado").value("SIN_SERVICIO"));
    }

    @Test
    void debeExponerEstadoNuloTalCualYNoComoConServicio() throws Exception {
        given(reloj.ahora()).willReturn(INSTANTE_FIJO);
        given(sectores.listarTodos()).willReturn(List.of(
                new Sector(new SectorId("isla-fuerte"), "ISLA FUERTE", null, null)));

        // ADR-014: el contrato transmite la ausencia de dato; no la rellena con un valor optimista.
        // La clave viaja presente y en null — explicito para el cliente generado, no omitida.
        mockMvc.perform(get("/api/sectores"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"estado\":null")))
                .andExpect(jsonPath("$.sectores[0].estado").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void debePublicarLaPoblacionCensalDelSectorYNuloSiNoHayDato() throws Exception {
        given(reloj.ahora()).willReturn(INSTANTE_FIJO);
        given(sectores.listarTodos()).willReturn(List.of(
                new Sector(new SectorId("bocagrande"), "BOCAGRANDE", 12000, null),
                new Sector(new SectorId("isla-fuerte"), "ISLA FUERTE", null, null)));

        // Sin dato censal la población viaja en null, nunca en 0: un 0 se leería como «nadie vive ahí».
        mockMvc.perform(get("/api/sectores"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sectores[0].poblacion").value(12000))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"poblacion\":null")));
    }

    @Test
    void debeDevolverUnSectorPorSuIdentificador() throws Exception {
        given(sectores.buscarPorId(any(SectorId.class))).willReturn(Optional.of(
                new Sector(new SectorId("manga"), "MANGA", 5000, EstadoServicio.PRESION_BAJA)));

        mockMvc.perform(get("/api/sectores/manga"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PRESION_BAJA"));
    }

    @Test
    void debeResponder404EnFormatoRfc7807CuandoElSectorNoExiste() throws Exception {
        given(sectores.buscarPorId(any(SectorId.class))).willReturn(Optional.empty());

        mockMvc.perform(get("/api/sectores/no-existe"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.title").value("Recurso no encontrado"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.type").value("https://aguavigia.example/errores/recurso-no-encontrado"));
    }

    /**
     * El controlador solo delega en el broadcaster (ADR-015-like: sin regla propia). Que la
     * respuesta viaje como `text/event-stream` con el estado actual es responsabilidad de
     * SseSectoresBroadcaster.registrar() y se prueba en SseSectoresBroadcasterTest.
     */
    @Test
    void debeIniciarStreamDeEventosSse() throws Exception {
        given(sseBroadcaster.registrar()).willReturn(new SseEmitter());

        mockMvc.perform(get("/api/sectores/stream"))
                .andExpect(status().isOk());

        verify(sseBroadcaster).registrar();
    }
}
