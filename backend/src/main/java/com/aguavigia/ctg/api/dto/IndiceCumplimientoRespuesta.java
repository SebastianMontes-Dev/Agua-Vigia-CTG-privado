package com.aguavigia.ctg.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
        Índice de Cumplimiento (RF020-RF022): comparación explícita entre duración prometida y
        real, nunca un porcentaje aislado.""")
public record IndiceCumplimientoRespuesta(
        @Schema(description = "Nulo cuando el índice es por corte o global, no por sector")
        String sectorId,
        long duracionPrometidaSegundos,
        long duracionRealSegundos,
        @Schema(description = "duracionReal - duracionPrometida. Negativa si terminó antes de lo prometido")
        long desviacionSegundos,
        @Schema(description = "Capado en 100 cuando el corte termina antes o a tiempo")
        double porcentajeCumplimiento,
        @Schema(description = """
                De los cierres que sostienen este indice, que porcentaje (0 a 100) solo lo sostienen vecinos o sensores y un
                veedor o un boletin aun puede corregir. Publicalo junto al porcentaje: el numero es solo tan solido como esto.""")
        double porcentajeProvisional,
        @Schema(description = """
                Cortes cuya ventana prometida ya termino y en los que algun barrio no tiene cierre (incluidos los que expiraron
                sin confirmacion). No cuentan a favor ni en contra de Acuacar: se declaran.""")
        long cortesSinCierreConfirmado,
        @Schema(description = "Cortes publicados por error y retirados: no entran al indice") long cortesAnulados) {
}
