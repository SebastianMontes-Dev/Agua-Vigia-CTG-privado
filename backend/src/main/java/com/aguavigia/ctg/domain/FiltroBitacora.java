package com.aguavigia.ctg.domain;

import java.time.Instant;

/**
 * Qué eventos de la bitácora pide el cliente. Cada campo nulo significa «sin restricción». El rango es
 * semiabierto: {@code desde} inclusivo y {@code hasta} exclusivo, para que dos días seguidos no
 * compartan el evento de la medianoche.
 */
public record FiltroBitacora(SectorId sectorId, TipoEvento tipo, Instant desde, Instant hasta) {

    public FiltroBitacora {
        if (desde != null && hasta != null && !hasta.isAfter(desde)) {
            throw new IllegalArgumentException("'hasta' debe ser posterior a 'desde'");
        }
    }

    public static FiltroBitacora sinFiltro() {
        return new FiltroBitacora(null, null, null, null);
    }
}
