package com.aguavigia.ctg.infrastructure.scheduling;

import java.time.Duration;

/**
 * Garantiza que una tarea programada corra en UNA sola réplica por ciclo. Con varias instancias
 * cada una dispara sus propios `@Scheduled`, y sin esto el ciclo de ingesta corría N veces por
 * intervalo y generaba propuestas duplicadas.
 */
@FunctionalInterface
public interface EjecucionUnica {

    /**
     * @param nombre         identifica la tarea; dos tareas con nombres distintos no se estorban
     * @param bloqueoMaximo  cuánto dura el bloqueo si la réplica que lo tiene muere a mitad de la tarea
     * @param bloqueoMinimo  cuánto se retiene el bloqueo como mínimo aunque la tarea termine antes: sin
     *                       él, un job de 50 ms disparado por dos réplicas con unos cientos de ms de
     *                       diferencia correría dos veces
     * @param tarea          lo que se ejecuta si esta réplica consigue el bloqueo
     */
    void ejecutar(String nombre, Duration bloqueoMaximo, Duration bloqueoMinimo, Runnable tarea);
}
