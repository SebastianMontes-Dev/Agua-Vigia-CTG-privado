package com.aguavigia.ctg.domain.port.in;

import com.aguavigia.ctg.domain.ResultadoDeRecalculo;
import com.aguavigia.ctg.domain.SectorId;

/**
 * La única forma de mover el estado de un barrio: reúne lo que cada fuente afirma, deja que el
 * resolutor decida y escribe una sola vez. Un reporte, un boletín aprobado, un corte del veedor, una
 * moderación y el paso del tiempo son solo motivos para volver a preguntar.
 */
public interface RecalcularSectorUseCase {

    /** Devuelve lo que quedó publicado del barrio, si este recálculo movió el estado y qué reportes lo sustentan. */
    ResultadoDeRecalculo recalcular(SectorId sectorId);

    /**
     * Igual que {@link #recalcular}, pero vuelve a comprobar que los reportes que siguen siendo válidos
     * sostienen el estado que los vecinos habían fijado: el barrio recuerda ese estado aunque los reportes
     * salgan de la ventana, y descartar los de un abusador no puede dejarlo publicado. Si deja de sostenerse
     * anexa a la bitácora que el consenso se revirtió.
     */
    ResultadoDeRecalculo reevaluarTrasDescarte(SectorId sectorId);
}
