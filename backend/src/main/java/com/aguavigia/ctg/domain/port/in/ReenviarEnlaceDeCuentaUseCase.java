package com.aguavigia.ctg.domain.port.in;

import com.aguavigia.ctg.domain.ContextoDeAccion;
import com.aguavigia.ctg.domain.CorreoElectronico;
import com.aguavigia.ctg.domain.UsuarioId;

/**
 * Volver a mandar el correo de una cuenta cuyo enlace no llegó o venció. Cada reenvío invalida el enlace
 * anterior: solo el último sirve.
 */
public interface ReenviarEnlaceDeCuentaUseCase {

    /**
     * Nunca falla ni distingue si el correo existe (igual que {@code RestablecerClaveUseCase#solicitar}): un
     * formulario que dijera «ese correo no está registrado» sería un buscador de cuentas. Solo actúa sobre
     * cuentas que esperan verificar su correo y, como mucho, una vez cada dos minutos por cuenta, para que no
     * sirva de bomba de correo contra la dirección de otra persona.
     */
    void reenviarVerificacion(CorreoElectronico correo, ContextoDeAccion contexto);

    /**
     * Reenvía la invitación a una cuenta que aún no la aceptó.
     *
     * @throws com.aguavigia.ctg.domain.EntidadNoEncontradaException si no existe la cuenta
     * @throws IllegalStateException                                 si la cuenta ya no está en estado INVITADA
     */
    void reenviarInvitacion(UsuarioId sujeto, ContextoDeAccion contexto);
}
