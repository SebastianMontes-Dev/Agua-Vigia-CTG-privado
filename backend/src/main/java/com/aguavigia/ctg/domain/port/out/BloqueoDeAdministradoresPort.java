package com.aguavigia.ctg.domain.port.out;

import java.util.function.Supplier;

/**
 * Exclusión mutua entre los cambios que pueden dejar el sistema sin administradores activos
 * (RF044, Fase 3 de `plan-validacion-backend.md`).
 *
 * Sin esto, dos peticiones concurrentes que suspenden o despromueven a dos ADMIN activos distintos
 * pueden leer el mismo conteo ("quedan 2") antes de que ninguna escriba, y las dos pasan la guarda:
 * el sistema se queda sin nadie que pueda otorgar permisos, aunque ninguna petición individual hizo
 * nada incorrecto por su cuenta. No es un error recuperable desde la aplicación — hay que ir a Mongo
 * a mano — así que se rechaza antes de que ocurra, no después.
 */
public interface BloqueoDeAdministradoresPort {

    /**
     * Ejecuta {@code accion} con el bloqueo adquirido y lo libera al terminar, incluso si
     * {@code accion} lanza. Si no se puede adquirir — otro cambio de administradores en curso, o el
     * almacén no responde — lanza {@link IllegalStateException} sin ejecutar {@code accion}: la
     * operación es segura de reintentar tal cual.
     */
    <T> T ejecutarExclusivo(Supplier<T> accion);
}
