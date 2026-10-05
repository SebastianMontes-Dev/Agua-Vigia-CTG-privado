package com.aguavigia.ctg.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Reporte ciudadano registrado")
public record ReporteRespuesta(
        String id,
        String sectorId,
        String tipo,
        Instant timestamp,

        @Schema(description = """
                Ruta de la foto: `/api/fotos/{nombre}`. Existir no significa que se pueda ver: el servidor la sirve al
                publico solo si `fotoEstado` es PUBLICA; antes responde 404.""")
        String fotoUrl,

        @Schema(description = """
                SIN_FOTO, EN_REVISION (el reporte espera moderacion), PUBLICA o DESCARTADA. Con EN_REVISION la interfaz
                muestra «en revision» en vez de pedir la imagen.""")
        String fotoEstado,

        Integer confirmaciones,

        @Schema(description = """
                Cuanto respalda el servidor que quien reporta esta en el barrio: CUENTA_VERIFICADA, UBICACION_VERIFICADA
                o NINGUNA. Lo decide el servidor; el cliente solo lo muestra.""")
        String verificacion,

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(description = """
                Solo al crear el reporte: el token con el que su autor sube la foto en `POST /api/reportes/{id}/foto`
                (cabecera `X-Subida`). De un solo uso y vence a los pocos minutos; no se vuelve a entregar.""")
        String subidaToken) {

    /** La misma respuesta con el token de subida: solo lo recibe quien acaba de crear el reporte. */
    public ReporteRespuesta conSubidaToken(String token) {
        return new ReporteRespuesta(id, sectorId, tipo, timestamp, fotoUrl, fotoEstado, confirmaciones, verificacion, token);
    }
}
