package com.aguavigia.ctg.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Un boletín simulado, con la forma de la API de WordPress de Acuacar")
public record SolicitudBoletinSimulado(
        @Schema(description = "Identificador del boletín; si falta se deriva del contenido de la petición")
        Long id,

        @Schema(description = "Instante ISO-8601 con zona, o fecha y hora local de Cartagena sin zona (como WordPress); si falta, la hora del reloj de la simulación")
        @Size(max = 40) String fecha,

        @Schema(description = "Enlace original; si falta, https://simulacion.local/boletin/{id}")
        @Size(max = 500) String enlace,

        @NotBlank @Size(max = 300) String titulo,

        @Schema(description = "Contenido en HTML")
        @NotBlank @Size(max = 20000) String contenido,

        @Size(max = 500) String portada) {
}
