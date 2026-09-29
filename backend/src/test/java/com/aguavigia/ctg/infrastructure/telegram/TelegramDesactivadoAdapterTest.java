package com.aguavigia.ctg.infrastructure.telegram;

import com.aguavigia.ctg.domain.ChatTelegramId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramDesactivadoAdapterTest {

    private final TelegramDesactivadoAdapter adaptador = new TelegramDesactivadoAdapter();

    /** Devolver `true` evita que el servicio dé por caída una suscripción solo porque el bot no está conectado. */
    @Test
    void enviarNoDebeFallarNiDarPorCaidoElChat() {
        assertThat(adaptador.enviar(new ChatTelegramId(42L), "hola")).isTrue();
    }

    @Test
    void sinTokenNoDebeHaberMensajesNuevos() {
        assertThat(adaptador.recibirNuevos()).isEmpty();
    }
}
