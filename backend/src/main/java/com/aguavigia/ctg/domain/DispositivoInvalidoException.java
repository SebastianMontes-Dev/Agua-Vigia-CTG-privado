package com.aguavigia.ctg.domain;

/**
 * El token de dispositivo falta, no lo firmó este servidor o su dispositivo ya no existe (venció tras 12 meses
 * sin uso). Responde 401 `dispositivo-invalido`: el cliente debe pedir otro con `POST /api/dispositivos`.
 */
public class DispositivoInvalidoException extends RuntimeException {

    public DispositivoInvalidoException(String mensaje) {
        super(mensaje);
    }
}
