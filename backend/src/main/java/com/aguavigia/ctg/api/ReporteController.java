package com.aguavigia.ctg.api;

import com.aguavigia.ctg.api.dto.ReporteRespuesta;
import com.aguavigia.ctg.api.dto.SolicitudReporte;
import com.aguavigia.ctg.api.mapper.ReporteApiMapper;
import com.aguavigia.ctg.domain.Coordenada;
import com.aguavigia.ctg.domain.ReporteId;
import com.aguavigia.ctg.domain.Reportante;
import com.aguavigia.ctg.domain.RolVeedor;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.SesionAutenticada;
import com.aguavigia.ctg.domain.TipoReporte;
import com.aguavigia.ctg.domain.UsuarioId;
import com.aguavigia.ctg.domain.port.in.AgregarEvidenciaUseCase;
import com.aguavigia.ctg.domain.port.in.ConfirmarReporteUseCase;
import com.aguavigia.ctg.domain.port.in.IdentificarReportanteUseCase;
import com.aguavigia.ctg.domain.port.in.RegistrarReporteUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.RestController;

/**
 * M2 — RF005-RF008: reportar sin registro, en máximo dos toques.
 *
 * La identidad de quien reporta la resuelve el servidor (IdentificarReportanteUseCase) con la cabecera
 * `X-Dispositivo` o la sesión de un vecino; el cuerpo ya no lleva ninguna. Esta ruta es pública: sin
 * identidad responde 401 `dispositivo-invalido`, no pide cuenta.
 */
@Tag(name = "Reportes", description = "Reportes ciudadanos de estado del servicio, sin registro")
@RestController
@RequestMapping(value = "/api/reportes", produces = MediaType.APPLICATION_JSON_VALUE)
public class ReporteController {

    private static final String CABECERA_DISPOSITIVO = "X-Dispositivo";

    private final RegistrarReporteUseCase registrarReporte;
    private final AgregarEvidenciaUseCase agregarEvidenciaUseCase;
    private final ConfirmarReporteUseCase confirmarReporte;
    private final IdentificarReportanteUseCase identificar;
    private final ReporteApiMapper mapper;

    public ReporteController(RegistrarReporteUseCase registrarReporte,
                             AgregarEvidenciaUseCase agregarEvidenciaUseCase,
                             ConfirmarReporteUseCase confirmarReporte,
                             IdentificarReportanteUseCase identificar,
                             ReporteApiMapper mapper) {
        this.registrarReporte = registrarReporte;
        this.agregarEvidenciaUseCase = agregarEvidenciaUseCase;
        this.confirmarReporte = confirmarReporte;
        this.identificar = identificar;
        this.mapper = mapper;
    }

