package com.aguavigia.ctg.domain;

/**
 * La copia analítica en PostgreSQL no está activada, no responde o todavía no se hizo la primera copia. No es un error del
 * cliente ni del dominio: la API lo traduce a 503 y nunca lo suple leyendo de Mongo (mezclar fuentes ocultaría el problema).
 */
public class AnaliticaNoDisponibleException extends RuntimeException {

    public AnaliticaNoDisponibleException(String mensaje) {
        super(mensaje);
    }

    public AnaliticaNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
