package com.aguavigia.ctg.domain;

/**
 * El enlace de «¿ya volvió el agua?» no sirve: no lo firmó este servidor, venció, es de otro barrio o quien lo recibió
 * ya no sigue los avisos. Es el mismo error en todos esos casos: distinguirlos le diría a quien prueba enlaces qué
 * suscripciones existen.
 */
public class EnlaceDeRestablecimientoInvalidoException extends RuntimeException {

    public EnlaceDeRestablecimientoInvalidoException(String mensaje) {
        super(mensaje);
    }
}
