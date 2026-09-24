package com.aguavigia.ctg.infrastructure.eventos;

import com.aguavigia.ctg.domain.Sector;

/**
 * Evento publicado cuando cambia el estado de un sector.
 * Usado para notificar a los clientes vía Server-Sent Events (SSE).
 */
public record SectorActualizadoEvent(Sector sector) {
}
