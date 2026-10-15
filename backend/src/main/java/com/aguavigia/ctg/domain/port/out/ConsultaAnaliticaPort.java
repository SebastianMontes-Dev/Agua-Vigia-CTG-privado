package com.aguavigia.ctg.domain.port.out;

import com.aguavigia.ctg.domain.CumplimientoAnalitico;
import com.aguavigia.ctg.domain.PuntoMensualAnalitico;
import com.aguavigia.ctg.domain.RegistroDeCopia;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Las lecturas de la copia analítica. Es una lectura sin regla de negocio, así que el controlador depende de este puerto
 * directamente (ADR-015). Todo método lanza {@link com.aguavigia.ctg.domain.AnaliticaNoDisponibleException} si la copia está
 * apagada, la base no responde o todavía no se hizo la primera copia.
 */
public interface ConsultaAnaliticaPort {

    /** El Índice de toda la ciudad. */
    CumplimientoAnalitico cumplimientoGlobal();

    /** El Índice de un barrio, o vacío si el barrio no existe en la copia. */
    Optional<CumplimientoAnalitico> cumplimientoDe(String sectorId);

    /** La serie por mes, en hora de Cartagena, de más antiguo a más reciente. */
    List<PuntoMensualAnalitico> cumplimientoMensual();

    /** Cuándo terminó la última copia que cuadró con Mongo, o vacío si todavía no hay ninguna. */
    Optional<Instant> ultimaCopiaOk();

    /** Las últimas copias, de la más reciente a la más antigua. */
    List<RegistroDeCopia> ultimasCopias(int limite);
}
