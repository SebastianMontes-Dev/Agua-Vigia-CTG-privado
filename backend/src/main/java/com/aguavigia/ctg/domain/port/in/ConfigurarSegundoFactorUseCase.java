package com.aguavigia.ctg.domain.port.in;

import com.aguavigia.ctg.domain.ContextoDeAccion;
import com.aguavigia.ctg.domain.UsuarioId;

/**
 * Alta en dos pasos: primero se genera el secreto y se muestra el QR, y solo cuando la persona
 * devuelve un código correcto se da por bueno. Si se activara en el primer paso, un QR mal
 * escaneado dejaría la cuenta sin forma de entrar.
 */
public interface ConfigurarSegundoFactorUseCase {

    /**
     * Devuelve la URI `otpauth://` para pintar el QR. El secreto queda guardado sin confirmar.
     *
     * {@code codigoActual} solo hace falta cuando la cuenta ya tiene un segundo factor confirmado:
     * rehacer el alta sustituye el secreto, y sin pedir el código vigente un token robado podía
     * dejar la cuenta sin la defensa que lo neutraliza. En un alta primera vez se ignora.
     */
    AltaSegundoFactor iniciar(UsuarioId usuarioId, String codigoActual, ContextoDeAccion contexto);

    /** Devuelve una sesión nueva de alcance completo cuando el alta cierra el paso pendiente. */
    String confirmar(UsuarioId usuarioId, String codigo, ContextoDeAccion contexto);

    void desactivar(UsuarioId usuarioId, String codigo, ContextoDeAccion contexto);

    /** `secreto` se devuelve para poder escribirlo a mano si la cámara falla. */
    record AltaSegundoFactor(String uri, String secreto) {
    }
}
