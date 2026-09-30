package com.aguavigia.ctg.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * El identificador del sensor abre un cupo propio de reportes: sin tope de longitud ni alfabeto, quien
 * tuviera la clave podría fabricar sensores nuevos sin límite y saltarse ese cupo con solo variarlo.
 */
public record IotPresionRequest(
    @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9._:-]+") String sensorId,
    @NotBlank @Size(max = 100) String sectorId,
    @DecimalMin("0.0") @DecimalMax("1000.0") Double presionPsi,
    @Valid IotCoordenada coordenada
) {}
