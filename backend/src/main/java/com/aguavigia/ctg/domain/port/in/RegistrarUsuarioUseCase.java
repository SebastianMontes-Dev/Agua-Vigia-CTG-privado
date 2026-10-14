package com.aguavigia.ctg.domain.port.in;

import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.ClaveEnClaro;
import com.aguavigia.ctg.domain.ContextoDeAccion;
import com.aguavigia.ctg.domain.CorreoElectronico;

/**
 * Alta abierta: cualquiera puede pedir cuenta. Registrarse no concede absolutamente nada — hace
 * falta verificar el correo y que un ADMIN apruebe (ver EstadoCuenta).
 */
public interface RegistrarUsuarioUseCase {

    /**
     * No devuelve el usuario ni indica si el correo ya existía: responder distinto según eso
     * convierte el registro en un buscador de cuentas ajenas. Quien ya tiene cuenta recibe un
     * correo avisándolo; quien no, el enlace de verificación.
     */
    default void registrar(CorreoElectronico correo, String nombre, ClaveEnClaro clave, ContextoDeAccion contexto) {
        registrar(correo, nombre, clave, null, contexto);
    }

    /**
     * `barrio` es opcional. Si viene y no existe se rechaza con {@link IllegalArgumentException}, antes de
     * mirar el correo: así la respuesta no depende de si el correo ya tenía cuenta (RNF024).
     */
    void registrar(CorreoElectronico correo, String nombre, ClaveEnClaro clave, SectorId barrio,
                   ContextoDeAccion contexto);
}
