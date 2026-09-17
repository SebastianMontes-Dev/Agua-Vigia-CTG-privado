package com.aguavigia.ctg.domain;

/**
 * El JWT sigue siendo válido, pero la cuenta que respaldaba la sesión ya no existe (borrada, o su
 * rol eliminado después de emitirse el token). No es un conflicto de estado del recurso (409):
 * es que la sesión dejó de ser válida, el mismo caso que cualquier otro fallo de autenticación —
 * el frontend debe reaccionar igual que ante un 401, con logout.
 */
public class SesionSinCuentaException extends RuntimeException {

    public SesionSinCuentaException(String mensaje) {
        super(mensaje);
    }
}
