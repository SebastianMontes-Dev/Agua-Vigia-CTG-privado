package com.aguavigia.ctg.domain.port.in;

/**
 * Vuelve a calcular el estado de todos los barrios desde lo que hay guardado (reportes, cortes y
 * boletines), no desde lo que Redis recuerda. Es lo que hace que, tras un reinicio o un Redis vaciado,
 * el mapa refleje lo que ya llegó.
 */
public interface PonerAlDiaSectoresUseCase {

    /** @return cuántos barrios cambiaron de estado en esta pasada. */
    int ponerAlDia();
}
