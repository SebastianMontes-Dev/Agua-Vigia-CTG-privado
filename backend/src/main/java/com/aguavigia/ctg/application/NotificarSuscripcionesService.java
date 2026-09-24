package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.Sector;
import com.aguavigia.ctg.domain.Suscripcion;
import com.aguavigia.ctg.domain.port.out.NotificacionPort;
import com.aguavigia.ctg.domain.port.out.SuscripcionRepository;

import java.util.List;

public class NotificarSuscripcionesService {

    private final SuscripcionRepository suscripciones;
    private final NotificacionPort notificacionPort;

    public NotificarSuscripcionesService(SuscripcionRepository suscripciones, NotificacionPort notificacionPort) {
        this.suscripciones = suscripciones;
        this.notificacionPort = notificacionPort;
    }

    public void notificarCambioDeEstado(Sector sector) {
        List<Suscripcion> confirmadas = suscripciones.buscarConfirmadasPorSector(sector.id());
        for (Suscripcion sub : confirmadas) {
            notificacionPort.avisarCambioDeEstado(sub, sector);
        }
    }
}
