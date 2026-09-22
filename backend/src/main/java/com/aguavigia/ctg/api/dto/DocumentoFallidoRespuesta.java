package com.aguavigia.ctg.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/** RNF006 — un documento de la ingesta que sigue fallando, con su motivo (BUG-091). */
@Schema(description = "Documento de la ingesta que falló al procesarse y sigue en cola de reintento")
public record DocumentoFallidoRespuesta(

        @Schema(example = "acuacar")
        String fuente,

        String urlOriginal,

        @Schema(nullable = true)
        String titulo,

        @Schema(description = "Mensaje de la excepción que hizo fallar el procesamiento")
        String motivo,

        Instant primerIntento,

        Instant ultimoIntento,

        @Schema(description = "Veces que se reintentó sin éxito, una por ciclo de ingesta")
        int reintentos) {
}
