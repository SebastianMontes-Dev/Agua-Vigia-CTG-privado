package com.aguavigia.ctg.api.dto;

import com.aguavigia.ctg.domain.EstadoServicio;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * Un sector tal como lo ve el mapa (M1). Replica a proposito la forma que el frontend declaraba
 * en frontend/src/types/tipos-dominio.ts (el frontend se retiro, ADR-048; el codigo sigue en la
 * etiqueta git pre-retiro-frontend), asi que cualquier cliente generado desde este contrato
 * encaja con esa forma.
 */
@Schema(description = "Sector de Cartagena con el estado conocido de su servicio de agua")
public record SectorRespuesta(

        @Schema(description = "Identificador estable del sector", example = "bocagrande")
        String id,

        @Schema(description = "Nombre del barrio segun el GeoJSON oficial", example = "BOCAGRANDE")
        String nombre,
        @Schema(description = """
                Habitantes según el censo. **Nulo cuando el barrio no tiene dato censal** (27 de los 211): no es 0,
                y no debe mostrarse como «0 habitantes».""", example = "12000", nullable = true)
        Integer poblacion,

        @Schema(description = """
                Estado conocido del servicio. **Nulo cuando no hay dato verificado**: no se asume
                CON_SERVICIO por omision, porque publicar servicio normal sin verificarlo es el
                falso positivo que el proyecto evita (ADR-014). Presentarlo como "sin datos".""",
                nullable = true)
        EstadoServicio estado,

        @Schema(description = "Cuando se registro ese estado. Nulo si el sector no tiene estado.",
                nullable = true)
        Instant actualizadoEn,

        @Schema(description = """
                Última vez que una fuente con autoridad (consenso de vecinos, corte del veedor o boletín
                aprobado) sostuvo ese estado, haya cambiado o no (ADR-073). Nunca anterior a
                `actualizadoEn`. Nulo si el sector no tiene estado. Confirmar sin cambiar no emite
                evento SSE: el valor se renueva al volver a pedir la lista.""",
                nullable = true)
        Instant verificadoEn) {
}
