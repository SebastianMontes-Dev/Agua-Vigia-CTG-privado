package com.aguavigia.ctg.api;

import com.aguavigia.ctg.api.dto.TokenDeDispositivoRespuesta;
import com.aguavigia.ctg.domain.port.in.EmitirTokenDeDispositivoUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Pública a propósito: quien reporta sin cuenta no tiene sesión que presentar. Lo que frena la
 * fabricación de identidades es el límite por IP de `application.yml` (10 por hora), no el acceso.
 * La respuesta no se cachea porque Spring Security ya marca toda respuesta con `no-store`.
 */
@Tag(name = "Dispositivos", description = "Identidad pseudonima de quien reporta sin cuenta")
@RestController
@RequestMapping(value = "/api/dispositivos", produces = MediaType.APPLICATION_JSON_VALUE)
public class DispositivoController {

    private final EmitirTokenDeDispositivoUseCase emitir;

    public DispositivoController(EmitirTokenDeDispositivoUseCase emitir) {
        this.emitir = emitir;
    }

    @Operation(summary = "Pedir una identidad de dispositivo",
            description = """
                    Crea un dispositivo nuevo y devuelve su token firmado. Pidelo la primera vez, guardalo y
                    enviarlo en `X-Dispositivo` en cada reporte y confirmacion. Cada llamada crea una
                    identidad distinta: no la pidas en cada reporte. Si un reporte responde 401
                    `dispositivo-invalido`, pide otro. Maximo 10 por hora por IP.""")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Token emitido"),
            @ApiResponse(responseCode = "429", description = "Demasiados tokens pedidos desde esta IP")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TokenDeDispositivoRespuesta pedir() {
        return new TokenDeDispositivoRespuesta(emitir.emitir());
    }
}
