package com.aguavigia.ctg.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Anulación de un corte o de una propuesta de la ingesta que se publicó por error")
public record SolicitudAnulacion(

        @NotBlank
        @Schema(description = "Por qué se anula. Queda en la bitácora pública y en la auditoría.",
                example = "El boletín hablaba de otro barrio")
        String motivo) {
}
