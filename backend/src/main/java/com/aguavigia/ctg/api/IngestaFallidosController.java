package com.aguavigia.ctg.api;

import com.aguavigia.ctg.api.dto.DocumentoFallidoRespuesta;
import com.aguavigia.ctg.infrastructure.persistence.mongo.DocumentoFallidoDocumento;
import com.aguavigia.ctg.infrastructure.persistence.mongo.DocumentoFallidoMongoRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * RNF006 — cola muerta de la ingesta (BUG-091): documentos que siguen fallando al procesarse, con
 * su motivo. Antes solo quedaba un `log.warn`; ver el javadoc de {@code PipelineOrquestador}.
 *
 * Mismo criterio que {@link IngestaSaludController} (ADR-015): lee directo del repositorio Mongo,
 * sin caso de uso intermedio — no hay regla de negocio en consultar esta cola, solo lectura.
 */
@Tag(name = "Veedor - Ingesta", description = "Documentos de la ingesta que siguen fallando, con su motivo (RNF006)")
@RestController
@RequestMapping(value = "/api/veedor/ingesta/fallidos", produces = MediaType.APPLICATION_JSON_VALUE)
public class IngestaFallidosController {

    private final DocumentoFallidoMongoRepository fallidos;

    public IngestaFallidosController(DocumentoFallidoMongoRepository fallidos) {
        this.fallidos = fallidos;
    }

    @Operation(summary = "Listar los documentos que siguen fallando al procesarse, más recientes primero",
            description = """
                    Un documento sale de esta lista en cuanto se procesa con éxito: es lo que sigue
                    roto *ahora*, no un histórico. Máximo 200 filas.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Listado generado"),
            @ApiResponse(responseCode = "401", description = "Falta el token del veedor")
    })
    @PreAuthorize("hasAuthority('PERM_VER_PANEL')")
    @GetMapping
    public List<DocumentoFallidoRespuesta> fallidos() {
        return fallidos.findTop200ByOrderByUltimoIntentoDesc().stream()
                .map(IngestaFallidosController::aRespuesta)
                .toList();
    }

    private static DocumentoFallidoRespuesta aRespuesta(DocumentoFallidoDocumento documento) {
        return new DocumentoFallidoRespuesta(
                documento.getFuente(),
                documento.getUrlOriginal(),
                documento.getTitulo(),
                documento.getMotivo(),
                documento.getPrimerIntento(),
                documento.getUltimoIntento(),
                documento.getReintentos());
    }
}
