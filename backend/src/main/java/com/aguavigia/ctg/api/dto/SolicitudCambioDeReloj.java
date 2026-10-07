package com.aguavigia.ctg.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Mover el reloj de la simulación: se manda exactamente uno de los dos campos")
public record SolicitudCambioDeReloj(
        @Schema(description = "Fija el reloj en este instante (ISO-8601 con zona, p. ej. 2026-11-02T13:00:00Z)")
        Instant instante,

        @Schema(description = "Adelanta el reloj estos segundos (positivo, como mucho 366 días)")
        Long avanzarSegundos) {
}
