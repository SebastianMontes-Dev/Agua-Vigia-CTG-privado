package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.ChatTelegramId;
import com.aguavigia.ctg.domain.EstadoServicio;
import com.aguavigia.ctg.domain.MensajeTelegram;
import com.aguavigia.ctg.domain.Sector;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.SuscripcionTelegram;
import com.aguavigia.ctg.domain.port.out.EnvioTelegramPort;
import com.aguavigia.ctg.domain.port.out.RelojPort;
import com.aguavigia.ctg.domain.port.out.SectorRepository;
import com.aguavigia.ctg.domain.port.out.SuscripcionTelegramRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProcesarMensajeTelegramServiceTest {

    private static final ChatTelegramId CHAT = new ChatTelegramId(777L);
    private static final Instant AHORA = Instant.parse("2026-09-24T15:00:00Z");

    private final Map<ChatTelegramId, SuscripcionTelegram> guardadas = new HashMap<>();
    private final List<String> respuestas = new ArrayList<>();
    private final SectorRepository sectores = mock(SectorRepository.class);
    private ProcesarMensajeTelegramService servicio;

    @BeforeEach
    void preparar() {
        Sector bocagrande = new Sector(new SectorId("bocagrande"), "BOCAGRANDE", 5583, null);
        Sector manga = new Sector(new SectorId("manga"), "MANGA", 9000, EstadoServicio.SIN_SERVICIO);
        Sector castillogrande = new Sector(new SectorId("castillogrande"), "CASTILLOGRANDE", 3000, EstadoServicio.CON_SERVICIO);
        Sector matuna = new Sector(new SectorId("la-matuna"), "LA MATUNA", 2000, EstadoServicio.CON_SERVICIO);
        List<Sector> todos = List.of(bocagrande, manga, castillogrande, matuna);
        when(sectores.listarTodos()).thenReturn(todos);
        when(sectores.buscarPorId(org.mockito.ArgumentMatchers.any())).thenAnswer(invocacion -> todos.stream()
                .filter(s -> s.id().equals(invocacion.getArgument(0))).findFirst());

        SuscripcionTelegramRepository repositorio = new SuscripcionTelegramRepository() {
            @Override
            public Optional<SuscripcionTelegram> buscarPorChat(ChatTelegramId chat) {
                return Optional.ofNullable(guardadas.get(chat));
            }

            @Override
            public SuscripcionTelegram guardar(SuscripcionTelegram suscripcion) {
                guardadas.put(suscripcion.chat(), suscripcion);
                return suscripcion;
            }

            @Override
            public void eliminar(ChatTelegramId chat) {
                guardadas.remove(chat);
            }

            @Override
            public List<SuscripcionTelegram> buscarPorSector(SectorId sectorId) {
                return guardadas.values().stream().filter(s -> s.sigue(sectorId)).toList();
            }
        };
        EnvioTelegramPort envio = (chat, texto) -> respuestas.add(texto);
        RelojPort reloj = mock(RelojPort.class);
        when(reloj.ahora()).thenReturn(AHORA);
        servicio = new ProcesarMensajeTelegramService(repositorio, sectores, envio, reloj);
    }

    private void escribir(String texto) {
        servicio.procesar(new MensajeTelegram(CHAT, texto));
    }

    private String ultimaRespuesta() {
        return respuestas.get(respuestas.size() - 1);
    }

    @Test
    void startYAyudaExplicanLosComandosYQueSoloSeGuardaElChat() {
        escribir("/start");
        escribir("/ayuda");

        assertThat(respuestas).hasSize(2).allSatisfy(r -> assertThat(r).contains("/suscribir", "/baja", "identificador de este chat"));
    }

    @Test
    void suscribirGuardaElChatConElSectorYConfirma() {
        escribir("/suscribir Bocagrande");

        assertThat(guardadas.get(CHAT).sigue(new SectorId("bocagrande"))).isTrue();
        assertThat(guardadas.get(CHAT).creadaEn()).isEqualTo(AHORA);
        assertThat(ultimaRespuesta()).contains("BOCAGRANDE");
    }

    @Test
    void elSectorSeEntiendeSinTildesNiMayusculasNiEspaciosDeMas() {
        escribir("/suscribir   la   MATUNA ");

        assertThat(guardadas.get(CHAT).sigue(new SectorId("la-matuna"))).isTrue();
    }

    @Test
    void elComandoAcepataElNombreDelBotDeLosGrupos() {
        escribir("/suscribir@AguaVigiaBot Manga");

        assertThat(guardadas.get(CHAT).sigue(new SectorId("manga"))).isTrue();
    }

    @Test
    void suscribirDosVecesElMismoSectorAvisaSinDuplicar() {
        escribir("/suscribir Manga");
        escribir("/suscribir manga");

        assertThat(guardadas.get(CHAT).sectorIds()).hasSize(1);
        assertThat(ultimaRespuesta()).contains("Ya seguías");
    }

    @Test
    void unSectorInexistenteNoSuscribeYSugiereParecidos() {
        escribir("/suscribir grande");

        assertThat(guardadas).isEmpty();
        assertThat(ultimaRespuesta()).contains("BOCAGRANDE", "CASTILLOGRANDE");
    }

    @Test
    void unSectorSinNingunParecidoPideEscribirloComoEnElMapa() {
        escribir("/suscribir zzz");

        assertThat(guardadas).isEmpty();
        assertThat(ultimaRespuesta()).contains("No encontré ningún sector");
    }

    @Test
    void suscribirSinSectorPideElSector() {
        escribir("/suscribir");

        assertThat(guardadas).isEmpty();
        assertThat(ultimaRespuesta()).contains("Dime el sector");
    }

    @Test
    void superarElMaximoDeSectoresLoAvisaSinRomperNada() {
        List<Sector> muchos = new ArrayList<>();
        SuscripcionTelegram llena = SuscripcionTelegram.nueva(CHAT, AHORA);
        for (int i = 0; i < SuscripcionTelegram.MAXIMO_SECTORES; i++) {
            llena = llena.siguiendo(new SectorId("otro-" + i));
        }
        guardadas.put(CHAT, llena);

        escribir("/suscribir Manga");

        assertThat(guardadas.get(CHAT).sigue(new SectorId("manga"))).isFalse();
        assertThat(ultimaRespuesta()).contains("máximo");
    }

    @Test
    void bajaDeUnSectorConservaLosDemas() {
        escribir("/suscribir Manga");
        escribir("/suscribir Bocagrande");

        escribir("/baja Manga");

        assertThat(guardadas.get(CHAT).sectorIds()).containsExactly(new SectorId("bocagrande"));
    }

    @Test
    void bajaDelUltimoSectorBorraElChatDeLosRegistros() {
        escribir("/suscribir Manga");

        escribir("/baja Manga");

        assertThat(guardadas).isEmpty();
        assertThat(ultimaRespuesta()).contains("borré tu chat");
    }

    @Test
    void bajaSinSectorBorraTodoElRegistroDelChat() {
        escribir("/suscribir Manga");
        escribir("/suscribir Bocagrande");

        escribir("/baja");

        assertThat(guardadas).isEmpty();
        assertThat(ultimaRespuesta()).contains("borré tu chat");
    }

    @Test
    void bajaDeUnSectorQueNoSeSeguiaLoAvisaSinTocarNada() {
        escribir("/suscribir Manga");

        escribir("/baja Bocagrande");

        assertThat(guardadas.get(CHAT).sectorIds()).hasSize(1);
        assertThat(ultimaRespuesta()).contains("No seguías");
    }

    @Test
    void bajaSinHaberSeguidoNadaNoFalla() {
        escribir("/baja");

        assertThat(ultimaRespuesta()).contains("No sigues ningún sector");
    }

    @Test
    void estadoDiceSinDatosCuandoNadieHaVerificadoElSectorYNuncaConServicio() {
        escribir("/estado Bocagrande");

        assertThat(ultimaRespuesta()).contains("sin datos verificados").doesNotContain("con servicio");
    }

    @Test
    void estadoDescribeElEstadoVigente() {
        escribir("/estado Manga");

        assertThat(ultimaRespuesta()).isEqualTo("MANGA: sin servicio.");
    }

    @Test
    void misListaLosSectoresQueSigue() {
        escribir("/suscribir Manga");
        escribir("/suscribir Bocagrande");

        escribir("/mis");

        assertThat(ultimaRespuesta()).contains("MANGA", "BOCAGRANDE");
    }

    @Test
    void misSinSuscripcionesDiceQueNoSigueNinguno() {
        escribir("/mis");

        assertThat(ultimaRespuesta()).contains("No sigues ningún sector");
    }

    @Test
    void unComandoDesconocidoYUnTextoLibreRemitenALaAyuda() {
        escribir("/bailar");
        escribir("hola");

        assertThat(respuestas).allSatisfy(r -> assertThat(r).contains("/ayuda"));
    }
}
