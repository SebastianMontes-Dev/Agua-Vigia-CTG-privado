package com.aguavigia.ctg.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
        Lo que sostiene el Indice de Cumplimiento: cuantos cierres se midieron y cuantos cortes no se pudieron medir. Existe aunque
        no haya un solo cierre (el indice entonces responde 400): es lo que permite decir «sin datos suficientes» con cifras.""")
public record CalidadDelCumplimientoRespuesta(
        @Schema(description = "Pares corte-barrio con cierre que entran al indice") long cierresMedidos,
        @Schema(description = "De ellos, los que solo sostienen vecinos o sensores") long cierresProvisionales,
        @Schema(description = "cierresProvisionales sobre cierresMedidos, de 0 a 100") double porcentajeProvisional,
        @Schema(description = "Cortes ya vencidos con algun barrio sin cierre, incluidos los expirados") long cortesSinCierreConfirmado,
        @Schema(description = "Cortes retirados por error") long cortesAnulados) {
}
