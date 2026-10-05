package com.aguavigia.ctg.domain;

/**
 * Qué tan sólido es el Índice de Cumplimiento: el porcentaje por sí solo oculta lo que no se pudo medir. Un corte sin cierre no
 * se cuenta a favor ni en contra de Acuacar: se declara.
 *
 * @param cortesSinCierreConfirmado cortes cuya ventana prometida ya terminó y en los que al menos un barrio no tiene cierre
 *                                  (incluidos los que expiraron sin que nadie confirmara el restablecimiento)
 * @param cortesAnulados            cortes que se publicaron por error y se retiraron; no entran al Índice
 */
public record CalidadDelDato(long cortesSinCierreConfirmado, long cortesAnulados) {

    public CalidadDelDato {
        if (cortesSinCierreConfirmado < 0 || cortesAnulados < 0) {
            throw new IllegalArgumentException("Los conteos de calidad del dato no pueden ser negativos");
        }
    }

    public static CalidadDelDato vacia() {
        return new CalidadDelDato(0, 0);
    }
}
