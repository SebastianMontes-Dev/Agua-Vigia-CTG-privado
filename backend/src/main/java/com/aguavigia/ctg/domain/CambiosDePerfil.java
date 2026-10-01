package com.aguavigia.ctg.domain;

/** Un campo nulo significa «no cambiar». `recibirAvisos` es la casilla de avisos de cortes. */
public record CambiosDePerfil(String nombre, SectorId barrio, Boolean recibirAvisos) {
}
