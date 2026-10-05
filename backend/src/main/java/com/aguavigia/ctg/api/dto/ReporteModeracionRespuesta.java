package com.aguavigia.ctg.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Reporte ciudadano en la cola de moderación del veedor (RF018)")
public record ReporteModeracionRespuesta(
        String id,
        String sectorId,
        String tipo,
        @Schema(description = "Nulo si el usuario no autorizó compartir su ubicación (RF007). Es una aproximación de unos 110 m, no la ubicación exacta")
        CoordenadaDTO coordenada,
        Instant timestamp,
        @Schema(description = "PENDIENTE, APROBADO o DESCARTADO")
        String estadoModeracion,
        @Schema(description = "CUENTA_VERIFICADA, UBICACION_VERIFICADA o NINGUNA: cuánto respalda el servidor que quien reporta está en el barrio")
        String verificacion,
        @Schema(description = "SIN_FOTO, EN_REVISION, PUBLICA o DESCARTADA")
        String fotoEstado,
        @Schema(description = "Ruta de la foto para el panel (`/api/veedor/fotos/{nombre}`), que la ve en cualquier estado. Nulo si no hay foto")
        String fotoUrl,
        @Schema(description = "Solo en la cola de pendientes: el reporte viene de una red (resumen diario de la IP, que no se expone) que ya envio una "
                + "rafaga de reportes a este barrio. No bloquea nada: es donde mirar primero. Nulo en el resto de respuestas",
                nullable = true)
        Boolean senalRed) {
}
