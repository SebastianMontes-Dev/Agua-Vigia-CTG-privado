package com.aguavigia.ctg.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Identidad de dispositivo emitida por el servidor")
public record TokenDeDispositivoRespuesta(

        @Schema(description = "Se envia en la cabecera `X-Dispositivo` de `POST /api/reportes` y de `/confirmar`. Guardalo: perderlo es perder la identidad.")
        String token) {
}
