package com.aguavigia.ctg.domain;

import java.time.Instant;

/** El último reporte de una identidad en un barrio: lo único que necesita el contador de la ventana de consenso. */
public record VotoReciente(SectorId sector, HuellaDispositivo huella, Instant instante) {

    public VotoReciente {
        if (sector == null || huella == null || instante == null) {
            throw new IllegalArgumentException("Un voto reciente necesita barrio, huella e instante");
        }
    }
}
