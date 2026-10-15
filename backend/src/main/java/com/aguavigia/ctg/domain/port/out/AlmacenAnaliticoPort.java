package com.aguavigia.ctg.domain.port.out;

import com.aguavigia.ctg.domain.ConteosAnaliticos;
import com.aguavigia.ctg.domain.FilaCorteAnalitica;
import com.aguavigia.ctg.domain.FilaSectorAnalitica;
import com.aguavigia.ctg.domain.ResultadoDeCopia;

import java.time.Instant;
import java.util.List;

/**
 * Donde se escribe la copia analítica (PostgreSQL). Es derivada: se llena leyendo de Mongo, Mongo nunca lee de aquí, y si se
 * borra entera se reconstruye. Todo método puede lanzar {@link com.aguavigia.ctg.domain.AnaliticaNoDisponibleException} si la
 * base no responde.
 */
public interface AlmacenAnaliticoPort {

    /** Anota que empieza una copia y devuelve su id. Prepara el esquema si hace falta. */
    long registrarInicioDeCopia(Instant ahora);

    /**
     * Deja la base igual que Mongo en una sola transacción: inserta o actualiza cada barrio y cada corte, reemplaza los pares
     * corte-barrio y quita lo que ya no existe. Repetirla no duplica nada. Devuelve lo que quedó, contado dentro de la misma
     * transacción; si algo falla, vuelve atrás y queda la copia anterior.
     */
    ConteosAnaliticos reemplazar(List<FilaSectorAnalitica> sectores, List<FilaCorteAnalitica> cortes, Instant copiadoEn);

    /** Cierra la fila de la copia con su resultado; los conteos de Mongo o de PostgreSQL pueden ser nulos si no se tomaron. */
    void cerrarCopia(long id, Instant terminadaEn, ResultadoDeCopia resultado, ConteosAnaliticos enMongo,
                     ConteosAnaliticos enSql, String detalle);
}
