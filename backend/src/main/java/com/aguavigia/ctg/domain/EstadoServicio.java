package com.aguavigia.ctg.domain;

/**
 * DESIGN.md §2 — los cuatro colores reservados al estado del servicio. No agregar un quinto
 * valor sin actualizar también la paleta visual.
 */
public enum EstadoServicio {
    CON_SERVICIO,
    SIN_SERVICIO,
    PRESION_BAJA,
    CORTE_PROGRAMADO;

    /**
     * El más severo de los dos, para cuando dos fuentes (avisos solapados, o un aviso y un corte
     * oficial abierto) proponen un estado distinto para el mismo sector a la vez. Conmutativo y
     * asociativo a propósito: al plegar una lista de candidatos con este método, el resultado no
     * depende del orden en que lleguen — condición de la Fase 2 del plan de validación
     * (`docs/ingenieria/plan-validacion-backend.md`).
     *
     * Orden: SIN_SERVICIO &gt; CORTE_PROGRAMADO &gt; PRESION_BAJA &gt; CON_SERVICIO. Un falso "hay
     * agua" es peor que un falso "no hay agua" (`ADR-014`, `ADR-006`), así que ante candidatos en
     * conflicto gana el que más se aleja de CON_SERVICIO.
     */
    public static EstadoServicio masSevero(EstadoServicio a, EstadoServicio b) {
        return severidad(a) >= severidad(b) ? a : b;
    }

    private static int severidad(EstadoServicio estado) {
        return switch (estado) {
            case SIN_SERVICIO -> 3;
            case CORTE_PROGRAMADO -> 2;
            case PRESION_BAJA -> 1;
            case CON_SERVICIO -> 0;
        };
    }
}
