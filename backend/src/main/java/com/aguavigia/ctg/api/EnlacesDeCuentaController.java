package com.aguavigia.ctg.api;

import com.aguavigia.ctg.domain.ClaveEnClaro;
import com.aguavigia.ctg.domain.port.in.AceptarInvitacionUseCase;
import com.aguavigia.ctg.domain.port.in.RestablecerClaveUseCase;
import com.aguavigia.ctg.domain.port.in.VerificarCorreoUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Las pantallas a las que llevan los enlaces de los correos de cuenta (verificar el correo, aceptar
 * una invitación, restablecer la clave). Existen porque el sitio que antes las pintaba se retiró
 * (ADR-048) y el correo necesita apuntar a algo que abra en un navegador.
 *
 * Es solo transporte, igual que {@link CuentaPublicaController}: cada `POST` delega en el mismo
 * caso de uso que el endpoint JSON equivalente. El `GET` únicamente muestra el formulario y no
 * consume el token — ver {@link PaginaDeCortesia#formulario}.
 */
@Tag(name = "Cuentas", description = "Páginas HTML a las que llevan los enlaces de los correos de cuenta")
@RestController
@RequestMapping(value = "/api/cuentas/enlaces", produces = MediaType.TEXT_HTML_VALUE)
public class EnlacesDeCuentaController {

    private static final String RUTA = "/api/cuentas/enlaces/";

    private final VerificarCorreoUseCase verificar;
    private final AceptarInvitacionUseCase aceptarInvitacion;
    private final RestablecerClaveUseCase restablecer;

    public EnlacesDeCuentaController(VerificarCorreoUseCase verificar,
                                     AceptarInvitacionUseCase aceptarInvitacion,
                                     RestablecerClaveUseCase restablecer) {
        this.verificar = verificar;
        this.aceptarInvitacion = aceptarInvitacion;
        this.restablecer = restablecer;
    }

    @Operation(summary = "Pantalla del enlace «Confirmar mi correo»",
            description = "Muestra el botón de confirmación. No consume el token: eso ocurre al enviar el formulario.")
    @GetMapping("/verificar")
    public ResponseEntity<String> pantallaVerificar(@RequestParam String token) {
        return PaginaDeCortesia.formulario(HttpStatus.OK, "Confirma tu correo",
                "Un paso más: confirma que esta dirección es tuya.",
                RUTA + "verificar", token, false, "Confirmar mi correo");
    }

    @Operation(summary = "Confirmar el correo desde el formulario de la pantalla",
            description = "Mismo efecto que POST /api/cuentas/verificacion, pero recibe el formulario y responde HTML.")
    @PostMapping(value = "/verificar", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<String> verificarCorreo(@RequestParam String token, HttpServletRequest peticion) {
        try {
            verificar.verificar(token, ContextoHttp.de(peticion));
            return PaginaDeCortesia.resultado("Correo confirmado",
                    "Listo. Un administrador revisará tu solicitud y te avisaremos por correo cuando decida.", true);
        } catch (IllegalArgumentException enlaceInvalido) {
            return PaginaDeCortesia.resultado("No pudimos confirmar tu correo", enlaceInvalido.getMessage(), false);
        }
    }

    @Operation(summary = "Pantalla del enlace de invitación",
            description = "Muestra el formulario para elegir la clave. No consume el token.")
    @GetMapping("/invitacion")
    public ResponseEntity<String> pantallaInvitacion(@RequestParam String token) {
        return PaginaDeCortesia.formulario(HttpStatus.OK, "Crea tu clave",
                "Elige la clave con la que entrarás al panel del veedor.",
                RUTA + "invitacion", token, true, "Crear mi clave");
    }

    @Operation(summary = "Aceptar la invitación desde el formulario de la pantalla",
            description = "Mismo efecto que POST /api/cuentas/invitacion, pero recibe el formulario y responde HTML.")
    @PostMapping(value = "/invitacion", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<String> aceptarInvitacion(@RequestParam String token, @RequestParam String clave,
                                                    HttpServletRequest peticion) {
        try {
            aceptarInvitacion.aceptar(token, new ClaveEnClaro(clave), ContextoHttp.de(peticion));
            return PaginaDeCortesia.resultado("Cuenta activada",
                    "Tu clave quedó fijada. Ya puedes iniciar sesión en el panel del veedor.", true);
        } catch (IllegalArgumentException rechazada) {
            return PaginaDeCortesia.formulario(HttpStatus.BAD_REQUEST, "No pudimos activar tu cuenta",
                    rechazada.getMessage(), RUTA + "invitacion", token, true, "Crear mi clave");
        }
    }

    @Operation(summary = "Pantalla del enlace para restablecer la clave",
            description = "Muestra el formulario para elegir la clave nueva. No consume el token.")
    @GetMapping("/restablecer")
    public ResponseEntity<String> pantallaRestablecer(@RequestParam String token) {
        return PaginaDeCortesia.formulario(HttpStatus.OK, "Elige una clave nueva",
                "Al cambiarla se cerrarán todas las sesiones abiertas de tu cuenta.",
                RUTA + "restablecer", token, true, "Cambiar mi clave");
    }

    @Operation(summary = "Restablecer la clave desde el formulario de la pantalla",
            description = "Mismo efecto que POST /api/cuentas/clave, pero recibe el formulario y responde HTML.")
    @PostMapping(value = "/restablecer", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<String> restablecerClave(@RequestParam String token, @RequestParam String clave,
                                                   HttpServletRequest peticion) {
        try {
            restablecer.restablecer(token, new ClaveEnClaro(clave), ContextoHttp.de(peticion));
            return PaginaDeCortesia.resultado("Clave cambiada",
                    "Tu clave nueva ya está activa. Vuelve a iniciar sesión en el panel del veedor.", true);
        } catch (IllegalArgumentException rechazada) {
            return PaginaDeCortesia.formulario(HttpStatus.BAD_REQUEST, "No pudimos cambiar tu clave",
                    rechazada.getMessage(), RUTA + "restablecer", token, true, "Cambiar mi clave");
        }
    }
}
