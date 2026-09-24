package com.aguavigia.ctg.infrastructure.eventos;

import com.aguavigia.ctg.application.NotificarSuscripcionesService;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/** Único suscriptor de {@link SectorActualizadoEvent} que manda correo. */
@Component
public class NotificarSuscripcionesListener {

    private final NotificarSuscripcionesService notificarSuscripciones;

    public NotificarSuscripcionesListener(NotificarSuscripcionesService notificarSuscripciones) {
        this.notificarSuscripciones = notificarSuscripciones;
    }

    @Async
    @EventListener
    public void alActualizarSector(SectorActualizadoEvent event) {
        notificarSuscripciones.notificarCambioDeEstado(event.sector());
    }
}
