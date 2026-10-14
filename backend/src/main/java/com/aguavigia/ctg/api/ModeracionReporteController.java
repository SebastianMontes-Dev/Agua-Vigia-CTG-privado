package com.aguavigia.ctg.api;

import com.aguavigia.ctg.api.dto.ReporteModeracionRespuesta;
import com.aguavigia.ctg.api.mapper.ReporteModeracionApiMapper;
import com.aguavigia.ctg.domain.Pagina;
import com.aguavigia.ctg.domain.ReporteId;
import com.aguavigia.ctg.domain.ReportesPendientes;
import com.aguavigia.ctg.domain.port.in.DescartarFotoUseCase;
import com.aguavigia.ctg.domain.port.in.ListarReportesPendientesUseCase;
import com.aguavigia.ctg.domain.port.in.ModerarReporteUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * M5 — RF018: moderar (aprobar/descartar) reportes ciudadanos. Protegido por completo por
 * `SecurityConfig` (`/api/veedor/**` exige token), sin declarar nada de seguridad aquí.
 *
 * `ADR-023`: "dudoso" es "todo lo que sigue PENDIENTE" — no hay una preselección automática. La
 * cola completa de pendientes es lo que ve el veedor.
 */
@Tag(name = "Veedor - Moderación", description = "Moderar reportes ciudadanos pendientes (RF018)")
@RestController
@RequestMapping(value = "/api/veedor/reportes", produces = MediaType.APPLICATION_JSON_VALUE)
public class ModeracionReporteController {

    private final ModerarReporteUseCase moderarReporte;
    private final DescartarFotoUseCase descartarFoto;
    private final ListarReportesPendientesUseCase listarPendientes;
    private final ReporteModeracionApiMapper mapper;

    public ModeracionReporteController(ModerarReporteUseCase moderarReporte,
                                        DescartarFotoUseCase descartarFoto,
                                        ListarReportesPendientesUseCase listarPendientes,
                                        ReporteModeracionApiMapper mapper) {
        this.moderarReporte = moderarReporte;
        this.descartarFoto = descartarFoto;
        this.listarPendientes = listarPendientes;
        this.mapper = mapper;
    }

    @Operation(summary = "Listar los reportes pendientes de moderación, más antiguos primero",
            description = """
                    Paginado, con el total y el enlace a la siguiente página en las cabeceras
                    `X-Total-Count` y `Link`. Por defecto 50; el máximo por página es 200. Cada reporte trae `senalRed`:
                    verdadero si viene de una red que ya envió una ráfaga de reportes a ese barrio (D9).""")
    @PreAuthorize("hasAuthority('PERM_VER_PANEL')")
    @GetMapping("/pendientes")
    public ResponseEntity<List<ReporteModeracionRespuesta>> listarPendientes(
            @RequestParam(required = false) Integer pagina,
            @RequestParam(required = false) Integer tamano) {

        ReportesPendientes resultado = listarPendientes.listar(
                Pagina.paginaValida(pagina), Pagina.tamanoValido(tamano));

        return CabecerasDePaginacion.respuesta(
                resultado.pagina(), mapper.aRespuestas(resultado), "/api/veedor/reportes/pendientes");
    }

    @Operation(summary = "Aprobar un reporte")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reporte aprobado"),
            @ApiResponse(responseCode = "404", description = "El reporte no existe",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PreAuthorize("hasAuthority('PERM_MODERAR_REPORTES')")
    @PatchMapping("/{id}/aprobar")
    public ReporteModeracionRespuesta aprobar(@PathVariable String id) {
        return mapper.aRespuesta(moderarReporte.aprobar(new ReporteId(id)));
    }

    @Operation(summary = "Descartar un reporte")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reporte descartado"),
            @ApiResponse(responseCode = "404", description = "El reporte no existe",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PreAuthorize("hasAuthority('PERM_MODERAR_REPORTES')")
    @PatchMapping("/{id}/descartar")
    public ReporteModeracionRespuesta descartar(@PathVariable String id) {
        return mapper.aRespuesta(moderarReporte.descartar(new ReporteId(id)));
    }

    @Operation(summary = "Descartar solo la foto de un reporte",
            description = """
                    El reporte sigue como esta (su voto no cambia); la foto deja de servirse al publico y el panel la
                    sigue viendo como evidencia.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Foto descartada"),
            @ApiResponse(responseCode = "404", description = "El reporte no existe",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "El reporte no tiene foto",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PreAuthorize("hasAuthority('PERM_MODERAR_REPORTES')")
    @PatchMapping("/{id}/foto/descartar")
    public ReporteModeracionRespuesta descartarFoto(@PathVariable String id) {
        return mapper.aRespuesta(descartarFoto.descartarFoto(new ReporteId(id)));
    }
}
