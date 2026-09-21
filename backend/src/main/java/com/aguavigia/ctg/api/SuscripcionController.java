package com.aguavigia.ctg.api;

import com.aguavigia.ctg.api.dto.SolicitudSuscripcion;
import com.aguavigia.ctg.api.dto.SuscripcionRespuesta;
import com.aguavigia.ctg.api.mapper.SuscripcionApiMapper;
import com.aguavigia.ctg.domain.CorreoElectronico;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.port.in.CancelarSuscripcionUseCase;
import com.aguavigia.ctg.domain.port.in.ConfirmarSuscripcionUseCase;
import com.aguavigia.ctg.domain.port.in.SuscribirseUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** M4 — RF012-RF015: suscribirse, confirmar por doble opt-in y darse de baja en 1 clic. */
@Tag(name = "Suscripciones", description = "Alertas por correo cuando cambia el estado de un sector")
@RestController
@RequestMapping(value = "/api/suscripciones", produces = MediaType.APPLICATION_JSON_VALUE)
public class SuscripcionController {

    private final SuscribirseUseCase suscribirse;
    private final ConfirmarSuscripcionUseCase confirmarSuscripcion;
    private final CancelarSuscripcionUseCase cancelarSuscripcion;
    private final SuscripcionApiMapper mapper;

    public SuscripcionController(SuscribirseUseCase suscribirse,
                                  ConfirmarSuscripcionUseCase confirmarSuscripcion,
                                  CancelarSuscripcionUseCase cancelarSuscripcion,
                                  SuscripcionApiMapper mapper) {
        this.suscribirse = suscribirse;
        this.confirmarSuscripcion = confirmarSuscripcion;
        this.cancelarSuscripcion = cancelarSuscripcion;
        this.mapper = mapper;
    }

    @Operation(summary = "Suscribirse a los avisos de uno o más sectores",
            description = """
                    Crea la suscripción en PENDIENTE_CONFIRMACION y envía un correo de doble
                    opt-in (Ley 1581/2012, RF013). No empieza a recibir avisos hasta confirmarla.""")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Suscripción creada, correo de confirmación en camino"),
            @ApiResponse(responseCode = "400", description = "Correo inválido o algún sector no existe",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping
    public ResponseEntity<SuscripcionRespuesta> suscribirse(@Valid @RequestBody SolicitudSuscripcion solicitud) {
        var suscripcion = suscribirse.suscribir(
                new CorreoElectronico(solicitud.correo()),
                solicitud.sectorIds().stream().map(SectorId::new).toList());

        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.aRespuesta(suscripcion));
    }

    @Operation(summary = "Confirmar la suscripción (doble opt-in)",
            description = """
                    Enlace del correo de confirmación. El token es de un solo enlace, no de un solo uso:
                    confirmarla dos veces no falla (RF013). Responde JSON o una página HTML de cortesía
                    según el `Accept` de quien pide — el mismo enlace del correo abre bien tanto en un
                    navegador como desde un cliente de API.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Suscripción confirmada"),
            @ApiResponse(responseCode = "400", description = "Token inválido, inexistente o de una suscripción ya cancelada",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping(value = "/confirmar", produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_HTML_VALUE})
    public ResponseEntity<?> confirmar(@RequestParam String token,
                                        @RequestHeader(value = HttpHeaders.ACCEPT, required = false) String accept) {
        if (prefiereHtml(accept)) {
            try {
                confirmarSuscripcion.confirmar(token);
                return PaginaDeCortesia.resultado("Suscripción confirmada",
                        "Listo. Ya vas a recibir un aviso cuando cambie el estado del servicio en tu sector.", true);
            } catch (IllegalArgumentException e) {
                return PaginaDeCortesia.resultado("No pudimos confirmar tu suscripción", e.getMessage(), false);
            }
        }
        var suscripcion = confirmarSuscripcion.confirmar(token);
        return ResponseEntity.ok(mapper.aRespuesta(suscripcion));
    }

    @Operation(summary = "Darse de baja en un clic (RF015)",
            description = """
                    Sin pedir credenciales — el token que llega en cada correo es suficiente. Responde
                    JSON o una página HTML de cortesía según el `Accept` de quien pide (mismo motivo
                    que en {@code /confirmar}).""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Suscripción cancelada"),
            @ApiResponse(responseCode = "400", description = "Token inválido o inexistente",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping(value = "/cancelar", produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_HTML_VALUE})
    public ResponseEntity<?> cancelar(@RequestParam String token,
                                       @RequestHeader(value = HttpHeaders.ACCEPT, required = false) String accept) {
        if (prefiereHtml(accept)) {
            try {
                cancelarSuscripcion.cancelar(token);
                return PaginaDeCortesia.resultado("Baja confirmada", "Ya no vas a recibir más avisos de AguaVigía para ese sector.", true);
            } catch (IllegalArgumentException e) {
                return PaginaDeCortesia.resultado("No pudimos procesar la baja", e.getMessage(), false);
            }
        }
        var suscripcion = cancelarSuscripcion.cancelar(token);
        return ResponseEntity.ok(mapper.aRespuesta(suscripcion));
    }

    /**
     * Un navegador que abre el enlace del correo siempre manda {@code text/html} en su
     * `Accept` (con o sin `q` explícito); un cliente de API que quiere JSON no lo incluye.
     * Sin `Accept` (curl a pelo, MockMvc por defecto) se mantiene el JSON de siempre.
     */
    private static boolean prefiereHtml(String accept) {
        return accept != null && accept.contains(MediaType.TEXT_HTML_VALUE);
    }

}
