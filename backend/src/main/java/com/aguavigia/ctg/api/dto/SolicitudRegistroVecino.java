package com.aguavigia.ctg.api.dto;

import com.aguavigia.ctg.domain.ConsentimientosAceptados;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = """
        Registro de un vecino. Confirmar el correo activa la cuenta: no hay aprobacion de un administrador
        porque un vecino solo gestiona su propio perfil.""")
public record SolicitudRegistroVecino(

        @NotBlank @Email
        String correo,

        @NotBlank
        @Size(min = 2, max = 80)
        String nombre,


        @NotBlank
        @Schema(description = "Slug del barrio donde vives (uno de `GET /api/sectores`). 400 si no existe.",
                example = "manga")
        String barrioId,

        @NotNull @Valid
        Consentimiento consentimiento) {

    @Schema(description = """
            Las dos casillas son independientes: aceptar la privacidad es obligatorio para registrarse;
            `avisos` autoriza recibir avisos de cortes de tu barrio y por defecto es falso.""")
    public record Consentimiento(

            @Schema(description = "Acepta el aviso de privacidad vigente. Debe ser true.")
            boolean privacidad,

            @Schema(description = "Acepta recibir avisos de cortes de su barrio", defaultValue = "false")
            boolean avisos) {

        public ConsentimientosAceptados aDominio() {
            return new ConsentimientosAceptados(privacidad, avisos);
        }
    }
}
