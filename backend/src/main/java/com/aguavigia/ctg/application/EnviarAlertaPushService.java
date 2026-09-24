package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.SuscripcionTelegram;
import com.aguavigia.ctg.domain.port.in.EnviarAlertaPushUseCase;
import com.aguavigia.ctg.domain.port.out.EnvioTelegramPort;
import com.aguavigia.ctg.domain.port.out.SuscripcionTelegramRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RF041 — avisa por Telegram a quien sigue el sector. Un chat que falla no impide avisar a los demás, y
 * el que bloqueó al bot se da de baja para no insistir (RNF009: se borra su id de chat).
 */
public class EnviarAlertaPushService implements EnviarAlertaPushUseCase {

    private static final Logger log = LoggerFactory.getLogger(EnviarAlertaPushService.class);

    private final SuscripcionTelegramRepository suscripciones;
    private final EnvioTelegramPort envio;

    public EnviarAlertaPushService(SuscripcionTelegramRepository suscripciones, EnvioTelegramPort envio) {
        this.suscripciones = suscripciones;
        this.envio = envio;
    }

    @Override
    public void enviar(SectorId sectorId, String mensaje) {
        for (SuscripcionTelegram suscripcion : suscripciones.buscarPorSector(sectorId)) {
            try {
                if (!envio.enviar(suscripcion.chat(), mensaje)) {
                    suscripciones.eliminar(suscripcion.chat());
                }
            } catch (RuntimeException fallo) {
                log.warn("No se pudo avisar por Telegram del sector '{}': {}", sectorId.valor(), fallo.toString());
            }
        }
    }
}
