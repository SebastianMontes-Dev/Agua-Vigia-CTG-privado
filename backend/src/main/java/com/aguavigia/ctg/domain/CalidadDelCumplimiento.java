package com.aguavigia.ctg.domain;

/**
 * Lo que sostiene —o no— el Índice de Cumplimiento, sin el porcentaje: cuántos cierres se midieron, cuántos son provisionales y
 * cuántos cortes no se pudieron medir. Existe aparte porque con cero cierres el Índice no se calcula, y esa es justo la
 * situación en la que hay que decir «sin datos suficientes» y cuánto queda sin cierre.
 */
public record CalidadDelCumplimiento(long cierresMedidos, long cierresProvisionales, double porcentajeProvisional,
                                     long cortesSinCierreConfirmado, long cortesAnulados) {
}
