package com.aguavigia.ctg.infrastructure.telegram;

import com.aguavigia.ctg.domain.port.in.AtenderTelegramUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * RF041 — pregunta a Telegram por mensajes nuevos cada pocos segundos. Sondeo corto (sin espera larga):
 * el planificador tiene un solo hilo y no debe bloquear al barrido del consenso. Solo corre con token, y
 * solo en una instancia (ver {@link TelegramApiAdapter}).
 */
@Component
@ConditionalOnExpression("!'${aguavigia.telegram.token:}'.isBlank()")
public class TelegramSondeoJob {

    private static final Logger log = LoggerFactory.getLogger(TelegramSondeoJob.class);

    private final AtenderTelegramUseCase atender;

    public TelegramSondeoJob(AtenderTelegramUseCase atender) {
        this.atender = atender;
    }

    @Scheduled(fixedDelayString = "${aguavigia.telegram.intervalo-ms:3000}")
    public void sondear() {
        try {
            atender.atender();
        } catch (RuntimeException fallo) {
            log.warn("No se pudieron leer los mensajes de Telegram: {}", fallo.toString());
        }
    }
}
