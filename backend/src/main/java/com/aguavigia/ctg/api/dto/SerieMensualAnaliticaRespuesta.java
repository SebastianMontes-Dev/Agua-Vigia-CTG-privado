package com.aguavigia.ctg.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = "La serie mensual del Índice según la copia analítica, de más antiguo a más reciente")
public record SerieMensualAnaliticaRespuesta(
        @Schema(description = "Cuándo terminó la última copia que cuadró con MongoDB. Nulo si todavía no hubo ninguna")
        Instant copiadoEn,
        List<PuntoMensualAnaliticoRespuesta> puntos) {
}
