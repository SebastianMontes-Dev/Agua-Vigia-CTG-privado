package com.aguavigia.ctg.domain.port.in;

import com.aguavigia.ctg.domain.ClaveEnClaro;
import com.aguavigia.ctg.domain.ContextoDeAccion;
import com.aguavigia.ctg.domain.UsuarioId;

/** Cambiar la propia clave estando dentro, sin pasar por el correo. */
public interface CambiarClaveUseCase {

    /**
     * Exige la clave actual (un token robado no debe bastar para cambiarla) y cierra todas las sesiones
     * vivas, la actual incluida: quien la cambia vuelve a entrar con la nueva.
     *
     * @throws IllegalArgumentException     si la clave actual no coincide o la nueva es igual a la actual
     * @throws com.aguavigia.ctg.domain.CuentaBloqueadaException si la cuenta está bloqueada por intentos fallidos
     */
    void cambiar(UsuarioId usuarioId, String claveActual, ClaveEnClaro claveNueva, ContextoDeAccion contexto);
}
