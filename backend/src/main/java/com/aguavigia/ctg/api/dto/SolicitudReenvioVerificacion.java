package com.aguavigia.ctg.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Pedir de nuevo el correo de verificación. Responde siempre 202, exista o no la cuenta.")
public record SolicitudReenvioVerificacion(
        @NotBlank @Email
        String correo) {
}
