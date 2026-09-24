package com.aguavigia.ctg.infrastructure.eventos;

import com.aguavigia.ctg.application.NotificarSuscripcionesService;
import com.aguavigia.ctg.domain.EstadoServicio;
import com.aguavigia.ctg.domain.Sector;
import com.aguavigia.ctg.domain.SectorId;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class NotificarSuscripcionesListenerTest {

    @Test
    void debeDelegarElSectorDelEventoAlCasoDeUso() {
        NotificarSuscripcionesService servicio = mock(NotificarSuscripcionesService.class);
        Sector sector = new Sector(new SectorId("manga"), "Manga", 5000,
                EstadoServicio.SIN_SERVICIO, Instant.parse("2026-08-09T20:00:00Z"));

        new NotificarSuscripcionesListener(servicio).alActualizarSector(new SectorActualizadoEvent(sector));

        verify(servicio).notificarCambioDeEstado(sector);
    }
}
