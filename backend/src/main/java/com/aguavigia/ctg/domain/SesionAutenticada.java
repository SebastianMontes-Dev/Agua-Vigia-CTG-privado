package com.aguavigia.ctg.domain;


import java.time.Instant;
import java.util.Set;

/**
 * Lo que el filtro extrae de un token válido y deja como principal de Spring Security. Que sea un
 * tipo propio y no un Map evita el patrón de leer claims por nombre en cada controlador, que es
 * como una clave mal escrita acaba devolviendo null sin que nadie se entere.
 */
public record SesionAutenticada(
        String usuarioId,
        String correo,
        String nombre,
        String rol,
        Set<Permiso> permisos,
        AlcanceSesion alcance,
        Instant emitidoEn) {

    public SesionAutenticada {
        permisos = permisos == null ? Set.of() : Set.copyOf(permisos);
    }

    public UsuarioId id() {
        return new UsuarioId(usuarioId);
    }
}
