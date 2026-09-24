package com.aguavigia.ctg.api;

import com.aguavigia.ctg.infrastructure.sse.SseSectoresBroadcaster;
import com.aguavigia.ctg.api.dto.GeometriaSectoresRespuesta;
import com.aguavigia.ctg.api.dto.RespuestaSectores;
import com.aguavigia.ctg.api.dto.SectorRespuesta;
import com.aguavigia.ctg.api.error.RecursoNoEncontradoException;
import com.aguavigia.ctg.api.mapper.SectorApiMapper;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.port.out.GeometriaSectoresPort;
import com.aguavigia.ctg.domain.port.out.RelojPort;
import com.aguavigia.ctg.domain.port.out.SectorRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import java.time.Duration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * M1 — mapa en vivo (RF001-RF004).
 *
 * Consulta de solo lectura sin regla de negocio: va directo al puerto de salida, sin caso de
 * uso intermedio (ADR-015). Si alguna vez aparece una regla aqui, deja de ser cosa del
 * controlador y pasa a application/.
 */
@Tag(name = "Sectores", description = "Estado del servicio de agua por sector de Cartagena")
@RestController
@RequestMapping(value = "/api/sectores", produces = MediaType.APPLICATION_JSON_VALUE)
public class SectorController {

    private final SectorRepository sectores;
    private final GeometriaSectoresPort geometrias;
    private final SectorApiMapper mapper;
    private final RelojPort reloj;
    private final SseSectoresBroadcaster sseBroadcaster;

    public SectorController(SectorRepository sectores, GeometriaSectoresPort geometrias, SectorApiMapper mapper,
                             RelojPort reloj, SseSectoresBroadcaster sseBroadcaster) {
        this.sectores = sectores;
        this.geometrias = geometrias;
        this.mapper = mapper;
        this.reloj = reloj;
        this.sseBroadcaster = sseBroadcaster;
    }

    @Operation(summary = "Avisos en vivo de cambios de estado (SSE)",
            description = """
                    Conexión abierta (`text/event-stream`) que AVISA de que algo cambió; no envía el
                    estado. Cada evento `sectores` trae `{"actualizadoEn": "..."}` y el cliente pide
                    entonces GET /api/sectores, que está cacheado. Un comentario `:latido` llega cada
                    25 s y `retry:` indica cuánto esperar antes de reconectar. El servidor agrupa los
                    cambios en un aviso por segundo como máximo y cierra la conexión cada ~10-12
                    minutos por diseño. Por encima del tope de conexiones responde 429 con `Retry-After`.""")
    @ApiResponse(responseCode = "429", description = "Tope de conexiones en vivo alcanzado; reintentar tras Retry-After",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamSectores() {
        return sseBroadcaster.registrar();
    }

    @Operation(summary = "Listar los sectores con su estado conocido",
            description = """
                    Devuelve los sectores de Cartagena (211 barrios sembrados desde el GeoJSON
                    oficial). `estado` viaja nulo mientras no haya dato verificado del sector —
                    el cliente debe mostrarlo como "sin datos" y no suponer que hay servicio.""")
    @ApiResponse(responseCode = "200", description = "Listado generado")
    @GetMapping
    public RespuestaSectores listarSectores() {
        return new RespuestaSectores(mapper.aRespuestas(sectores.listarTodos()), reloj.ahora());
    }

    @Operation(summary = "Polígonos de todos los sectores (GeoJSON)",
            description = """
                    FeatureCollection con un Feature por sector; `id` es el mismo que devuelve
                    GET /api/sectores. Los polígonos solo cambian al volver a sembrar, por eso la
                    respuesta se puede cachear un día.""")
    @ApiResponse(responseCode = "200", description = "Geometrías",
            content = @Content(mediaType = "application/geo+json"))
    @GetMapping(value = "/geometria", produces = "application/geo+json")
    public ResponseEntity<GeometriaSectoresRespuesta> geometria() {
        var features = geometrias.listar().stream()
                .map(g -> GeometriaSectoresRespuesta.Feature.de(g.id().valor(), g.nombre(), g.geometria()))
                .toList();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic())
                .contentType(MediaType.parseMediaType("application/geo+json"))
                .body(GeometriaSectoresRespuesta.de(features));
    }

    @Operation(summary = "Consultar un sector por su identificador")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sector encontrado"),
            @ApiResponse(responseCode = "404", description = "No existe un sector con ese id",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/{id}")
    public SectorRespuesta consultarSector(@PathVariable String id) {
        return sectores.buscarPorId(new SectorId(id))
                .map(mapper::aRespuesta)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el sector '" + id + "'"));
    }
}
