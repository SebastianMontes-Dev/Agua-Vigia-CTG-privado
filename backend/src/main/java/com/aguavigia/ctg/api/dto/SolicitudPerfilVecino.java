package com.aguavigia.ctg.api.dto;

import com.aguavigia.ctg.domain.CambiosDePerfil;
import com.aguavigia.ctg.domain.SectorId;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "Cambios al perfil. Solo se aplica lo que viene; un campo ausente no se toca.")
public record SolicitudPerfilVecino(

        @Size(min = 2, max = 80)
        String nombre,

        @Schema(description = """
                Slug del barrio donde vives. Cambiarlo anula la verificacion de barrio anterior. 400 si no existe.""",
                example = "crespo", nullable = true)
        String barrioId,

        @Schema(description = "true acepta avisos de cortes de tu barrio; false retira ese consentimiento",
                nullable = true)
        Boolean recibirAvisos) {

    public CambiosDePerfil aDominio() {
        return new CambiosDePerfil(
                nombre,
                barrioId == null || barrioId.isBlank() ? null : new SectorId(barrioId.strip()),
                recibirAvisos);
    }
}
