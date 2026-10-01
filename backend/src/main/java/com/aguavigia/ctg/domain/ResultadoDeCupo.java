package com.aguavigia.ctg.domain;

import java.time.Duration;

/**
 * Respuesta de un cupo: si todavía había y, cuando no, cuánto falta para que se renueve (para fijar
 * `Retry-After`). `hastaRenovar` es nulo cuando se concedió.
 */
public record ResultadoDeCupo(boolean concedido, Duration hastaRenovar) {

    public ResultadoDeCupo {
        if (!concedido && (hastaRenovar == null || hastaRenovar.isNegative())) {
            throw new IllegalArgumentException("Un cupo agotado necesita saber cuánto falta para renovarse");
        }
    }

    public static ResultadoDeCupo conCupo() {
        return new ResultadoDeCupo(true, null);
    }

    public static ResultadoDeCupo agotado(Duration hastaRenovar) {
        return new ResultadoDeCupo(false, hastaRenovar);
    }
}
