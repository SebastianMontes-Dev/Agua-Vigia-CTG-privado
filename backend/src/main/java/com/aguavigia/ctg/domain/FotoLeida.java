package com.aguavigia.ctg.domain;

/** Una foto lista para servirse: sus bytes y su tipo, que sale de la extensión que el servidor guardó. */
public record FotoLeida(byte[] contenido, String tipo) {
}
