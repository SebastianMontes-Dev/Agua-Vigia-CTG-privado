package com.aguavigia.ctg.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

@Schema(description = """
        La ubicacion del momento. El servidor la compara con el barrio que declaraste y la descarta: no se
        guarda ni se audita; solo queda que el barrio quedo verificado.""")
public record SolicitudVerificacionBarrio(

        @NotNull @Valid
        CoordenadaDTO coordenada,

        @NotNull @PositiveOrZero
        @Schema(description = """
                Precision de la lectura en metros (`coords.accuracy` del navegador). Una precision peor que 200 m
                (ubicacion aproximada por red) no verifica y responde 422 `ubicacion-imprecisa`.""",
                example = "25.5")
        Double precisionMetros) {
}
