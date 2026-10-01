package com.aguavigia.ctg.domain.port.in;

/**
 * Cierra el ciclo de un corte que nadie confirmó: pasado el plazo de expiración desde el fin prometido
 * pasa a EXPIRADO, el barrio vuelve a «sin datos» y la bitácora lo dice. Sin esto un corte quedaba abierto
 * para siempre y el Índice de Cumplimiento nunca sabía que existió.
 */
public interface ExpirarCortesVencidosUseCase {

    /** @return cuántos cortes se expiraron en esta pasada. */
    int expirarVencidos();
}
