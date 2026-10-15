package com.aguavigia.ctg.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Un barrio afectado por un corte (par corte-barrio, D14). Sin {@code cierreHora} el barrio todavía no se restableció y no
 * cuenta en el Índice.
 */
public record FilaParAnalitica(String sectorId, Instant cierreHora, String cierreFuente, boolean cierreProvisional) {

    public FilaParAnalitica {
        Objects.requireNonNull(sectorId, "El par debe tener barrio");
        if (cierreHora == null && (cierreFuente != null || cierreProvisional)) {
            throw new IllegalArgumentException("Un barrio sin cierre no puede declarar fuente ni ser provisional");
        }
    }

    public boolean estaCerrado() {
        return cierreHora != null;
    }
}
