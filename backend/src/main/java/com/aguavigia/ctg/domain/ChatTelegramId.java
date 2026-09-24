package com.aguavigia.ctg.domain;

/**
 * RF041 — identificador de un chat privado de Telegram. Es un dato personal: se guarda solo mientras
 * la suscripción existe y se borra con la baja (RNF009).
 */
public record ChatTelegramId(long valor) {

    public ChatTelegramId {
        if (valor == 0) {
            throw new IllegalArgumentException("El identificador de un chat de Telegram no puede ser 0");
        }
    }
}
