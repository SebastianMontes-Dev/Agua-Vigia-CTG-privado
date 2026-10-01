package com.aguavigia.ctg.api.dto;

import com.aguavigia.ctg.domain.Permiso;
import com.aguavigia.ctg.domain.SesionEmitida;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Sesion de un vecino. Sirve en `/api/vecino/**`; del panel solo abre `GET /api/veedor/yo` y `POST /api/veedor/sesion/cierre`, que muestran o cierran lo propio.")
public record SesionVecino(

        @Schema(description = "Token JWT. Se envia como 'Authorization: Bearer <token>'")
        String token,

        String usuarioId,
        String nombre,
        String correo,

        @Schema(description = "Siempre `GESTIONAR_PERFIL_PROPIO`")
        List<String> permisos) {

    public static SesionVecino de(SesionEmitida sesion) {
        return new SesionVecino(
                sesion.token(),
                sesion.usuarioId().valor(),
                sesion.nombre(),
                sesion.correo().valor(),
                sesion.permisos().stream().map(Permiso::name).sorted().toList());
    }
}
