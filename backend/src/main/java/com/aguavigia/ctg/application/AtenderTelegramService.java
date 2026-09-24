package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.MensajeTelegram;
import com.aguavigia.ctg.domain.port.in.AtenderTelegramUseCase;
import com.aguavigia.ctg.domain.port.in.ProcesarMensajeTelegramUseCase;
import com.aguavigia.ctg.domain.port.out.RecepcionTelegramPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** RF041 — un mensaje que falla no detiene a los demás: se registra y se sigue con el siguiente. */
public class AtenderTelegramService implements AtenderTelegramUseCase {

    private static final Logger log = LoggerFactory.getLogger(AtenderTelegramService.class);

    private final RecepcionTelegramPort recepcion;
    private final ProcesarMensajeTelegramUseCase procesar;

    public AtenderTelegramService(RecepcionTelegramPort recepcion, ProcesarMensajeTelegramUseCase procesar) {
        this.recepcion = recepcion;
        this.procesar = procesar;
    }

    @Override
    public int atender() {
        int atendidos = 0;
        for (MensajeTelegram mensaje : recepcion.recibirNuevos()) {
            try {
                procesar.procesar(mensaje);
                atendidos++;
            } catch (RuntimeException fallo) {
                log.warn("No se pudo atender un mensaje de Telegram: {}", fallo.toString());
            }
        }
        return atendidos;
    }
}
