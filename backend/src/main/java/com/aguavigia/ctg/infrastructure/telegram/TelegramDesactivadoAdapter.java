package com.aguavigia.ctg.infrastructure.telegram;

import com.aguavigia.ctg.domain.ChatTelegramId;
import com.aguavigia.ctg.domain.MensajeTelegram;
import com.aguavigia.ctg.domain.port.out.EnvioTelegramPort;
import com.aguavigia.ctg.domain.port.out.RecepcionTelegramPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * RF041 — Telegram armado pero sin conectar: falta {@code TELEGRAM_BOT_TOKEN}. No envía ni recibe nada,
 * y el resto de la plataforma funciona igual. Se activa con solo poner el token y reiniciar el backend.
 */
@Component
@ConditionalOnExpression("'${aguavigia.telegram.token:}'.isBlank()")
public class TelegramDesactivadoAdapter implements EnvioTelegramPort, RecepcionTelegramPort {

    private static final Logger log = LoggerFactory.getLogger(TelegramDesactivadoAdapter.class);

    public TelegramDesactivadoAdapter() {
        log.info("Telegram desactivado: falta TELEGRAM_BOT_TOKEN. Las alertas por Telegram (RF041) no se envían.");
    }

    @Override
    public boolean enviar(ChatTelegramId chat, String texto) {
        return true;
    }

    @Override
    public List<MensajeTelegram> recibirNuevos() {
        return List.of();
    }
}
