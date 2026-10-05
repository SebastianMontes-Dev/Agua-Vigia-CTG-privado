package com.aguavigia.ctg.domain;

/**
 * Una red (resumen diario de la IP, nunca la IP) que envió muchos reportes a un mismo barrio en poco tiempo. La señal es por
 * barrio: la misma red reportando en otro barrio no es una ráfaga allí.
 */
public record RedEnRafaga(SectorId sector, String redHash) {

    public RedEnRafaga {
        if (sector == null) {
            throw new IllegalArgumentException("La ráfaga debe tener barrio");
        }
        if (redHash == null || redHash.isBlank()) {
            throw new IllegalArgumentException("La ráfaga debe tener una red conocida");
        }
    }
}
