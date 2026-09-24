package com.aguavigia.ctg.domain;

/** RF041 — un mensaje de texto que una persona le escribió al bot en un chat privado. */
public record MensajeTelegram(ChatTelegramId chat, String texto) {

    public MensajeTelegram {
        if (chat == null) {
            throw new IllegalArgumentException("El mensaje debe venir de un chat");
        }
        texto = texto == null ? "" : texto.strip();
    }
}
