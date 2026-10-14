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

/**
 * M4 — RF012-RF015: suscribirse, confirmar por doble opt-in y darse de baja en 1 clic. Los enlaces de los
 * correos son GET que solo muestran un botón; confirmar y cancelar son POST (`ADR-054`).
 */
@Tag(name = "Suscripciones", description = "Alertas por correo cuando cambia el estado de un sector")
@RestController
@RequestMapping(value = "/api/suscripciones", produces = MediaType.APPLICATION_JSON_VALUE)
public class SuscripcionController {

    private static final String RUTA = "/api/suscripciones/";

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

    @Operation(summary = "Pantalla del enlace «Confirmar» del correo",
            description = """
                    Página a la que lleva el enlace del correo. Solo muestra un botón: NO confirma nada, porque un
                    antivirus o una vista previa de enlaces abre los GET sin que nadie los pida (`ADR-054`). La
                    acción ocurre al enviar el formulario, que hace POST a la misma ruta.""")
    @ApiResponse(responseCode = "200", description = "Página con el botón")
    @GetMapping(value = "/confirmar", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> pantallaConfirmar(@RequestParam String token) {
        return PaginaDeCortesia.formulario(HttpStatus.OK, "Confirma tu suscripción",
                "Un paso más: confirma que quieres recibir los avisos del estado del servicio.", RUTA + "confirmar", token, false, "Confirmar mi suscripción");
    }

    @Operation(summary = "Confirmar la suscripción (doble opt-in)",
            description = """
                    Acción del botón de la página de confirmación (o de un cliente de API). El token es de un solo enlace, no de un solo uso:
                    confirmarla dos veces no falla (RF013). Responde JSON o una página HTML de cortesía
                    según el `Accept` de quien pide: el formulario de la página responde HTML y un cliente de API
                    responde JSON. `token` va como parámetro de consulta o del formulario.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Suscripción confirmada"),
            @ApiResponse(responseCode = "400", description = "Token inválido, inexistente o de una suscripción ya cancelada",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping(value = "/confirmar", produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_HTML_VALUE})
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

    @Operation(summary = "Pantalla del enlace de baja de todo correo (RF015)",
            description = """
                    Página a la que lleva el enlace del correo. Solo muestra un botón: NO cancela nada, porque un
                    antivirus o una vista previa de enlaces abre los GET sin que nadie los pida (`ADR-054`). La
                    acción ocurre al enviar el formulario, que hace POST a la misma ruta.""")
    @ApiResponse(responseCode = "200", description = "Página con el botón")
    @GetMapping(value = "/cancelar", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> pantallaCancelar(@RequestParam String token) {
        return PaginaDeCortesia.formulario(HttpStatus.OK, "Darte de baja",
                "Si sigues, dejarás de recibir avisos de AguaVigía para ese sector.", RUTA + "cancelar", token, false, "Darme de baja");
    }

    @Operation(summary = "Darse de baja en un clic (RF015)",
            description = """
                    Acción del botón de la página de baja (o de un cliente de API). Sin pedir credenciales: el token que llega en cada correo es suficiente. Responde
                    JSON o una página HTML de cortesía según el `Accept` de quien pide (mismo motivo
                    que en {@code /confirmar}).""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Suscripción cancelada"),
            @ApiResponse(responseCode = "400", description = "Token inválido o inexistente",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping(value = "/cancelar", produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_HTML_VALUE})
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
