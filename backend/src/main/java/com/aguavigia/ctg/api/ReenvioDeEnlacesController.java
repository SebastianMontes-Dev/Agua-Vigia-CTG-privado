package com.aguavigia.ctg.api;

import com.aguavigia.ctg.api.dto.SolicitudReenvioVerificacion;
import com.aguavigia.ctg.domain.CorreoElectronico;
import com.aguavigia.ctg.domain.UsuarioId;
import com.aguavigia.ctg.domain.port.in.ReenviarEnlaceDeCuentaUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reenvío de los correos de cuenta cuyo enlace no llegó o venció. Van juntos porque son el mismo caso de uso, aunque
 * uno es público (quien espera verificar su correo) y el otro es del panel (un administrador con la invitación).
 */
@Tag(name = "Cuentas", description = "Alta, verificacion y recuperacion de cuentas del panel")
@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
public class ReenvioDeEnlacesController {

    private final ReenviarEnlaceDeCuentaUseCase reenviar;

    public ReenvioDeEnlacesController(ReenviarEnlaceDeCuentaUseCase reenviar) {
        this.reenviar = reenviar;
    }

    @Operation(summary = "Reenviar el correo de verificación",
            description = """
                    Para quien se registró y no recibió el enlace (o venció). Responde 202 **siempre**, exista o no
                    la cuenta y ya esté verificada o no: no revela qué correos están registrados. Reenvía como mucho
                    una vez cada 2 minutos por cuenta. El enlace anterior deja de servir.""")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Si había algo que reenviar, el correo va en camino"),
            @ApiResponse(responseCode = "400", description = "Correo mal formado")
    })
    @PostMapping("/api/cuentas/verificacion/reenvio")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void reenviarVerificacion(@Valid @RequestBody SolicitudReenvioVerificacion solicitud,
                                     HttpServletRequest peticion) {
        reenviar.reenviarVerificacion(new CorreoElectronico(solicitud.correo()), ContextoHttp.de(peticion));
    }

    @Operation(summary = "Reenviar la invitación a una cuenta que aún no la aceptó",
            description = "Requiere GESTIONAR_USUARIOS. El enlace anterior deja de servir y se reinicia su vigencia de 7 días.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Invitación reenviada"),
            @ApiResponse(responseCode = "404", description = "No existe la cuenta"),
            @ApiResponse(responseCode = "409", description = "La cuenta ya aceptó la invitación (no está en estado INVITADA)")
    })
    @PreAuthorize("hasAuthority('PERM_GESTIONAR_USUARIOS')")
    @PostMapping("/api/veedor/usuarios/{id}/invitacion/reenvio")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void reenviarInvitacion(@PathVariable String id, HttpServletRequest peticion) {
        reenviar.reenviarInvitacion(new UsuarioId(id), ContextoHttp.de(peticion));
    }
}
