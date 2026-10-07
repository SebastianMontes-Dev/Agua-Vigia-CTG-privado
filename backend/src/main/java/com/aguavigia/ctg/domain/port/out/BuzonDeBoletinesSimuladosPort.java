package com.aguavigia.ctg.domain.port.out;

import com.aguavigia.ctg.domain.BoletinSimulado;

/** Donde espera un boletín simulado hasta que el ciclo de ingesta lo lee. Solo existe en la instancia de simulación. */
public interface BuzonDeBoletinesSimuladosPort {

    void encolar(BoletinSimulado boletin);

    /** Descarta lo que esperaba: lo usa el reinicio de la simulación. */
    void vaciar();
}
