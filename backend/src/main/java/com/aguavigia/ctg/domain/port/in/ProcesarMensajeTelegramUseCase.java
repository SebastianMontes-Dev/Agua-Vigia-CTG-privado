package com.aguavigia.ctg.domain.port.in;

import com.aguavigia.ctg.domain.MensajeTelegram;

public interface ProcesarMensajeTelegramUseCase {

    /** Interpreta el comando del mensaje (suscribir, baja, estado…) y responde en el mismo chat. */
    void procesar(MensajeTelegram mensaje);
}
