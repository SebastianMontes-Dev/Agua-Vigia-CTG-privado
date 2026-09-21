package com.aguavigia.ctg.api.error;

/**
 * La ruta existe pero el servidor no está configurado para atenderla (p. ej. `/api/iot/presion` sin
 * `IOT_KEY`). La API lo responde 503. Vive en api/ y no en domain/: «503» es un concepto HTTP.
 */
public class ServicioNoDisponibleException extends RuntimeException {

    public ServicioNoDisponibleException(String mensaje) {
        super(mensaje);
    }
}
