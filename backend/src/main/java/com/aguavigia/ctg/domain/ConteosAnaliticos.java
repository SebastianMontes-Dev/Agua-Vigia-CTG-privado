package com.aguavigia.ctg.domain;

/** Cuántos barrios, cortes y pares corte-barrio hay en un lado (Mongo o PostgreSQL): la comparación barata de cada copia. */
public record ConteosAnaliticos(int sectores, int cortes, int pares) {

    public ConteosAnaliticos {
        if (sectores < 0 || cortes < 0 || pares < 0) {
            throw new IllegalArgumentException("Los conteos no pueden ser negativos");
        }
    }
}
