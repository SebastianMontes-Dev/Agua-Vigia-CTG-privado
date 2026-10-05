package com.aguavigia.ctg.domain;

import java.time.Duration;

/**
 * Salida de CalcularCumplimientoUseCase (RF020-RF022). sectorId nulo = índice global de la
 * ciudad. Se presenta como comparación explícita (prometida vs. real), nunca como puntaje
 * aislado — DESIGN.md exige que ambos números viajen juntos.
 */
public record IndiceCumplimiento(
        SectorId sectorId,
        Duration duracionPrometida,
        Duration duracionReal,
        Duration desviacion,
        double porcentajeCumplimiento,
        /** De los cierres que sostienen este índice, qué porcentaje solo lo sostienen vecinos o sensores (0 a 100). */
        double porcentajeProvisional,
        /** Cortes cuya ventana ya terminó y que no tienen cierre en algún barrio: no se cuentan a favor ni en contra. */
        long cortesSinCierreConfirmado,
        /** Cortes publicados por error y retirados: no entran al índice. */
        long cortesAnulados) {

    /** Sin calidad del dato: lo que existía antes de declararla. */
    public IndiceCumplimiento(SectorId sectorId, Duration duracionPrometida, Duration duracionReal, Duration desviacion,
                              double porcentajeCumplimiento) {
        this(sectorId, duracionPrometida, duracionReal, desviacion, porcentajeCumplimiento, 0, 0, 0);
    }

    public IndiceCumplimiento {
        if (duracionPrometida == null || duracionReal == null) {
            throw new IllegalArgumentException("Duración prometida y real son obligatorias");
        }
        if (desviacion == null) {
            throw new IllegalArgumentException("La desviación es obligatoria");
        }
    }
}
