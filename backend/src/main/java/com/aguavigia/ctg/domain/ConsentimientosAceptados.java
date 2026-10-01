package com.aguavigia.ctg.domain;

/**
 * Las dos casillas del registro de un vecino. Son aparte a propósito: aceptar el tratamiento de
 * datos no autoriza a mandar avisos, y el servidor sella la versión del texto vigente, no el cliente.
 */
public record ConsentimientosAceptados(boolean privacidad, boolean avisos) {
}
