package com.aguavigia.ctg.domain;

/**
 * Un recurso que la URL identifica (un reporte, un corte, una propuesta, una cuenta) no existe.
 * La API lo responde 404. Extiende {@link IllegalArgumentException} a propósito: quien ya captura
 * la excepción genérica sigue funcionando igual, y solo el manejador global distingue el caso.
 *
 * NO es para un sector que llega dentro del cuerpo de un POST: ahí el recurso no está en la URL y
 * lo correcto es un 400 (la petición referencia algo inexistente).
 */
public class EntidadNoEncontradaException extends IllegalArgumentException {

    public EntidadNoEncontradaException(String mensaje) {
        super(mensaje);
    }
}
