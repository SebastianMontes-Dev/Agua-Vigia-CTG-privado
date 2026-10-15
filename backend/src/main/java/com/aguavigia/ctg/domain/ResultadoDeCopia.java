package com.aguavigia.ctg.domain;

/** Cómo terminó una copia de Mongo a la base analítica. */
public enum ResultadoDeCopia {
    EN_CURSO,
    /** Los conteos de las dos bases cuadran. */
    OK,
    /** Se copió, pero los conteos no cuadran (p. ej. un corte apunta a un barrio que no existe). */
    CON_DIFERENCIAS,
    /** No se pudo copiar: PostgreSQL volvió atrás y quedó la copia anterior entera. */
    FALLIDA
}
