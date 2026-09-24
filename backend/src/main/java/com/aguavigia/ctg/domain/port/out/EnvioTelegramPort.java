package com.aguavigia.ctg.domain.port.out;

import com.aguavigia.ctg.domain.ChatTelegramId;

public interface EnvioTelegramPort {

    /**
     * Devuelve {@code false} cuando el destinatario es inalcanzable de forma definitiva (bloqueó al bot o el
     * chat ya no existe): quien llama debe darlo de baja. Un fallo pasajero (red, Telegram caído) lanza una
     * excepción, y la suscripción se conserva.
     */
    boolean enviar(ChatTelegramId chat, String texto);
}
