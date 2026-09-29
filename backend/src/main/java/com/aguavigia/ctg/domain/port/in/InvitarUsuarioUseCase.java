package com.aguavigia.ctg.domain.port.in;

import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.ContextoDeAccion;
import com.aguavigia.ctg.domain.CorreoElectronico;
import com.aguavigia.ctg.domain.RolVeedor;
import com.aguavigia.ctg.domain.Usuario;

/** Alta dirigida: el ADMIN crea la cuenta con su rol y la persona solo fija su clave. */
public interface InvitarUsuarioUseCase {

    default Usuario invitar(CorreoElectronico correo, String nombre, RolVeedor rol, ContextoDeAccion contexto) {
        return invitar(correo, nombre, rol, null, contexto);
    }

    /** `barrio` es opcional; si viene y no existe se rechaza con {@link IllegalArgumentException}. */
    Usuario invitar(CorreoElectronico correo, String nombre, RolVeedor rol, SectorId barrio,
                    ContextoDeAccion contexto);
}
