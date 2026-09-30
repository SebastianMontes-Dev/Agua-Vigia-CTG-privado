package com.aguavigia.ctg.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Cómo y cuándo terminó un corte en un barrio. {@code provisional} marca un cierre que solo
 * sostienen los vecinos o los sensores: un veedor o un boletín de Acuacar puede confirmarlo o
 * corregirlo después.
 */
public record CierreDeCorte(Instant hora, OrigenEstado fuente, boolean provisional) {

    public CierreDeCorte {
        Objects.requireNonNull(hora, "El cierre debe tener hora");
        Objects.requireNonNull(fuente, "El cierre debe declarar su fuente");
    }
}
