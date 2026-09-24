package com.aguavigia.ctg.domain;

/** El estado de un sector en una frase, para mensajes a personas (Telegram). El estado nulo es «sin datos», nunca «con servicio». */
public final class DescripcionDeEstado {

    private DescripcionDeEstado() {
    }

    public static String describir(EstadoServicio estado) {
        if (estado == null) {
            return "sin datos verificados todavía";
        }
        return switch (estado) {
            case CON_SERVICIO -> "con servicio";
            case SIN_SERVICIO -> "sin servicio";
            case PRESION_BAJA -> "con presión baja";
            case CORTE_PROGRAMADO -> "con un corte programado";
        };
    }
}
