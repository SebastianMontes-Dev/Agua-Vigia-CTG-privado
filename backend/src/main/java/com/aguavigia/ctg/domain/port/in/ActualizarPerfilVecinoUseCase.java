package com.aguavigia.ctg.domain.port.in;

import com.aguavigia.ctg.domain.CambiosDePerfil;
import com.aguavigia.ctg.domain.ContextoDeAccion;
import com.aguavigia.ctg.domain.Usuario;
import com.aguavigia.ctg.domain.UsuarioId;

public interface ActualizarPerfilVecinoUseCase {

    /**
     * Solo cambia lo que viene; una petición sin cambios devuelve la cuenta como está. Un barrio
     * inexistente se rechaza con {@link IllegalArgumentException}, y cambiar de barrio anula la
     * verificación del anterior.
     */
    Usuario actualizar(UsuarioId vecino, CambiosDePerfil cambios, ContextoDeAccion contexto);
}
