package com.aguavigia.ctg.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * Un corte con el cierre de cada barrio. Ya no hay un {@code finReal} único: un corte agrupa varios barrios y se
 * restablece barrio por barrio, cada uno con su hora, su fuente y si es provisional.
 */
@Schema(description = "Corte oficial (RF016-RF017)")
public record CorteRespuesta(
        String id,
        List<String> sectoresAfectados,
        Instant inicio,
        Instant finPrometido,
        String causa,
        String origen,
        @Schema(description = "ANUNCIADO, CONFIRMADO, RESTABLECIDO, EXPIRADO o ANULADO. RESTABLECIDO solo cuando todos "
                + "sus barrios tienen cierre; EXPIRADO nadie lo confirmó a tiempo y no cuenta en el Índice.")
        String estado,
        @Schema(description = "Un cierre por cada barrio ya restablecido; los pendientes no aparecen. Vacío mientras "
                + "ningún barrio se haya restablecido.")
        List<CierreRespuesta> cierres,
        @Schema(description = "Solo en un corte ANULADO: por qué se anuló", nullable = true)
        String motivoAnulacion,
        @Schema(description = "Solo en el corte del veedor, que es el override: desde esta hora deja de afirmar nada. "
                + "Nulo si dura hasta que alguien lo cierre o expire.", nullable = true)
        Instant caducaEn) {

    @Schema(description = "Cómo y cuándo se restableció un barrio dentro del corte")
    public record CierreRespuesta(
            String sectorId,
            Instant hora,
            @Schema(description = "Quién lo sostiene: VEEDOR, ACUACAR, VECINOS, SENSOR o PRENSA")
            String fuente,
            @Schema(description = "Un cierre que solo sostienen los vecinos o los sensores: un veedor o un boletín "
                    + "puede confirmarlo o corregirlo")
            boolean provisional) {
    }
}
