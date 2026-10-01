package com.aguavigia.ctg.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Credencial de un vecino registrado")
public record CredencialVecino(

        @NotBlank @Email
        @Schema(description = "Correo de la cuenta", example = "vecina@ejemplo.org")
        String correo,

        @NotBlank
        @Schema(description = "Clave de la cuenta")
        String clave) {
}
