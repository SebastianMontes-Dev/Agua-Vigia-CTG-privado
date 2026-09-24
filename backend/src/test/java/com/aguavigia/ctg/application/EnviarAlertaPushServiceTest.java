package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.ChatTelegramId;
import com.aguavigia.ctg.domain.MensajeTelegram;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.SuscripcionTelegram;
import com.aguavigia.ctg.domain.port.in.ProcesarMensajeTelegramUseCase;
import com.aguavigia.ctg.domain.port.out.EnvioTelegramPort;
import com.aguavigia.ctg.domain.port.out.RecepcionTelegramPort;
import com.aguavigia.ctg.domain.port.out.SuscripcionTelegramRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EnviarAlertaPushServiceTest {

    private static final SectorId MANGA = new SectorId("manga");
    private static final Instant AHORA = Instant.parse("2026-09-24T15:00:00Z");
    private static final ChatTelegramId UNO = new ChatTelegramId(1L);
    private static final ChatTelegramId DOS = new ChatTelegramId(2L);

    private final SuscripcionTelegramRepository suscripciones = mock(SuscripcionTelegramRepository.class);
    private final EnvioTelegramPort envio = mock(EnvioTelegramPort.class);
    private final EnviarAlertaPushService servicio = new EnviarAlertaPushService(suscripciones, envio);

    private static SuscripcionTelegram sigue(ChatTelegramId chat) {
        return SuscripcionTelegram.nueva(chat, AHORA).siguiendo(MANGA);
    }

    @Test
    void avisaATodosLosChatsQueSiguenElSector() {
        when(suscripciones.buscarPorSector(MANGA)).thenReturn(List.of(sigue(UNO), sigue(DOS)));
        when(envio.enviar(any(), any())).thenReturn(true);

        servicio.enviar(MANGA, "Manga sin servicio");

        verify(envio).enviar(UNO, "Manga sin servicio");
        verify(envio).enviar(DOS, "Manga sin servicio");
        verify(suscripciones, never()).eliminar(any());
    }

    @Test
    void sinSuscriptoresNoEnviaNada() {
        when(suscripciones.buscarPorSector(MANGA)).thenReturn(List.of());

        servicio.enviar(MANGA, "Manga sin servicio");

        verify(envio, never()).enviar(any(), any());
    }

    @Test
    void elChatQueBloqueoAlBotSeDaDeBajaYLosDemasSiguenRecibiendo() {
        when(suscripciones.buscarPorSector(MANGA)).thenReturn(List.of(sigue(UNO), sigue(DOS)));
        when(envio.enviar(UNO, "aviso")).thenReturn(false);
        when(envio.enviar(DOS, "aviso")).thenReturn(true);

        servicio.enviar(MANGA, "aviso");

        verify(suscripciones).eliminar(UNO);
        verify(suscripciones, never()).eliminar(DOS);
        verify(envio).enviar(DOS, "aviso");
    }

    @Test
    void unFalloPasajeroNoDetieneLosDemasAvisosNiDaDeBajaAlChat() {
        when(suscripciones.buscarPorSector(MANGA)).thenReturn(List.of(sigue(UNO), sigue(DOS)));
        doThrow(new IllegalStateException("Telegram respondió 500")).when(envio).enviar(UNO, "aviso");
        when(envio.enviar(DOS, "aviso")).thenReturn(true);

        servicio.enviar(MANGA, "aviso");

        verify(envio).enviar(DOS, "aviso");
        verify(suscripciones, never()).eliminar(UNO);
    }

    @Test
    void atenderProcesaCadaMensajeYUnFalloNoDetieneElSiguiente() {
        RecepcionTelegramPort recepcion = mock(RecepcionTelegramPort.class);
        ProcesarMensajeTelegramUseCase procesar = mock(ProcesarMensajeTelegramUseCase.class);
        MensajeTelegram malo = new MensajeTelegram(UNO, "/suscribir manga");
        MensajeTelegram bueno = new MensajeTelegram(DOS, "/ayuda");
        when(recepcion.recibirNuevos()).thenReturn(List.of(malo, bueno));
        doThrow(new IllegalStateException("fallo")).when(procesar).procesar(malo);

        int atendidos = new AtenderTelegramService(recepcion, procesar).atender();

        assertThat(atendidos).isEqualTo(1);
        verify(procesar).procesar(bueno);
    }
}
