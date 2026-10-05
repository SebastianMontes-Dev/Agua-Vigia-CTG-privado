package com.aguavigia.ctg.api.dto;

import com.aguavigia.ctg.domain.Permiso;
import com.aguavigia.ctg.domain.Usuario;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * Nunca lleva `claveHash` ni `secretoTotp`. No es un olvido afortunado: son justamente los dos
 * campos por los que existe la regla de no exponer entidades de dominio en la API.
 */
@Schema(description = "Cuenta del panel, tal como la ve un ADMIN")
public record UsuarioRespuesta(
        String id,
        String correo,
        String nombre,

        @Schema(description = "PENDIENTE_VERIFICACION, PENDIENTE_APROBACION, INVITADA, ACTIVA, SUSPENDIDA o RECHAZADA")
        String estado,

        String rol,

        @Schema(description = "Slug del barrio donde vive la persona; nulo si no lo dio (ADR-081)", nullable = true)
        String barrioId,

        List<String> permisosEfectivos,
        List<String> permisosConcedidos,
        List<String> permisosRevocados,
        boolean segundoFactorActivo,
        Instant creadoEn,
        Instant actualizadoEn,

        @Schema(description = """
                Cuenta de vecino creada por el sistema para probar el volumen (ADR-094): no es una persona registrada y no puede
                iniciar sesion. El listado las incluye; esta marca permite ocultarlas.""")
        boolean sintetica) {

    public static UsuarioRespuesta de(Usuario usuario) {
        return new UsuarioRespuesta(
                usuario.id().valor(),
                usuario.correo().valor(),
                usuario.nombre(),
                usuario.estado().name(),
                usuario.permisos().rol().name(),
                usuario.barrio() == null ? null : usuario.barrio().valor(),
                aNombres(usuario.permisosEfectivos()),
                aNombres(usuario.permisos().concedidos()),
                aNombres(usuario.permisos().revocados()),
                usuario.tieneSegundoFactorConfirmado(),
                usuario.creadoEn(),
                usuario.actualizadoEn(),
                usuario.datosDeDemostracion());
    }

    private static List<String> aNombres(java.util.Set<Permiso> permisos) {
        return permisos.stream().map(Permiso::name).sorted().toList();
    }
}
