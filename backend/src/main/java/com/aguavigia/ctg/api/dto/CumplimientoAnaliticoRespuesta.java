package com.aguavigia.ctg.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = """
        Índice de Cumplimiento calculado por las vistas SQL de la copia analítica (PostgreSQL, ADR-100). Misma fórmula que
        GET /api/cumplimiento (ADR-022): se suman duraciones, no se promedian porcentajes. Va hasta 5 minutos por detrás de MongoDB.""")
public record CumplimientoAnaliticoRespuesta(
        @Schema(description = "Nulo en el índice global de la ciudad") String sectorId,
        @Schema(description = "Nulo en el índice global de la ciudad") String nombre,
        @Schema(description = "Pares corte-barrio ya restablecidos que sostienen el índice. Con 0, el 100 % no dice nada: no hay nada que medir")
        long paresCerrados,
        long duracionPrometidaSegundos,
        long duracionRealSegundos,
        @Schema(description = "duracionReal - duracionPrometida. Negativa si terminó antes de lo prometido")
        long desviacionSegundos,
        @Schema(description = "Capado en 100 cuando los cortes terminan antes o a tiempo")
        double porcentajeCumplimiento,
        @Schema(description = "Cuándo terminó la última copia que cuadró con MongoDB. Nulo si todavía no hubo ninguna")
        Instant copiadoEn) {
}
