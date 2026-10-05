package com.aguavigia.ctg.domain;

import java.time.Instant;

/**
 * Una fila inmutable de "quién le hizo qué a quién y cuándo". No tiene métodos de cambio a
 * propósito: una auditoría que se puede editar no es una auditoría.
 *
 * `venceEn` es la fecha en que Mongo borra el evento (índice TTL); nulo significa que se conserva. Solo la lleva la
 * auditoría de la ciudadanía: la del panel es la evidencia de quién hizo qué.
 *
 * `autorId` es nulo cuando el actor no es una cuenta —el propio sistema al sembrar el primer
 * admin, o alguien que aún no se ha autenticado al registrarse—; `detalle` guarda ahí el texto
 * que explica el origen.
 */
public record EventoAuditoria(
        AuditoriaId id,
        AccionAuditada accion,
        UsuarioId autorId,
        String autorCorreo,
        UsuarioId sujetoId,
        String sujetoCorreo,
        String detalle,
        String ip,
        Instant ocurrioEn,
        Instant venceEn) {

    /** Un evento que se conserva: sin fecha de vencimiento. */
    public EventoAuditoria(AuditoriaId id, AccionAuditada accion, UsuarioId autorId, String autorCorreo,
                           UsuarioId sujetoId, String sujetoCorreo, String detalle, String ip, Instant ocurrioEn) {
        this(id, accion, autorId, autorCorreo, sujetoId, sujetoCorreo, detalle, ip, ocurrioEn, null);
    }

    public EventoAuditoria {
        if (id == null) {
            throw new IllegalArgumentException("El evento de auditoría debe tener id");
        }
        if (accion == null) {
            throw new IllegalArgumentException("El evento de auditoría debe tener una acción");
        }
        if (ocurrioEn == null) {
            throw new IllegalArgumentException("El evento de auditoría debe tener un instante");
        }
    }
}
