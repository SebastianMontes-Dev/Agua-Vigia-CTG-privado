package com.aguavigia.ctg.domain;

import java.time.Instant;

/**
 * poblacion es nulable: 27 de 213 barrios de Cartagena no tienen dato censal (corregimientos
 * rurales/insulares que el DANE no cubre). Ver docs/ingenieria/modelo-de-dominio.md §3.1 —
 * un 0 sería indistinguible de un barrio real sin habitantes.
 *
 * RF003 — {@code estadoActualizadoEn} es cuándo se registró {@code estadoActual}, y es lo que
 * permite mostrar "actualizado hace X" junto a cada sector (DESIGN.md §6: un mapa congelado
 * mostrando datos viejos como actuales es peor que uno que admite que no sabe). Nulo mientras el
 * sector no tenga estado registrado.
 *
 * {@code estadoVerificadoEn} es la última vez que una fuente con autoridad —consenso de vecinos,
 * corte del veedor o boletín aprobado— sostuvo ese estado, haya cambiado o no (ADR-073). Un barrio con
 * servicio estable no cambia en días; sin este dato, la advertencia de «sin verificación reciente»
 * saldría sobre casi todo el mapa. Nunca es anterior al cambio: cambiar el estado es verificarlo.
 *
 * Quien escribe las dos marcas es {@code SectorMongoAdapter}, con {@link
 * com.aguavigia.ctg.domain.port.out.RelojPort}, y no {@link #conEstado(EstadoServicio)}: así hay un
 * solo reloj decidiendo la marca de tiempo de un cambio de estado. La consecuencia es que un Sector
 * recién derivado con {@code conEstado()} conserva en memoria las marcas anteriores hasta que se
 * guarda y se relee — el {@code SectorActualizadoEvent} que publica el adaptador sí viaja con las
 * nuevas, porque se construye desde el documento ya guardado.
 */
public record Sector(SectorId id, String nombre, Integer poblacion, EstadoServicio estadoActual,
                     Instant estadoActualizadoEn, Instant estadoVerificadoEn) {

    public Sector {
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("El nombre del sector no puede estar vacío");
        }
        if (poblacion != null && poblacion < 0) {
            throw new IllegalArgumentException("La población no puede ser negativa");
        }
        if (estadoActualizadoEn != null && estadoVerificadoEn != null
                && estadoVerificadoEn.isBefore(estadoActualizadoEn)) {
            throw new IllegalArgumentException("La verificación no puede ser anterior al cambio de estado");
        }
    }

    /** Sin verificación aparte: la última verificación conocida es el propio cambio de estado. */
    public Sector(SectorId id, String nombre, Integer poblacion, EstadoServicio estadoActual,
                  Instant estadoActualizadoEn) {
        this(id, nombre, poblacion, estadoActual, estadoActualizadoEn, estadoActualizadoEn);
    }

    /** Sector sin marca de tiempo conocida — la pone el adaptador al persistir el cambio de estado. */
    public Sector(SectorId id, String nombre, Integer poblacion, EstadoServicio estadoActual) {
        this(id, nombre, poblacion, estadoActual, null, null);
    }

    public Sector conEstado(EstadoServicio nuevoEstado) {
        return new Sector(id, nombre, poblacion, nuevoEstado, estadoActualizadoEn, estadoVerificadoEn);
    }

    /** Sin verificación conocida, cualquier instante es posterior: hay que verificar. */
    public boolean verificadoAntesDe(Instant instante) {
        return estadoVerificadoEn == null || estadoVerificadoEn.isBefore(instante);
    }
}
