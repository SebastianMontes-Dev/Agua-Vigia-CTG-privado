package com.aguavigia.ctg.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Cuántos barrios, cortes y pares corte-barrio hay en una de las dos bases")
public record ConteosAnaliticosRespuesta(int sectores, int cortes, int pares) {
}
