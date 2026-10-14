package com.aguavigia.ctg.domain;

/**
 * La ubicación llegó con una precisión peor que la mínima (por ejemplo, una ubicación aproximada por
 * red). No prueba nada sobre el barrio, así que no se evalúa: 422, y se puede reintentar con GPS.
 */
public class UbicacionImprecisaException extends RuntimeException {

    public UbicacionImprecisaException(String mensaje) {
        super(mensaje);
    }
}
