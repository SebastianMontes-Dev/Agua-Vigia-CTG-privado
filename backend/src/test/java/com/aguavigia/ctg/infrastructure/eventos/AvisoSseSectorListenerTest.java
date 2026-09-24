package com.aguavigia.ctg.infrastructure.eventos;

import com.aguavigia.ctg.domain.EstadoServicio;
import com.aguavigia.ctg.domain.Sector;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.infrastructure.sse.SseSectoresBroadcaster;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Backplane Redis (estado-del-backend.md #6.1): el listener solo le avisa al broadcaster para que
 * publique en Redis. Quién empuja a los clientes conectados se prueba en SseSectoresBroadcasterTest.
 */
class AvisoSseSectorListenerTest {

    @Test
    void debeNotificarAlBroadcasterCuandoUnSectorEsActualizado() {
        SseSectoresBroadcaster broadcaster = mock(SseSectoresBroadcaster.class);

        new AvisoSseSectorListener(broadcaster).onSectorActualizado(new SectorActualizadoEvent(
                new Sector(new SectorId("manga"), "MANGA", 5000, EstadoServicio.PRESION_BAJA)));

        verify(broadcaster).notificarActualizacion();
    }
}
