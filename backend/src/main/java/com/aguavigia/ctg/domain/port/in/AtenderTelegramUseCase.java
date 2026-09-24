package com.aguavigia.ctg.domain.port.in;

public interface AtenderTelegramUseCase {

    /** Recoge los mensajes nuevos del bot y los procesa. Devuelve cuántos atendió. */
    int atender();
}
