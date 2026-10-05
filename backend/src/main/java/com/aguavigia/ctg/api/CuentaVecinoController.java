package com.aguavigia.ctg.api;

import com.aguavigia.ctg.api.dto.SolicitudRegistroVecino;
import com.aguavigia.ctg.domain.CorreoElectronico;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.port.in.RegistrarVecinoUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Registro abierto de vecinos. Responde 202 sin cuerpo exista o no el correo, por la misma razón
 * que {@link CuentaPublicaController}: devolver la cuenta o un 409 lo volvería un buscador de
 * correos. Su freno es el límite por IP de `application.yml` (`/api/cuentas/**`).
 */
@Tag(name = "Vecinos", description = "Registro, ingreso y perfil de los vecinos")
@RestController
@RequestMapping(value = "/api/cuentas/vecino", produces = MediaType.APPLICATION_JSON_VALUE)
public class CuentaVecinoController {

    private final RegistrarVecinoUseCase registrar;

    public CuentaVecinoController(RegistrarVecinoUseCase registrar) {
        this.registrar = registrar;
    }

    @Operation(summary = "Registrarse como vecino",
            description = """
                    Crea la cuenta sin clave y envia al correo el enlace para elegirla. Al elegirla la
                    cuenta queda ACTIVA, sin aprobacion de un administrador. La clave no viaja aqui: asi
                    nadie puede registrar el correo de otra persona con una clave suya.
                    Exige el barrio y aceptar el aviso de privacidad; la casilla de avisos es aparte.
                    Responde 202 aunque el correo ya tenga cuenta, para no revelar que direcciones
                    estan registradas.""")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Solicitud recibida; revisa tu correo"),
            @ApiResponse(responseCode = "400", description = """
                    Correo mal formado, barrio ausente o inexistente, \
                    o privacidad no aceptada""")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void registrarse(@Valid @RequestBody SolicitudRegistroVecino solicitud, HttpServletRequest peticion) {
        registrar.registrar(
                new CorreoElectronico(solicitud.correo()),
                solicitud.nombre(),
                new SectorId(solicitud.barrioId().strip()),
                solicitud.consentimiento().aDominio(),
                ContextoHttp.de(peticion));
    }
}
