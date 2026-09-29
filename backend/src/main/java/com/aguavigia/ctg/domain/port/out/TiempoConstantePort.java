package com.aguavigia.ctg.domain.port.out;

/**
 * Ejecuta una acción sin que su duración delate qué rama tomó. Es la contraparte de
 * {@link CifradorClavePort#gastarTiempoEquivalente()} para las operaciones cuya diferencia de
 * tiempo no viene del cifrado: en "olvidé mi clave", emitir el token y mandar el correo cuesta
 * más que no hacer nada, y esa diferencia basta para saber qué correos tienen cuenta.
 */
public interface TiempoConstantePort {

    /** Corre la acción y no retorna hasta que haya pasado la duración mínima, falle o no la acción. */
    void ejecutar(Runnable accion);
}
