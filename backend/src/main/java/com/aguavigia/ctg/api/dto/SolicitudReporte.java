package com.aguavigia.ctg.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

@Schema(description = """
        Reporte ciudadano (RF005-RF008). La identidad de quien reporta no va en el cuerpo: viaja en la
        cabecera `X-Dispositivo` (token de `POST /api/dispositivos`) o en la sesion de un vecino
        (`Authorization: Bearer`). Un campo `huella` en el cuerpo, como el de versiones anteriores, se ignora.""")
public record SolicitudReporte(

        @Schema(description = """
                Identificador del sector reportado. Opcional si viaja la coordenada: entonces el
                servidor infiere el barrio que la contiene (RF007). Si no viaja ninguno, 400.""",
                example = "bocagrande", nullable = true)
        String sectorId,

        @NotBlank
        @Schema(description = "SIN_AGUA, PRESION_BAJA o SERVICIO_RESTABLECIDO", example = "SIN_AGUA")
        String tipo,

        @Valid
        @Schema(description = """
                Opcional — solo si el usuario autorizo compartir su ubicacion (RF007). El servidor la usa para
                verificar el reporte y guarda solo una aproximacion de unos 110 m.""")
        CoordenadaDTO coordenada,

        @PositiveOrZero
        @Schema(description = """
                Precision de la coordenada en metros (`coords.accuracy` del navegador). Sin ella, o si es peor que
                200 m, la ubicacion no verifica el reporte.""",
                example = "25.5", nullable = true)
        Double precisionMetros) {
}
