package com.aguavigia.ctg.domain;

import java.time.Instant;

/**
 * RNF007 — lo que se expone de cada colector de la ingesta: última ejecución exitosa, ítems procesados y tasa de error.
 * `fallosConsecutivos` es lo que decide si se reporta caído: una tasa acumulada del 20 % puede ser un colector sano
 * que tuvo un mal día hace un mes, mientras que tres ciclos seguidos fallando es un problema ahora.
 */
public record SaludDeColector(
        String nombre,
        Instant ultimaEjecucionExitosa,
        Instant ultimoFallo,
        String motivoDelUltimoFallo,
        long itemsProcesados,
        double tasaDeError,
        int fallosConsecutivos) {
}
