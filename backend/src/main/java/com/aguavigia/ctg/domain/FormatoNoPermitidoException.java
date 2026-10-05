package com.aguavigia.ctg.domain;

/**
 * El tipo de archivo no es de los que este servidor sabe limpiar (JPEG y PNG). Responde 415, no 400, para que el
 * cliente distinga «formato que no acepto» de «petición mal formada» y no ofrezca WebP.
 */
public class FormatoNoPermitidoException extends RuntimeException {

    public FormatoNoPermitidoException(String mensaje) {
        super(mensaje);
    }
}
