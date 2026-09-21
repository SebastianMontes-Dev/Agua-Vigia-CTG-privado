package com.aguavigia.ctg.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

/** GeoJSON `FeatureCollection` con un `Feature` por sector. El `id` del feature es el `id` del sector. */
@Schema(description = """
        Polígonos de todos los sectores en GeoJSON (application/geo+json). El `id` de cada Feature
        es el mismo identificador que usa el resto de la API, así que se puede unir con
        GET /api/sectores sin calcular nada.""")
public record GeometriaSectoresRespuesta(String type, List<Feature> features) {

    public static GeometriaSectoresRespuesta de(List<Feature> features) {
        return new GeometriaSectoresRespuesta("FeatureCollection", features);
    }

    public record Feature(String type, String id, Propiedades properties, Map<String, Object> geometry) {

        public static Feature de(String id, String nombre, Map<String, Object> geometry) {
            return new Feature("Feature", id, new Propiedades(nombre), geometry);
        }
    }

    public record Propiedades(String nombre) {
    }
}
