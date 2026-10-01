package com.aguavigia.ctg.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Reporte ciudadano registrado")
public record ReporteRespuesta(
        String id,
        String sectorId,
        String tipo,
        Instant timestamp,
        String fotoUrl,
        Integer confirmaciones,

        @Schema(description = """
                Cuanto respalda el servidor que quien reporta esta en el barrio: CUENTA_VERIFICADA, UBICACION_VERIFICADA
                o NINGUNA. Lo decide el servidor; el cliente solo lo muestra.""")
        String verificacion) {
}
