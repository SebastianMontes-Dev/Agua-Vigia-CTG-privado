package com.aguavigia.ctg.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Reporte ciudadano en la cola de moderación del veedor (RF018)")
public record ReporteModeracionRespuesta(
        String id,
        String sectorId,
        String tipo,
        @Schema(description = "Nulo si el usuario no autorizó compartir su ubicación (RF007). Es una aproximación de unos 110 m, no la ubicación exacta")
        CoordenadaDTO coordenada,
        Instant timestamp,
        @Schema(description = "PENDIENTE, APROBADO o DESCARTADO")
        String estadoModeracion,
        @Schema(description = "CUENTA_VERIFICADA, UBICACION_VERIFICADA o NINGUNA: cuánto respalda el servidor que quien reporta está en el barrio")
        String verificacion) {
}
