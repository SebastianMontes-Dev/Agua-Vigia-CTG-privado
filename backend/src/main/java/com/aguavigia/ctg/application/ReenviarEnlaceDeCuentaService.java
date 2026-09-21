package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.AccionAuditada;
import com.aguavigia.ctg.domain.ContextoDeAccion;
import com.aguavigia.ctg.domain.CorreoElectronico;
import com.aguavigia.ctg.domain.EntidadNoEncontradaException;
import com.aguavigia.ctg.domain.EstadoCuenta;
import com.aguavigia.ctg.domain.TipoTokenCuenta;
import com.aguavigia.ctg.domain.Usuario;
import com.aguavigia.ctg.domain.UsuarioId;
import com.aguavigia.ctg.domain.port.in.ReenviarEnlaceDeCuentaUseCase;
import com.aguavigia.ctg.domain.port.out.ControlIntentosPort;
import com.aguavigia.ctg.domain.port.out.NotificacionCuentaPort;
import com.aguavigia.ctg.domain.port.out.UsuarioRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Antes, invitar con el correo caído dejaba la cuenta creada y sin forma de reenviar el enlace, y quien
 * no recibía el de verificación tenía que registrarse otra vez. Reemitir invalida el enlace anterior
 * ({@link EmisorDeTokensDeCuenta#emitir}), así que nunca hay dos enlaces vivos.
 */
@Service
public class ReenviarEnlaceDeCuentaService implements ReenviarEnlaceDeCuentaUseCase {

    private static final Duration ENFRIAMIENTO_VERIFICACION = Duration.ofMinutes(2);

    private final UsuarioRepository usuarios;
    private final EmisorDeTokensDeCuenta emisorDeTokens;
    private final NotificacionCuentaPort notificaciones;
    private final ControlIntentosPort control;
    private final RegistroDeAuditoria auditoria;

    public ReenviarEnlaceDeCuentaService(UsuarioRepository usuarios,
                                         EmisorDeTokensDeCuenta emisorDeTokens,
                                         NotificacionCuentaPort notificaciones,
                                         ControlIntentosPort control,
                                         RegistroDeAuditoria auditoria) {
        this.usuarios = usuarios;
        this.emisorDeTokens = emisorDeTokens;
        this.notificaciones = notificaciones;
        this.control = control;
        this.auditoria = auditoria;
    }

    @Override
    public void reenviarVerificacion(CorreoElectronico correo, ContextoDeAccion contexto) {
        usuarios.buscarPorCorreo(correo.normalizado())
                .filter(usuario -> usuario.estado() == EstadoCuenta.PENDIENTE_VERIFICACION)
                .filter(usuario -> control.consumirPorPrimeraVez(
                        "reenvio-verificacion:" + usuario.id().valor(), ENFRIAMIENTO_VERIFICACION))
                .ifPresent(usuario -> notificaciones.enviarVerificacionDeCorreo(usuario,
                        emisorDeTokens.emitir(usuario.id(), TipoTokenCuenta.VERIFICACION_CORREO)));
    }

    @Override
    public void reenviarInvitacion(UsuarioId sujeto, ContextoDeAccion contexto) {
        Usuario invitado = usuarios.buscarPorId(sujeto)
                .orElseThrow(() -> new EntidadNoEncontradaException("No existe la cuenta '" + sujeto.valor() + "'"));
        if (invitado.estado() != EstadoCuenta.INVITADA) {
            throw new IllegalStateException(
                    "Solo se puede reenviar la invitación de una cuenta que aún no la aceptó (estado actual: "
                            + invitado.estado() + ").");
        }
        Usuario autor = usuarios.buscarPorId(contexto.autorId())
                .orElseThrow(() -> new IllegalStateException("La sesión que reenvía ya no existe"));

        notificaciones.enviarInvitacion(invitado, autor,
                emisorDeTokens.emitir(invitado.id(), TipoTokenCuenta.INVITACION));
        auditoria.registrarConAutor(AccionAuditada.CUENTA_INVITADA, autor, invitado,
                "Invitación reenviada", contexto);
    }
}
