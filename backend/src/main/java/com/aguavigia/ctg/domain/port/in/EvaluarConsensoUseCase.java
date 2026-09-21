package com.aguavigia.ctg.domain.port.in;

import com.aguavigia.ctg.domain.ResultadoConsenso;
import com.aguavigia.ctg.domain.SectorId;

/** RF009-RF011 — cambia el estado de un sector cuando N reportes independientes coinciden. */
public interface EvaluarConsensoUseCase {

    /**
     * Evalúa el consenso de un sector, salvo que otra petición lo haya evaluado hace menos de un
     * intervalo: entonces lo deja pendiente y responde "no alcanzado" sin consultar nada.
     */
    ResultadoConsenso evaluar(SectorId sectorId);

    /** Evalúa los sectores que quedaron pendientes (los llama un barrido periódico). */
    void evaluarPendientes();
}
