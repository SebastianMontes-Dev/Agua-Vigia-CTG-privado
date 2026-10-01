package com.aguavigia.ctg.domain;

/** Identificador opaco de un dispositivo: lo genera el servidor y solo viaja dentro de un token firmado. */
public record DispositivoId(String valor) {

    public DispositivoId {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("El dispositivo debe tener un identificador");
        }
    }
}
