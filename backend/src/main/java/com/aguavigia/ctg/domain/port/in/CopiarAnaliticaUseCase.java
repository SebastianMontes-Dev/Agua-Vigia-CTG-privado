package com.aguavigia.ctg.domain.port.in;

import com.aguavigia.ctg.domain.ResultadoDeCopia;

/**
 * Pasa a la base analítica (PostgreSQL) una copia de los barrios y los cortes de Mongo. Mongo sigue siendo la única fuente de
 * verdad: la copia es derivada, se puede borrar y reconstruir, y un fallo aquí no puede propagarse al resto del sistema.
 */
public interface CopiarAnaliticaUseCase {

    /**
     * Hace una copia completa y la anota. Nunca lanza: si PostgreSQL no responde o la copia falla, lo registra (si puede) y devuelve
     * {@link ResultadoDeCopia#FALLIDA}, y la copia anterior queda intacta.
     */
    ResultadoDeCopia copiar();
}
