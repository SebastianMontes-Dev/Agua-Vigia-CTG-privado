package com.aguavigia.ctg.api;

import com.aguavigia.ctg.api.error.ManejadorGlobalDeErrores;
import com.aguavigia.ctg.api.mapper.CorteApiMapperImpl;
import com.aguavigia.ctg.domain.CorteAgua;
import com.aguavigia.ctg.domain.CorteId;
import com.aguavigia.ctg.domain.EstadoCorte;
import com.aguavigia.ctg.domain.OrigenCorte;
import com.aguavigia.ctg.domain.Sector;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.port.out.CorteAguaRepository;
import com.aguavigia.ctg.domain.port.out.RevocacionSesionPort;
import com.aguavigia.ctg.domain.port.out.SectorRepository;
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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** RF002 — el histórico de cortes de un sector es información pública: se ve sin iniciar sesión. */
@WebMvcTest(HistorialDeCortesController.class)
@Import({CorteApiMapperImpl.class, ManejadorGlobalDeErrores.class, SecurityConfig.class})
@TestPropertySource(properties = "aguavigia.rate-limit.reglas=")
class HistorialDeCortesControllerTest {

    private static final Instant BASE = Instant.parse("2026-08-09T10:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SectorRepository sectores;

    @MockitoBean
    private CorteAguaRepository cortes;

    @MockitoBean
    private JwtProvider jwtProvider;

    @MockitoBean
    private RevocacionSesionPort revocacion;

    @MockitoBean(name = "redisTemplate")
    private RedisTemplate<String, String> redisTemplateMock;

    private static CorteAgua corte(String id, int diasDespuesDeLaBase, EstadoCorte estado, Instant finReal) {
        Instant inicio = BASE.plus(diasDespuesDeLaBase, ChronoUnit.DAYS);
        return CorteAgua.builder()
                .id(new CorteId(id))
                .sectoresAfectados(List.of(new SectorId("manga")))
                .inicio(inicio)
                .finPrometido(inicio.plus(6, ChronoUnit.HOURS))
                .finReal(finReal)
                .causa("Mantenimiento")
                .origen(OrigenCorte.VEEDOR)
                .estado(estado)
                .build();
    }

    private void existeElSector() {
        given(sectores.buscarPorId(new SectorId("manga")))
                .willReturn(Optional.of(new Sector(new SectorId("manga"), "MANGA", 5000, null)));
    }

    @Test
    void debeListarLosCortesDeUnSectorSinAutenticacionYElMasRecientePrimero() throws Exception {
        existeElSector();
        given(cortes.listarPorSector(new SectorId("manga"))).willReturn(List.of(
                corte("viejo", 0, EstadoCorte.RESTABLECIDO, BASE.plus(7, ChronoUnit.HOURS)),
                corte("nuevo", 5, EstadoCorte.ANUNCIADO, null),
                corte("medio", 2, EstadoCorte.RESTABLECIDO, BASE.plus(2, ChronoUnit.DAYS).plus(8, ChronoUnit.HOURS))));

        mockMvc.perform(get("/api/sectores/manga/cortes"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "3"))
                .andExpect(jsonPath("$[0].id").value("nuevo"))
                .andExpect(jsonPath("$[0].finReal").doesNotExist())
                .andExpect(jsonPath("$[1].id").value("medio"))
                .andExpect(jsonPath("$[2].id").value("viejo"));
    }

    @Test
    void debePaginarElHistorico() throws Exception {
        existeElSector();
        given(cortes.listarPorSector(new SectorId("manga"))).willReturn(List.of(
                corte("c0", 0, EstadoCorte.CONFIRMADO, null), corte("c1", 1, EstadoCorte.CONFIRMADO, null),
                corte("c2", 2, EstadoCorte.CONFIRMADO, null), corte("c3", 3, EstadoCorte.CONFIRMADO, null),
                corte("c4", 4, EstadoCorte.CONFIRMADO, null)));

        mockMvc.perform(get("/api/sectores/manga/cortes").param("pagina", "1").param("tamano", "2"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "5"))
                .andExpect(header().string("X-Total-Pages", "3"))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value("c2"))
                .andExpect(jsonPath("$[1].id").value("c1"));
    }

    @Test
    void unSectorSinCortesDebeDevolverUnaListaVaciaNoUn404() throws Exception {
        existeElSector();
        given(cortes.listarPorSector(any())).willReturn(List.of());

        mockMvc.perform(get("/api/sectores/manga/cortes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0))
                .andExpect(header().string("X-Total-Count", "0"));
    }

    @Test
    void debeResponder404ConProblemaRfc7807SiElSectorNoExiste() throws Exception {
        given(sectores.buscarPorId(new SectorId("no-existe"))).willReturn(Optional.empty());

        mockMvc.perform(get("/api/sectores/no-existe/cortes"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
