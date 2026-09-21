package com.aguavigia.ctg.domain;

import java.util.Map;

/**
 * El polígono de un sector tal como está sembrado: un GeoJSON `Polygon` o `MultiPolygon`
 * (`zona-industrial` es el único MultiPolygon). Se guarda como mapa y no como un tipo geométrico propio
 * porque el dominio no calcula nada con él; solo lo entrega a quien dibuja el mapa.
 */
public record GeometriaSector(SectorId id, String nombre, Map<String, Object> geometria) {

    public GeometriaSector {
        if (id == null) {
            throw new IllegalArgumentException("La geometría necesita el id del sector");
        }
        if (geometria == null || geometria.isEmpty()) {
            throw new IllegalArgumentException("La geometría no puede estar vacía");
        }
    }
}
