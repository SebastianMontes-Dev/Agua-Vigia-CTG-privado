package com.aguavigia.ctg.domain.port.in;

import com.aguavigia.ctg.domain.ContextoDeAccion;
import com.aguavigia.ctg.domain.PropuestaId;
import com.aguavigia.ctg.domain.PropuestaIngesta;

/** M9 — el veedor decide si una propuesta de la ingesta llega o no al mapa público (RF018, M5). */
public interface RevisarPropuestaIngestaUseCase {

    /** Guarda la propuesta como aprobada, registra el corte del boletín y recalcula el estado del barrio (RF026). */
    PropuestaIngesta aprobar(PropuestaId id);

    /** No toca el sector: la propuesta queda archivada como descartada, no se borra. */
    PropuestaIngesta descartar(PropuestaId id);

    /**
     * Deshace una aprobación por error: la propuesta queda ANULADA con su motivo, deja de afirmar nada del
     * presente, se anexa un evento de corrección a la bitácora y se recalcula el barrio.
     */
    PropuestaIngesta anular(PropuestaId id, String motivo, ContextoDeAccion contexto);
}
