package com.aguavigia.ctg.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Una copia de MongoDB a la base analítica: cuándo corrió y si los conteos de las dos bases cuadraron")
public record CopiaAnaliticaRespuesta(
        long id,
        Instant iniciadaEn,
        @Schema(description = "Nulo si la copia sigue en curso o el backend se detuvo a mitad de ella") Instant terminadaEn,
        @Schema(description = "EN_CURSO, OK, CON_DIFERENCIAS o FALLIDA") String resultado,
        @Schema(description = "Nulo si la copia no llegó a leer MongoDB") ConteosAnaliticosRespuesta enMongo,
        @Schema(description = "Nulo si la copia no llegó a escribir en PostgreSQL") ConteosAnaliticosRespuesta enSql,
        @Schema(description = "Qué no cuadró o por qué falló. Nulo si todo salió bien") String detalle) {
}
