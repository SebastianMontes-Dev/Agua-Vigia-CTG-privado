package com.aguavigia.ctg.api;

import com.aguavigia.ctg.api.mapper.SectorApiMapperImpl;
import com.aguavigia.ctg.domain.EstadoServicio;
import com.aguavigia.ctg.domain.MarcasDeEstado;
import com.aguavigia.ctg.domain.OrigenEstado;
import com.aguavigia.ctg.domain.Permiso;
import com.aguavigia.ctg.domain.Sector;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.VentanaTiempo;
import com.aguavigia.ctg.domain.port.in.ListarDisputasUseCase;
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
import java.util.List;
import java.util.Optional;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DisputaController.class)
@Import({SectorApiMapperImpl.class, SecurityConfig.class})
@TestPropertySource(properties = "aguavigia.rate-limit.reglas=")
class DisputaControllerTest {

    private static final Instant T = Instant.parse("2026-08-21T14:00:00Z");
    private static final String TOKEN = "Bearer token-de-veedor";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ListarDisputasUseCase disputas;

    @MockitoBean
    private JwtProvider jwtProvider;

    @MockitoBean
    private com.aguavigia.ctg.domain.port.out.RevocacionSesionPort revocacion;

    @MockitoBean(name = "redisTemplate")
    private RedisTemplate<String, String> redisTemplateMock;

    @Test
    void sinTokenResponde401() throws Exception {
        mockMvc.perform(get("/api/veedor/disputas")).andExpect(status().isUnauthorized());
    }

    @Test
    void listaLosBarriosEnDisputaConLosVecinosQueLosContradicen() throws Exception {
        given(jwtProvider.validar("token-de-veedor"))
                .willReturn(Optional.of(AutenticacionDePrueba.sesionCon(Permiso.VER_PANEL)));
        MarcasDeEstado marcas = new MarcasDeEstado(OrigenEstado.ACUACAR, new VentanaTiempo(T, T.plusSeconds(3600)),
                false, true, 9, null);
        given(disputas.listar()).willReturn(List.of(new Sector(new SectorId("manga"), "MANGA", 10754,
                EstadoServicio.SIN_SERVICIO, T, T, marcas)));

        mockMvc.perform(get("/api/veedor/disputas").header("Authorization", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("manga"))
                .andExpect(jsonPath("$[0].enDisputa").value(true))
                .andExpect(jsonPath("$[0].reportesEnContra").value(9))
                .andExpect(jsonPath("$[0].origen").value("ACUACAR"));
    }
}
