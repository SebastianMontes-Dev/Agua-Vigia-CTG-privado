package com.aguavigia.ctg.domain;

import java.time.Instant;

/** Una fila de la bitácora de copias: cuándo corrió, cuánto copió y si los conteos cuadraron. Los conteos son nulos si no se llegaron a tomar. */
public record RegistroDeCopia(long id, Instant iniciadaEn, Instant terminadaEn, ResultadoDeCopia resultado,
                              ConteosAnaliticos enMongo, ConteosAnaliticos enSql, String detalle) {
}
