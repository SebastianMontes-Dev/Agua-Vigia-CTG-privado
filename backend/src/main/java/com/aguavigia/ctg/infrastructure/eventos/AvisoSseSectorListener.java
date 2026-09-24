package com.aguavigia.ctg.infrastructure.eventos;

import com.aguavigia.ctg.infrastructure.sse.SseSectoresBroadcaster;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * @Async porque este listener corre en el hilo que guardó el sector — el POST /api/reportes de
 * un vecino, o el ciclo de ingesta. Sin esto, ese vecino esperaba a que se recorrieran todos
 * los emisores conectados, con una consulta del listado completo por medio, antes de recibir su
 * 201. RNF002 exige confirmar un reporte en menos de un segundo.
 *
 * Publica en Redis en vez de difundir directo: SseSectoresBroadcaster.onMessage() es quien
 * empuja a los clientes, en TODAS las instancias suscritas — no solo en esta (estado-del-backend.md
 * #6.1, "SSE de una sola instancia").
 */
@Component
public class AvisoSseSectorListener {

    private final SseSectoresBroadcaster sseBroadcaster;

    public AvisoSseSectorListener(SseSectoresBroadcaster sseBroadcaster) {
        this.sseBroadcaster = sseBroadcaster;
    }

    @Async
    @EventListener
    public void onSectorActualizado(SectorActualizadoEvent event) {
        sseBroadcaster.notificarActualizacion();
    }
}
