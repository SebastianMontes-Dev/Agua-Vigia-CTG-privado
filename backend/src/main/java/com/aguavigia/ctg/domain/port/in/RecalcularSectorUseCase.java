package com.aguavigia.ctg.domain.port.in;

import com.aguavigia.ctg.domain.EstadoPublicado;
import com.aguavigia.ctg.domain.SectorId;

/**
 * La única forma de mover el estado de un barrio: reúne lo que cada fuente afirma, deja que el
 * resolutor decida y escribe una sola vez. Un reporte, un boletín aprobado, un corte del veedor, una
 * moderación y el paso del tiempo son solo motivos para volver a preguntar.
 */
public interface RecalcularSectorUseCase {

    /** Devuelve lo que quedó publicado del barrio, haya cambiado o no. */
    EstadoPublicado recalcular(SectorId sectorId);
}
