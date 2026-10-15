package com.aguavigia.ctg.domain;

/**
 * El Índice de Cumplimiento calculado por las vistas SQL. Misma fórmula que {@code CalcularCumplimientoService} (ADR-022):
 * se suman duraciones, no se promedian porcentajes. {@code sectorId} nulo = toda la ciudad.
 */
public record CumplimientoAnalitico(String sectorId, String nombre, long paresCerrados, long prometidaSegundos,
                                    long realSegundos, long desviacionSegundos, double porcentajeCumplimiento) {
}
