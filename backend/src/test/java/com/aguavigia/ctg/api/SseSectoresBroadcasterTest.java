package com.aguavigia.ctg.api;

import com.aguavigia.ctg.domain.LimiteDePeticionesExcedidoException;
import com.aguavigia.ctg.domain.port.out.RelojPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;

import static com.aguavigia.ctg.api.SseSectoresBroadcaster.CANAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

/**
 * El SSE ya no empuja el estado de los sectores: avisa de que cambió (`actualizadoEn`) y el cliente
 * lo pide con GET /api/sectores, que sí se cachea. Con 50 000 conexiones abiertas, enviar el
 * listado completo a cada una en cada cambio eran ~1,25 GB por evento.
 *
 * `MockMvcBuilders.standaloneSetup` deja driblar el ciclo de vida real de un SseEmitter sin depender
 * de clases internas de Spring. La difusión usa un Executor inyectable: aquí es síncrono.
 */
class SseSectoresBroadcasterTest {

    private static final Instant INSTANTE = Instant.parse("2026-08-11T10:00:00Z");
    private static final int MAXIMO = 2;

    @RestController
    static class ControladorDePrueba {
        private final SseSectoresBroadcaster broadcaster;

        ControladorDePrueba(SseSectoresBroadcaster broadcaster) {
            this.broadcaster = broadcaster;
        }

        @GetMapping(value = "/stream-de-prueba", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        SseEmitter stream() {
            return broadcaster.registrar();
        }
    }

    private RelojPort reloj;
    private RedisTemplate<String, String> redisTemplate;
    private SseSectoresBroadcaster broadcaster;
    private MockMvc mockMvc;

    @BeforeEach
    void montar() {
        reloj = mock(RelojPort.class);
        redisTemplate = mock(RedisTemplate.class);
        broadcaster = new SseSectoresBroadcaster(reloj, redisTemplate, Runnable::run, MAXIMO);
        mockMvc = MockMvcBuilders.standaloneSetup(new ControladorDePrueba(broadcaster)).build();
        given(reloj.ahora()).willReturn(INSTANTE);
    }

    private MvcResult conectar() throws Exception {
        return mockMvc.perform(get("/stream-de-prueba")).andExpect(request().asyncStarted()).andReturn();
    }

    private static int eventos(MvcResult conexion) throws Exception {
        String cuerpo = conexion.getResponse().getContentAsString();
        return cuerpo.split("event:sectores", -1).length - 1;
    }

    @Test
    void alConectarDebeAvisarConLaHoraDelUltimoCambioSinEmpujarElListado() throws Exception {
        MvcResult conexion = conectar();

        String cuerpo = conexion.getResponse().getContentAsString();
        assertThat(cuerpo).contains("event:sectores").contains("actualizadoEn").contains("2026-08-11T10:00:00Z");
        assertThat(cuerpo).doesNotContain("\"sectores\"");
    }

    @Test
    void debeIndicarAlClienteCuantoEsperarAntesDeReconectar() throws Exception {
        MvcResult conexion = conectar();

        assertThat(conexion.getResponse().getContentAsString()).containsPattern("retry:\\d+");
    }

    @Test
    void notificarActualizacionDebePublicarEnElCanalDeRedis() {
        broadcaster.notificarActualizacion();

        verify(redisTemplate).convertAndSend(eq(CANAL), any());
    }

    @Test
    void debeRechazarUnaConexionNuevaAlAlcanzarElTope() throws Exception {
        conectar();
        conectar();

        assertThatThrownBy(broadcaster::registrar)
                .isInstanceOf(LimiteDePeticionesExcedidoException.class)
                .hasMessageContaining("conexiones");
        assertThat(broadcaster.conexionesActivas()).isEqualTo(MAXIMO);
    }

    /** Un mensaje de Redis solo marca que hay algo que difundir; quien envía es el barrido periódico. */
    @Test
    void onMessageNoDebeEnviarNadaHastaElBarridoPeriodico() throws Exception {
        MvcResult conexion = conectar();
        int alConectar = eventos(conexion);

        broadcaster.onMessage(null, null);

        assertThat(eventos(conexion)).isEqualTo(alConectar);
    }

    @Test
    void elBarridoDebeAvisarATodosLosClientesRegistrados() throws Exception {
        MvcResult primero = conectar();
        MvcResult segundo = conectar();
        int antes = eventos(primero);

        broadcaster.onMessage(null, null);
        broadcaster.difundirPendiente();

        assertThat(eventos(primero)).isEqualTo(antes + 1);
        assertThat(eventos(segundo)).isEqualTo(antes + 1);
    }

    /** Una ráfaga de cambios (una avería masiva) cuesta un solo aviso por cliente, no uno por cambio. */
    @Test
    void variosMensajesSeguidosDebenCoalescerseEnUnSoloAviso() throws Exception {
        MvcResult conexion = conectar();
        int antes = eventos(conexion);

        broadcaster.onMessage(null, null);
        broadcaster.onMessage(null, null);
        broadcaster.onMessage(null, null);
        broadcaster.difundirPendiente();
        broadcaster.difundirPendiente();

        assertThat(eventos(conexion)).isEqualTo(antes + 1);
    }

    @Test
    void elLatidoDebeMantenerVivaLaConexionSinAvisarDeUnCambio() throws Exception {
        MvcResult conexion = conectar();
        int antes = eventos(conexion);

        broadcaster.enviarLatido();

        String cuerpo = conexion.getResponse().getContentAsString();
        assertThat(cuerpo).contains(":latido");
        assertThat(eventos(conexion)).isEqualTo(antes);
    }
}
