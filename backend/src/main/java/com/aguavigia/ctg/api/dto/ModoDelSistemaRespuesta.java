package com.aguavigia.ctg.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Qué instancia es esta y cuánto de lo que contiene es sintético")
public record ModoDelSistemaRespuesta(
        @Schema(description = "REAL o SIMULACION") String modo,
        @Schema(description = "Cuentas de vecino creadas por el sistema para probar el volumen; no son personas registradas")
        long cuentasSinteticas) {
}
