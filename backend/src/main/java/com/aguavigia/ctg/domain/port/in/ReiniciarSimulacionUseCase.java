package com.aguavigia.ctg.domain.port.in;

/**
 * Deja en blanco lo que la simulación guarda en memoria del backend: los boletines que esperaban a la ingesta y el desfase del reloj.
 * Lo que vive en Mongo y Redis lo suelta el simulador. Solo existe en la instancia de simulación.
 */
public interface ReiniciarSimulacionUseCase {

    void reiniciar();
}
