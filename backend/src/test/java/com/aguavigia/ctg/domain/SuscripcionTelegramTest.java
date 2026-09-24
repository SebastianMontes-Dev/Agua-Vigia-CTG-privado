package com.aguavigia.ctg.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SuscripcionTelegramTest {

    private static final Instant AHORA = Instant.parse("2026-09-24T15:00:00Z");
    private static final ChatTelegramId CHAT = new ChatTelegramId(1234L);
    private static final SectorId MANGA = new SectorId("manga");
    private static final SectorId BOCAGRANDE = new SectorId("bocagrande");

    @Test
    void debeRechazarUnChatConIdentificadorCero() {
        assertThatThrownBy(() -> new ChatTelegramId(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unaSuscripcionNuevaNoSigueNingunSector() {
        SuscripcionTelegram suscripcion = SuscripcionTelegram.nueva(CHAT, AHORA);

        assertThat(suscripcion.sinSectores()).isTrue();
        assertThat(suscripcion.sigue(MANGA)).isFalse();
    }

    @Test
    void seguirUnSectorDosVecesNoLoDuplica() {
        SuscripcionTelegram suscripcion = SuscripcionTelegram.nueva(CHAT, AHORA).siguiendo(MANGA).siguiendo(MANGA);

        assertThat(suscripcion.sectorIds()).containsExactly(MANGA);
    }

    @Test
    void dejarDeSeguirUnSectorQuitaSoloEse() {
        SuscripcionTelegram suscripcion = SuscripcionTelegram.nueva(CHAT, AHORA)
                .siguiendo(MANGA).siguiendo(BOCAGRANDE).sinSeguir(MANGA);

        assertThat(suscripcion.sectorIds()).containsExactly(BOCAGRANDE);
        assertThat(suscripcion.sinSeguir(BOCAGRANDE).sinSectores()).isTrue();
    }

    @Test
    void dejarDeSeguirUnSectorQueNoSeSeguiaNoFalla() {
        SuscripcionTelegram suscripcion = SuscripcionTelegram.nueva(CHAT, AHORA).siguiendo(MANGA);

        assertThat(suscripcion.sinSeguir(BOCAGRANDE).sectorIds()).containsExactly(MANGA);
    }

    @Test
    void debeRechazarSeguirMasSectoresQueElMaximo() {
        SuscripcionTelegram suscripcion = SuscripcionTelegram.nueva(CHAT, AHORA);
        for (int i = 0; i < SuscripcionTelegram.MAXIMO_SECTORES; i++) {
            suscripcion = suscripcion.siguiendo(new SectorId("sector-" + i));
        }
        SuscripcionTelegram llena = suscripcion;

        assertThatThrownBy(() -> llena.siguiendo(MANGA)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void debeRechazarConstruirUnaSuscripcionConMasSectoresQueElMaximo() {
        List<SectorId> demasiados = IntStream.rangeClosed(0, SuscripcionTelegram.MAXIMO_SECTORES)
                .mapToObj(i -> new SectorId("sector-" + i)).toList();

        assertThatThrownBy(() -> new SuscripcionTelegram(CHAT, demasiados, AHORA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void debeRechazarUnaSuscripcionSinChatNiFecha() {
        assertThatThrownBy(() -> new SuscripcionTelegram(null, List.of(), AHORA)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SuscripcionTelegram(CHAT, List.of(), null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void elEstadoNuloSeDescribeComoSinDatosYNuncaComoConServicio() {
        assertThat(DescripcionDeEstado.describir(null)).contains("sin datos").doesNotContain("con servicio");
        assertThat(DescripcionDeEstado.describir(EstadoServicio.SIN_SERVICIO)).isEqualTo("sin servicio");
    }
}
