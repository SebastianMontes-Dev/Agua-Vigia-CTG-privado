package com.aguavigia.ctg.domain;

/**
 * La ubicación que mandó el vecino no cae dentro del barrio que declaró (o cae fuera de todo barrio).
 * No es un error de formato (400) sino que la petición está bien formada y no se puede cumplir: 422.
 */
public class UbicacionFueraDelBarrioException extends RuntimeException {

    public UbicacionFueraDelBarrioException(String mensaje) {
        super(mensaje);
    }
}
