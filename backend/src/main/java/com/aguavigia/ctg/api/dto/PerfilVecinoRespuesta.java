package com.aguavigia.ctg.api.dto;

import com.aguavigia.ctg.domain.Usuario;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * Nunca lleva `claveHash` ni datos del panel (segundo factor, permisos): es el perfil que el propio
 * vecino puede ver, y esos campos no le sirven ni deben salir de la API.
 */
@Schema(description = "Perfil del vecino con sesion")
public record PerfilVecinoRespuesta(
        String id,
        String correo,
        String nombre,

        @Schema(description = "ACTIVA mientras pueda iniciar sesion")
        String estado,

        @Schema(description = "Slug del barrio que declaro")
        String barrioId,

        @Schema(description = "true si probo con su ubicacion que vive en `barrioId`")
        boolean barrioVerificado,

        @Schema(nullable = true)
        Instant barrioVerificadoEn,

        @Schema(description = "true si acepto recibir avisos de cortes de su barrio")
        boolean recibeAvisos,

        @Schema(description = "Lo que acepto, con la version del texto y la fecha")
        List<ConsentimientoRespuesta> consentimientos,

        Instant creadoEn,
        Instant actualizadoEn) {

    public record ConsentimientoRespuesta(
            @Schema(description = "PRIVACIDAD o AVISOS") String tipo,
            String version,
            Instant fecha) {
    }

    public static PerfilVecinoRespuesta de(Usuario vecino) {
        return new PerfilVecinoRespuesta(
                vecino.id().valor(),
                vecino.correo().valor(),
                vecino.nombre(),
                vecino.estado().name(),
                vecino.barrio() == null ? null : vecino.barrio().valor(),
                vecino.barrioVerificado(),
                vecino.barrioVerificadoEn(),
                vecino.recibeAvisos(),
                vecino.consentimientos().stream()
                        .map(c -> new ConsentimientoRespuesta(c.tipo().name(), c.version(), c.fecha()))
                        .toList(),
                vecino.creadoEn(),
                vecino.actualizadoEn());
    }
}
