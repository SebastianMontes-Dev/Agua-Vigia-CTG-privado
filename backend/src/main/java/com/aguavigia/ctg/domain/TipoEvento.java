package com.aguavigia.ctg.domain;

/** RF026 — eventos que la bitácora pública registra de forma inmutable. */
public enum TipoEvento {
    CORTE_ANUNCIADO,
    CORTE_CONFIRMADO_POR_CIUDADANOS,
    CORTE_RESTABLECIDO,
    CORTE_DETECTADO_POR_INGESTA,
    /** Nadie confirmó el restablecimiento a tiempo: el barrio vuelve a «sin datos». */
    CORTE_EXPIRADO,
    /** El corte se publicó por error; la bitácora solo se anexa, así que la corrección es un evento nuevo. */
    CORTE_ANULADO,
    /** Los vecinos confirmaron que volvió el agua antes de que lo hiciera una fuente oficial. */
    RESTABLECIMIENTO_POR_VECINOS,
    /** Un quórum de vecinos contradice a la fuente oficial. */
    ESTADO_EN_DISPUTA,
    /** Un estado que movió el mapa se cayó al descartar los reportes que lo sostenían. */
    CONSENSO_REVERTIDO
}
