package com.aguavigia.ctg.api.dto;

import com.aguavigia.ctg.domain.EstadoServicio;
import com.aguavigia.ctg.domain.OrigenEstado;
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
        Instant verificadoEn,

        @Schema(description = """
                Quién sostiene el estado: ACUACAR (boletín oficial), PRENSA (nota aprobada por el veedor),
                VEEDOR (corte o cierre del veedor), VECINOS (quórum de reportes) o SENSOR. Nulo si el sector
                no tiene estado.""", nullable = true)
        OrigenEstado origen,

        @Schema(description = """
                La ventana que prometió la fuente oficial, cuando el estado sale de una. Permite mostrar
                «Acuacar prometió hasta…». Nulo si el estado no sale de una ventana.""", nullable = true)
        VentanaPrometidaRespuesta ventanaPrometida,

        @Schema(description = """
                La promesa ya venció y nadie confirmó que volvió el agua: el barrio sigue como estaba, pero
                por confirmar. Un restablecimiento pide menos vecinos que reportar una avería.""")
        boolean restablecimientoPorConfirmar,

        @Schema(description = """
                Un quórum de vecinos contradice a la fuente oficial. El color no cambia: la contradicción
                se muestra como una insignia y llega a la cola del veedor.""")
        boolean enDisputa,

        @Schema(description = "Cuántos vecinos sostienen esa contradicción. 0 si no hay disputa.")
        int reportesEnContra,

        @Schema(description = """
                Los vecinos que sostienen el estado y cuántos hacían falta («11 de 12»). Nulo si el estado
                no sale de los vecinos. La afectación parcial de un barrio no se modela: un barrio tiene un
                solo estado.""", nullable = true)
        RespaldoVecinalRespuesta respaldo) {

    @Schema(description = "Desde cuándo y hasta cuándo prometió la fuente oficial la afectación")
    public record VentanaPrometidaRespuesta(Instant inicio, Instant fin) {
    }

    @Schema(description = "Cuántos vecinos respaldan un estado y cuántos hacían falta")
    public record RespaldoVecinalRespuesta(int vecinos, int umbral) {
    }
}
