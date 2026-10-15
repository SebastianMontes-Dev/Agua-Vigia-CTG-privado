package com.aguavigia.ctg.api;

import com.aguavigia.ctg.api.dto.CopiaAnaliticaRespuesta;
import com.aguavigia.ctg.api.dto.CumplimientoAnaliticoRespuesta;
import com.aguavigia.ctg.api.dto.SerieMensualAnaliticaRespuesta;
import com.aguavigia.ctg.api.error.RecursoNoEncontradoException;
import com.aguavigia.ctg.api.mapper.AnaliticaApiMapper;
import com.aguavigia.ctg.domain.CumplimientoAnalitico;
import com.aguavigia.ctg.domain.port.out.ConsultaAnaliticaPort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * El Índice de Cumplimiento según la copia analítica en PostgreSQL (ADR-100). Solo lectura y sin regla de negocio, así que depende del
 * puerto de consulta directamente (ADR-015). Los endpoints de siempre (`/api/cumplimiento`) siguen leyendo de MongoDB: estos son rutas
 * nuevas y opcionales. Con la copia apagada o PostgreSQL caído responden 503, y nunca suplen la consulta leyendo de MongoDB.
 */
@Tag(name = "Analítica", description = "Índice de Cumplimiento desde la copia analítica en PostgreSQL (ADR-100)")
@RestController
@RequestMapping(value = "/api", produces = MediaType.APPLICATION_JSON_VALUE)
public class AnaliticaController {

    private final ConsultaAnaliticaPort consulta;
    private final AnaliticaApiMapper mapper;

    public AnaliticaController(ConsultaAnaliticaPort consulta, AnaliticaApiMapper mapper) {
        this.consulta = consulta;
        this.mapper = mapper;
    }

    @Operation(operationId = "cumplimientoAnalitico", summary = "Índice de Cumplimiento desde la copia SQL",
            description = "El índice global de la ciudad, o el de un barrio con `sectorId`. Misma fórmula que "
                    + "`GET /api/cumplimiento`, calculada por una vista de PostgreSQL sobre la copia, que va hasta 5 minutos por "
                    + "detrás de MongoDB.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Índice calculado"),
            @ApiResponse(responseCode = "404", description = "El barrio no existe en la copia",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "503", description = "La copia analítica está apagada, PostgreSQL no responde o todavía no hubo una primera copia",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/analitica/cumplimiento")
    public CumplimientoAnaliticoRespuesta cumplimiento(@RequestParam(required = false) String sectorId) {
        CumplimientoAnalitico indice = sectorId == null
                ? consulta.cumplimientoGlobal()
                : consulta.cumplimientoDe(sectorId)
                        .orElseThrow(() -> new RecursoNoEncontradoException("No existe el barrio '" + sectorId + "' en la copia analítica"));
        return mapper.aRespuesta(indice, consulta.ultimaCopiaOk().orElse(null));
    }

    @Operation(operationId = "cumplimientoAnaliticoMensual", summary = "Serie mensual del Índice desde la copia SQL",
            description = "El índice agrupado por mes en hora de Cartagena, de más antiguo a más reciente.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Serie calculada"),
            @ApiResponse(responseCode = "503", description = "La copia analítica está apagada, PostgreSQL no responde o todavía no hubo una primera copia",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/analitica/cumplimiento/mensual")
    public SerieMensualAnaliticaRespuesta cumplimientoMensual() {
        Instant copiadoEn = consulta.ultimaCopiaOk().orElse(null);
        return new SerieMensualAnaliticaRespuesta(copiadoEn, mapper.aRespuestas(consulta.cumplimientoMensual()));
    }

    @Operation(operationId = "ultimasCopiasAnaliticas", summary = "Últimas copias de MongoDB a la base analítica",
            description = "Cuándo corrió cada copia, cuánto copió y si los conteos de las dos bases cuadraron. Es donde se ve el estado "
                    + "de la copia: `/actuator/health` no la incluye a propósito.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Las copias, de la más reciente a la más antigua"),
            @ApiResponse(responseCode = "503", description = "La copia analítica está apagada o PostgreSQL no responde",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PreAuthorize("hasAuthority('PERM_VER_PANEL')")
    @GetMapping("/veedor/analitica/copia")
    public List<CopiaAnaliticaRespuesta> ultimasCopias(@RequestParam(defaultValue = "20") int limite) {
        return mapper.aRespuestasDeCopias(consulta.ultimasCopias(Math.max(1, Math.min(200, limite))));
    }
}
