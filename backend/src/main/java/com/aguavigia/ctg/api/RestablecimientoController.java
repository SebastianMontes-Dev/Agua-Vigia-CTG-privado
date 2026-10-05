package com.aguavigia.ctg.api;

import com.aguavigia.ctg.api.dto.ReporteRespuesta;
import com.aguavigia.ctg.api.mapper.ReporteApiMapper;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.port.in.ConfirmarRestablecimientoPorEnlaceUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * «¿Ya volvió el agua?» con un toque (D18). El correo de aviso lleva un enlace firmado a la pantalla del frontend, y esa
 * pantalla llama aquí. Es un {@code POST} a propósito: abrir un enlace del correo no debe votar, y algunos clientes de
 * correo abren los enlaces por su cuenta para revisarlos.
 */
@Tag(name = "Reportes", description = "Reportes ciudadanos de estado del servicio, sin registro")
@RestController
@RequestMapping(value = "/api/sectores/{sectorId}/restablecimiento", produces = MediaType.APPLICATION_JSON_VALUE)
public class RestablecimientoController {

    private final ConfirmarRestablecimientoPorEnlaceUseCase confirmar;
    private final ReporteApiMapper mapper;

    public RestablecimientoController(ConfirmarRestablecimientoPorEnlaceUseCase confirmar, ReporteApiMapper mapper) {
        this.confirmar = confirmar;
        this.mapper = mapper;
    }

    @Operation(summary = "Confirmar con un toque que volvió el agua",
            description = """
                    El `token` es el del enlace del correo de aviso. Sin sesion ni `X-Dispositivo`: lo que identifica a
                    quien toca es el token, que firma el servidor, vence y es de un barrio y una suscripcion. Registra un
                    reporte `SERVICIO_RESTABLECIDO` en ese barrio, con el mismo cupo y el mismo quorum de cualquier otro
                    reporte: un toque no cambia el estado por si solo.""")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Voto registrado"),
            @ApiResponse(responseCode = "400", description = "Falta el token",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "El enlace no sirve (type `enlace-invalido`): vencio, es de otro barrio o ya no recibes los avisos",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "429", description = "Se agoto el cupo de reportes de esta suscripcion en el barrio",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping
    public ResponseEntity<ReporteRespuesta> confirmarRestablecimiento(
            @PathVariable("sectorId") String sectorId,
            @RequestParam("token") String token,
            HttpServletRequest peticion) {
        var reporte = confirmar.confirmar(new SectorId(sectorId), token, ContextoHttp.de(peticion).ip());
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.aRespuesta(reporte));
    }
}
