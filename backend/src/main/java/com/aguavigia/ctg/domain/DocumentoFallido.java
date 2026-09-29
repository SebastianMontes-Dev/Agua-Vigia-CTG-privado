package com.aguavigia.ctg.domain;

import java.time.Instant;

/**
 * BUG-091 / RNF006 — un documento de la ingesta que sigue fallando al procesarse, con su motivo y cuántas veces se
 * reintentó. Es lo que sigue roto *ahora*, no un histórico: sale de la lista en cuanto se procesa con éxito.
 */
public record DocumentoFallido(
        String fuente,
        String urlOriginal,
        String titulo,
        String motivo,
        Instant primerIntento,
        Instant ultimoIntento,
        int reintentos) {
}
