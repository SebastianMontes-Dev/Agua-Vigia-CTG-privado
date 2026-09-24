package com.aguavigia.ctg.domain.port.out;

import com.aguavigia.ctg.domain.MensajeTelegram;

import java.util.List;

public interface RecepcionTelegramPort {

    /**
     * Los mensajes que las personas le escribieron al bot desde la última vez, en orden. Con Telegram
     * desactivado (sin token) siempre está vacío.
     */
    List<MensajeTelegram> recibirNuevos();
}
