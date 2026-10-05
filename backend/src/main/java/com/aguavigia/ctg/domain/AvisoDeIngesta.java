package com.aguavigia.ctg.domain;

import java.time.Instant;
import java.util.List;

/**
 * Lo que dice una zona de un boletín: un estado, una ventana y los sectores que toca. Es la unidad sobre la que se
 * deciden las compuertas de publicación (D5): «demasiados barrios» o «un nombre ambiguo» solo se ven con la zona entera,
 * no sector por sector.
 *
 * @param aliasAmbiguo si algún nombre del boletín casa con más de un sector del catálogo y nadie lo resolvió
 * @param sectoresDelBoletin cuántos sectores distintos toca el boletín entero, sumadas todas sus zonas: la compuerta
 *                           de «demasiados barrios» mira este total y no solo los de la zona
 */
public record AvisoDeIngesta(List<SectorId> sectores, EstadoServicio estadoPropuesto, String fuente, String urlOriginal,
                             String citaTextual, double confianza, Instant inicioDeclarado, Instant finPrometido,
                             String imagenUrl, Instant publicadoEn, String tituloOriginal, boolean aliasAmbiguo,
                             int sectoresDelBoletin) {

    /** Un aviso que es todo el boletín: sus sectores son los del boletín. */
    public AvisoDeIngesta(List<SectorId> sectores, EstadoServicio estadoPropuesto, String fuente, String urlOriginal,
                          String citaTextual, double confianza, Instant inicioDeclarado, Instant finPrometido,
                          String imagenUrl, Instant publicadoEn, String tituloOriginal, boolean aliasAmbiguo) {
        this(sectores, estadoPropuesto, fuente, urlOriginal, citaTextual, confianza, inicioDeclarado, finPrometido,
                imagenUrl, publicadoEn, tituloOriginal, aliasAmbiguo, sectores == null ? 0 : sectores.size());
    }

    public AvisoDeIngesta {
        if (sectores == null) {
            throw new IllegalArgumentException("El aviso debe traer la lista de sectores");
        }
        sectores = List.copyOf(sectores);
    }
}
