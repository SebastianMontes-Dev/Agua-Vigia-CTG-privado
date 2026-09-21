package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.AccionAuditada;
import com.aguavigia.ctg.domain.ClaveEnClaro;
import com.aguavigia.ctg.domain.ContextoDeAccion;
import com.aguavigia.ctg.domain.CuentaBloqueadaException;
import com.aguavigia.ctg.domain.Usuario;
import com.aguavigia.ctg.domain.UsuarioId;
import com.aguavigia.ctg.domain.port.in.CambiarClaveUseCase;
import com.aguavigia.ctg.domain.port.out.CifradorClavePort;
import com.aguavigia.ctg.domain.port.out.ControlIntentosPort;
import com.aguavigia.ctg.domain.port.out.NotificacionCuentaPort;
import com.aguavigia.ctg.domain.port.out.RelojPort;
import com.aguavigia.ctg.domain.port.out.RevocacionSesionPort;
import com.aguavigia.ctg.domain.port.out.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * Cambio de clave con la sesión iniciada. Comparte el contador de intentos fallidos con el inicio de
 * sesión (misma clave de bloqueo: el correo), de modo que quien tiene un token robado no puede usar
 * este endpoint para adivinar la clave actual sin freno. El bloqueo se mira antes que nada, por el mismo
 * motivo que en {@link AutenticarUsuarioService}: comprobar la clave durante un bloqueo costaría un BCrypt
 * por intento.
 *
 * Revoca todas las sesiones, la actual incluida: si alguien más tenía un token, deja de servir.
 */
@Service
public class CambiarClaveService implements CambiarClaveUseCase {

    private final UsuarioRepository usuarios;
    private final CifradorClavePort cifrador;
    private final RevocacionSesionPort revocacion;
    private final ControlIntentosPort intentos;
    private final NotificacionCuentaPort notificaciones;
    private final RegistroDeAuditoria auditoria;
    private final RelojPort reloj;
    private final int maximoIntentos;
    private final Duration ventanaIntentos;
    private final Duration bloqueo;

    public CambiarClaveService(UsuarioRepository usuarios,
                               CifradorClavePort cifrador,
                               RevocacionSesionPort revocacion,
                               ControlIntentosPort intentos,
                               NotificacionCuentaPort notificaciones,
                               RegistroDeAuditoria auditoria,
                               RelojPort reloj,
                               @Value("${aguavigia.cuentas.maximo-intentos:5}") int maximoIntentos,
                               @Value("${aguavigia.cuentas.ventana-intentos-minutos:15}") long ventanaIntentosMinutos,
                               @Value("${aguavigia.cuentas.bloqueo-minutos:15}") long bloqueoMinutos) {
        this.usuarios = usuarios;
        this.cifrador = cifrador;
        this.revocacion = revocacion;
        this.intentos = intentos;
        this.notificaciones = notificaciones;
        this.auditoria = auditoria;
        this.reloj = reloj;
        this.maximoIntentos = maximoIntentos;
        this.ventanaIntentos = Duration.ofMinutes(ventanaIntentosMinutos);
        this.bloqueo = Duration.ofMinutes(bloqueoMinutos);
    }

    @Override
    public void cambiar(UsuarioId usuarioId, String claveActual, ClaveEnClaro claveNueva, ContextoDeAccion contexto) {
        Usuario usuario = usuarios.buscarPorId(usuarioId)
                .orElseThrow(() -> new IllegalStateException("La sesión ya no corresponde a una cuenta"));
        String clave = usuario.correo().valor();

        Optional<Duration> bloqueoVigente = intentos.bloqueoVigente(clave);
        if (bloqueoVigente.isPresent()) {
            throw new CuentaBloqueadaException(bloqueoVigente.get(),
                    "Demasiados intentos fallidos. Vuelve a intentarlo en "
                            + Math.max(1, bloqueoVigente.get().toMinutes()) + " minutos.");
        }

        if (usuario.claveHash() == null || !cifrador.coincide(claveActual, usuario.claveHash())) {
            intentos.registrarFallo(clave, ventanaIntentos, maximoIntentos, bloqueo);
            throw new IllegalArgumentException("La clave actual no es correcta.");
        }
        if (claveActual.equals(claveNueva.valor())) {
            throw new IllegalArgumentException("La clave nueva debe ser distinta de la actual.");
        }

        Usuario actualizado = usuarios.guardar(
                usuario.cambiarClave(cifrador.cifrar(claveNueva.valor()), reloj.ahora()));
        revocacion.revocarSesionesAnterioresA(actualizado.id(), reloj.ahora());
        intentos.limpiarIntentos(clave);
        notificaciones.avisarCambioDeAcceso(actualizado, "Tu clave de AguaVigía cambió",
                "Cambiaste la clave de tu cuenta y se cerraron todas las sesiones abiertas. "
                        + "Si no fuiste tú, avisa a un administrador de inmediato.");
        auditoria.registrarConAutor(AccionAuditada.CLAVE_CAMBIADA, actualizado, actualizado,
                "Clave cambiada por su titular con la sesión iniciada; sesiones revocadas", contexto);
    }
}
