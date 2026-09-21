package com.aguavigia.ctg.api;

import com.aguavigia.ctg.api.dto.SolicitudCambioClave;
import com.aguavigia.ctg.domain.ClaveEnClaro;
import com.aguavigia.ctg.domain.port.in.CambiarClaveUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lo que una persona puede hacer con su propia cuenta estando dentro. Pide `VER_PANEL`, que tienen todos los
 * roles: así una sesión restringida a dar de alta el segundo factor (alcance ALTA_SEGUNDO_FACTOR) no llega aquí.
 */
@Tag(name = "Veedor - Cuenta propia", description = "Cambios que una persona hace sobre su propia cuenta")
@RestController
@RequestMapping(value = "/api/veedor/cuenta", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasAuthority('PERM_VER_PANEL')")
public class CuentaPropiaController {

    private final CambiarClaveUseCase cambiarClave;

    public CuentaPropiaController(CambiarClaveUseCase cambiarClave) {
        this.cambiarClave = cambiarClave;
    }

    @Operation(summary = "Cambiar la propia clave",
            description = """
                    Exige la clave actual y comparte el contador de intentos fallidos con el inicio de sesión
                    (5 fallos en 15 minutos bloquean la cuenta 15 minutos). Al cambiarla se cierran **todas** las
                    sesiones, la actual incluida: el cliente debe volver a pedir `POST /api/veedor/sesion` con la
                    clave nueva. Se avisa por correo del cambio.""")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Clave cambiada; todas las sesiones cerradas"),
            @ApiResponse(responseCode = "400", description = "La clave actual no es correcta, la nueva no cumple la política "
                    + "(12 a 128 caracteres) o es igual a la actual"),
            @ApiResponse(responseCode = "423", description = "Cuenta bloqueada por intentos fallidos")
    })
    @PostMapping("/clave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cambiarClave(@Valid @RequestBody SolicitudCambioClave solicitud, HttpServletRequest peticion) {
        cambiarClave.cambiar(ContextoHttp.usuarioActual(), solicitud.claveActual(),
                new ClaveEnClaro(solicitud.claveNueva()), ContextoHttp.de(peticion));
    }
}
