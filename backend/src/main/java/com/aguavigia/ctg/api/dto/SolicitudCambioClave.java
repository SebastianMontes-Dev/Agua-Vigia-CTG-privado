package com.aguavigia.ctg.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Cambiar la propia clave con la sesión iniciada")
public record SolicitudCambioClave(
        @NotBlank
        @Schema(description = "La clave de hoy. Sin ella un token robado bastaría para cambiarla.")
        String claveActual,
        @NotBlank
        @Schema(description = "La nueva: de 12 a 128 caracteres y distinta de la actual.")
        String claveNueva) {
}
