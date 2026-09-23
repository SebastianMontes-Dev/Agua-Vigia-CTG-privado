package com.aguavigia.ctg.domain.port.out;

import java.util.function.Supplier;

/**
 * Agrupa varias escrituras en distintos documentos como una sola unidad atómica (Fase 3 de
 * `plan-validacion-backend.md`): sin esto, un fallo a mitad de camino entre guardar el estado de un
 * sector y anexar su evento a la bitácora dejaba uno hecho y el otro no, sin manera de revertirlo.
 */
public interface TransaccionPort {

    /**
     * Ejecuta {@code accion} dentro de una transacción multi-documento: si lanza, revierte todas las
     * escrituras hechas dentro de ella. Reintenta la unidad completa un número acotado de veces ante
     * una falla transitoria de Mongo (p. ej. un conflicto de escritura entre dos transacciones
     * concurrentes) — nunca reintenta tras un commit exitoso, así que no duplica escrituras.
     */
    <T> T ejecutar(Supplier<T> accion);
}
