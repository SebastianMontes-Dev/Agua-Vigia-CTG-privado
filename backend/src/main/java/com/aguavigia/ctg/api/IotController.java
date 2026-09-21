package com.aguavigia.ctg.api;

import com.aguavigia.ctg.api.dto.IotPresionRequest;
import com.aguavigia.ctg.api.error.ServicioNoDisponibleException;
import com.aguavigia.ctg.domain.Coordenada;
import com.aguavigia.ctg.domain.CredencialInvalidaException;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.port.in.RegistrarLecturaDePresionUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * M13 — telemetría IoT pasiva (presión de red). Solo autentica al sensor y traduce HTTP; qué cuenta
 * como presión baja lo decide {@link RegistrarLecturaDePresionUseCase}. Todos los errores salen en
 * RFC 7807 como el resto de la API.
 */
@Tag(name = "IoT", description = "Telemetría de sensores de presión de la red (autenticada con X-IoT-Key)")
@RestController
@RequestMapping("/api/iot")
public class IotController {

    private final RegistrarLecturaDePresionUseCase lecturas;

    /**
     * Sin default: a diferencia de JWT_SECRET/VEEDOR_PASSWORD_HASH (mismo patrón), una clave
     * adivinable aquí permite inyectar reportes anónimos que alteran el consenso público.
     */
    private final String iotKey;

    public IotController(RegistrarLecturaDePresionUseCase lecturas,
                          @Value("${aguavigia.iot.key:}") String iotKey) {
        this.lecturas = lecturas;
        this.iotKey = iotKey;
    }

    @Operation(summary = "Registrar una lectura de presión de un sensor",
            description = """
                    Una presión por debajo del umbral (15 psi por defecto) se registra como reporte de
                    PRESION_BAJA con el cupo de sensor. Una lectura normal responde 200 sin registrar
                    nada. Sin cuerpo de respuesta en el caso exitoso.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lectura recibida (haya generado reporte o no)"),
            @ApiResponse(responseCode = "400", description = "Falta el sensor, el sector no existe o la coordenada es inválida",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "X-IoT-Key ausente o incorrecta",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "503", description = "El servidor no tiene configurada la clave de sensores",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/presion")
    public ResponseEntity<Void> reportarPresion(
            @RequestHeader(value = "X-IoT-Key", required = false) String key,
            @RequestBody IotPresionRequest request) {

        if (iotKey.isBlank()) {
            throw new ServicioNoDisponibleException("La telemetría de sensores no está configurada en este servidor");
        }
        if (key == null || !constantTimeEquals(key, iotKey)) {
            throw new CredencialInvalidaException("Clave de sensor ausente o incorrecta");
        }

        Coordenada coordenada = null;
        if (request.coordenada() != null && request.coordenada().lat() != null && request.coordenada().lon() != null) {
            coordenada = new Coordenada(request.coordenada().lat(), request.coordenada().lon());
        }
        lecturas.registrar(request.sensorId(), new SectorId(request.sectorId()), request.presionPsi(), coordenada);

        return ResponseEntity.ok().build();
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
