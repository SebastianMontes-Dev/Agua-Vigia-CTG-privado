package com.aguavigia.ctg.domain;

/**
 * Falta el token de subida, ya se usó, venció o es de otro reporte. Responde 403 y es el mismo error en todos esos
 * casos: distinguirlos diría qué ids de reporte existen, y los ids son públicos en la bitácora.
 */
public class SubidaNoAutorizadaException extends RuntimeException {

    public SubidaNoAutorizadaException(String mensaje) {
        super(mensaje);
    }
}
