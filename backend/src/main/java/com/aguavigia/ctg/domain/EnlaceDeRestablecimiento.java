package com.aguavigia.ctg.domain;

import java.time.Instant;

/**
 * Lo que dice un enlace de «¿ya volvió el agua?»: de qué barrio es, quién lo recibió y hasta cuándo vale. Lo firma el
 * servidor, así que quien lo abre no puede cambiar ninguno de los tres.
 */
public record EnlaceDeRestablecimiento(SectorId sector, SuscripcionId suscripcion, Instant venceEn) {

    public EnlaceDeRestablecimiento {
        if (sector == null || suscripcion == null || venceEn == null) {
            throw new IllegalArgumentException("El enlace de restablecimiento debe declarar barrio, suscripción y vencimiento");
        }
    }
}