    @Operation(summary = "Registrar un reporte ciudadano",
            description = """
                    Sin registro ni cuenta (RF005), pero con identidad: la cabecera `X-Dispositivo` (token de
                    `POST /api/dispositivos`) o la sesion de un vecino. Sin ninguna de las dos responde 401
                    `dispositivo-invalido`. Limita automaticamente los reportes por identidad en la ventana vigente
                    (RF006, 3 para un dispositivo y 5 para un vecino) — ver 429. Hace falta el `sectorId`, la
                    `coordenada` o ambos (RF007): con solo la coordenada el servidor infiere el sector que la
                    contiene y responde 400 si cae fuera de todo barrio de Cartagena. La coordenada se envia solo si
                    el usuario autorizo compartir su ubicacion; con su `precisionMetros` el servidor verifica el
                    reporte (campo `verificacion` de la respuesta) y guarda solo una aproximacion de ella.""")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Reporte registrado"),
            @ApiResponse(responseCode = "400", description = "Sector inexistente, tipo inválido, coordenada fuera de Cartagena o sin sector ni coordenada",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Falta `X-Dispositivo`, no lo firmó este servidor o el dispositivo ya no existe (type `dispositivo-invalido`): pide otro con POST /api/dispositivos",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "429", description = "La identidad superó el límite de reportes para este sector",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping
    public ResponseEntity<ReporteRespuesta> registrar(
            @RequestHeader(value = CABECERA_DISPOSITIVO, required = false) String dispositivo,
            @Valid @RequestBody SolicitudReporte solicitud,
            HttpServletRequest peticion) {
        Reportante reportante = identificar.identificar(dispositivo, cuentaDeVecino());

        Coordenada coordenada = solicitud.coordenada() != null
                ? new Coordenada(solicitud.coordenada().latitud(), solicitud.coordenada().longitud())
                : null;

        // esSensor=false siempre: esta ruta es pública, así que nada de lo que llegue aquí puede otorgar el
        // cupo de sensor — eso lo decide solo IotController, tras validar X-IoT-Key.
        SectorId sectorId = solicitud.sectorId() == null || solicitud.sectorId().isBlank()
                ? null : new SectorId(solicitud.sectorId());
        var reporte = registrarReporte.registrar(
                sectorId,
                tipoDe(solicitud.tipo()),
                coordenada,
                solicitud.precisionMetros(),
                reportante,
                ContextoHttp.de(peticion).ip(),
                false);

        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.aRespuesta(reporte));
    }

    @Operation(summary = "Agregar evidencia a un reporte",
            description = "Permite subir una foto y asociarla a un reporte existente (M10).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Evidencia agregada"),
            @ApiResponse(responseCode = "400", description = "Error en la solicitud",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Reporte no encontrado",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping(value = "/{id}/foto", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ReporteRespuesta> agregarEvidencia(
            @PathVariable("id") String id,
            @RequestParam("foto") MultipartFile foto) throws java.io.IOException {
        var reporte = agregarEvidenciaUseCase.agregarEvidencia(id, foto.getContentType(), foto.getBytes());
        return ResponseEntity.ok(mapper.aRespuesta(reporte));
    }

    @Operation(summary = "Confirmar un reporte",
            description = """
                    Permite a otro vecino confirmar un reporte ciudadano (M11). Sin cuerpo: la identidad de quien
                    confirma viaja en `X-Dispositivo` o en la sesion de un vecino, igual que al reportar.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reporte confirmado"),
            @ApiResponse(responseCode = "401", description = "Falta o no es válida la identidad del dispositivo (type `dispositivo-invalido`)",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Reporte no encontrado",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping(value = "/{id}/confirmar")
    public ResponseEntity<ReporteRespuesta> confirmar(
            @PathVariable("id") String id,
            @RequestHeader(value = CABECERA_DISPOSITIVO, required = false) String dispositivo) {
        Reportante reportante = identificar.identificar(dispositivo, cuentaDeVecino());
        var reporte = confirmarReporte.confirmar(new ReporteId(id), reportante.huella());
        return ResponseEntity.ok(mapper.aRespuesta(reporte));
    }

    /**
     * La cuenta de la sesión, solo si es de un vecino. Un ADMIN tiene el permiso del vecino por heredarlos todos,
     * pero no reporta como tal: por eso se mira el rol y no el permiso.
     */
    private static UsuarioId cuentaDeVecino() {
        return ContextoHttp.sesionActual()
                .filter(sesion -> RolVeedor.VECINO.name().equals(sesion.rol()))
                .map(SesionAutenticada::id)
                .orElse(null);
    }

    /** Traduce el texto del cliente al enum sin exponer el mensaje de `valueOf`, que nombra la clase del dominio. */
    private static TipoReporte tipoDe(String texto) {
        try {
            return TipoReporte.valueOf(texto);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Tipo de reporte inválido '" + texto + "'. Valores permitidos: "
                    + java.util.Arrays.stream(TipoReporte.values()).map(Enum::name).collect(java.util.stream.Collectors.joining(", ")));
        }
    }
}
