package com.aguavigia.ctg.domain;

import java.time.Instant;

/**
 * Una identidad pseudónima de quien reporta sin tener cuenta: no tiene nombre, correo ni perfil, solo
 * un id que el servidor firmó. Existe para contar el cupo de reportes y, más adelante, poder bloquear
 * a quien abusa sin saber quién es.
 */
public record Dispositivo(DispositivoId id, Instant creadoEn, Instant ultimoVisto) {

    public Dispositivo {
        if (id == null) {
            throw new IllegalArgumentException("El dispositivo debe tener id");
        }
        if (creadoEn == null || ultimoVisto == null) {
            throw new IllegalArgumentException("El dispositivo debe tener fecha de creación y de último uso");
        }
        if (ultimoVisto.isBefore(creadoEn)) {
            throw new IllegalArgumentException("Un dispositivo no puede haberse visto antes de existir");
        }
    }
}
