package com.aguavigia.ctg.domain;

import com.aguavigia.ctg.domain.Afirmacion.QuorumVecinos;

import java.time.Instant;
import java.util.Optional;

/**
 * El quórum a 30 minutos deja de verse pronto, pero el estado que produjo no: el barrio lo recuerda con su origen y su respaldo, y
 * solo caduca si ningún reporte nuevo lo renueva (6 h sin verificación, 24 h sin datos).
 */
public final class MemoriaDelBarrio {

    private MemoriaDelBarrio() {
    }

    /** Lo que los vecinos del barrio sostenían la última vez que se miró; vacío si el estado no es de ellos o no se guardó su respaldo. */
    public static Optional<QuorumVecinos> recordado(Sector sector) {
        MarcasDeEstado marcas = sector.marcas();
        if (sector.estadoActual() == null || !OrigenEstado.votan(marcas.origen()) || marcas.respaldo() == null) {
            return Optional.empty();
        }
        Instant ultimo = sector.estadoVerificadoEn() != null ? sector.estadoVerificadoEn() : sector.estadoActualizadoEn();
        if (ultimo == null) {
            ultimo = Instant.EPOCH;
        }
        Instant primero = sector.estadoActualizadoEn() != null ? sector.estadoActualizadoEn() : ultimo;
        return Optional.of(new QuorumVecinos(tipoDe(sector.estadoActual()), marcas.respaldo().vecinos(),
                marcas.respaldo().umbral(), true, primero, ultimo, true));
    }

    public static TipoReporte tipoDe(EstadoServicio estado) {
        return switch (estado) {
            case SIN_SERVICIO, CORTE_PROGRAMADO -> TipoReporte.SIN_AGUA;
            case PRESION_BAJA -> TipoReporte.PRESION_BAJA;
            case CON_SERVICIO -> TipoReporte.SERVICIO_RESTABLECIDO;
        };
    }

    /**
     * Cuántos vecinos válidos hacen falta, al descartar reportes, para que lo recordado siga en pie: el mismo listón con que se fijó,
     * es decir, el umbral que se guardó, o el reducido si lo que se confirmó fue un restablecimiento.
     */
    public static int liston(QuorumVecinos recordada, ReglasDeEstado reglas) {
        return recordada.estado() == EstadoServicio.CON_SERVICIO
                ? reglas.quorumReducido(recordada.umbral())
                : recordada.umbral();
    }
}
