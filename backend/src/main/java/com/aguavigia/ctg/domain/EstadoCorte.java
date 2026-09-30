package com.aguavigia.ctg.domain;

/**
 * Lo que está derivado del reloj (en curso, vencido por confirmar) no se guarda: se calcula al
 * resolver el estado del barrio, así que no hace falta ningún proceso que lo mantenga.
 */
public enum EstadoCorte {
    ANUNCIADO,
    CONFIRMADO,
    RESTABLECIDO,
    /** Nadie confirmó el restablecimiento a tiempo: sin hora real, el barrio vuelve a «sin datos». */
    EXPIRADO,
    /** Se publicó por error: queda como historia con su motivo, fuera del Índice y de las estadísticas. */
    ANULADO
}
