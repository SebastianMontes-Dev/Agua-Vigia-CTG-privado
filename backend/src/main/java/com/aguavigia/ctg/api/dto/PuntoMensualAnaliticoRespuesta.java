package com.aguavigia.ctg.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "El Índice de un mes, calculado por las vistas SQL de la copia analítica")
public record PuntoMensualAnaliticoRespuesta(
        @Schema(description = "Mes en hora de Cartagena, formato ISO 8601", example = "2026-08") String periodo,
        long paresCerrados,
        long duracionPrometidaSegundos,
        long duracionRealSegundos,
        double porcentajeCumplimiento) {
}
