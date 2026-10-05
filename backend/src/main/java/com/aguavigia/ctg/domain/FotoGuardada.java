package com.aguavigia.ctg.domain;

/** Lo que devuelve el almacén tras guardar una foto: dónde se sirve y el SHA-256 de lo que quedó en disco. */
public record FotoGuardada(String url, String sha256) {
}
