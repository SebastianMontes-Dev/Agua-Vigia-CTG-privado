package com.aguavigia.ctg.domain.port.in;

import com.aguavigia.ctg.domain.ContextoDeAccion;
import com.aguavigia.ctg.domain.CorteAgua;
import com.aguavigia.ctg.domain.CorteId;
import com.aguavigia.ctg.domain.SectorId;

import java.time.Instant;

/**
 * RF016-RF017 — el veedor registra un corte, lo cierra barrio por barrio (o todo de una vez), confirma los
 * cierres que solo sostenían los vecinos y anula lo que se publicó por error.
 */
public interface GestionarCorteOficialUseCase {

    CorteAgua registrar(CorteAgua corte);

    /** El atajo: restablece de una vez todos los barrios que siguen pendientes. */
    CorteAgua cerrar(CorteId corteId, Instant horaReal);

    /** Los barrios se restablecen a horas distintas: cierra solo {@code sectorId}. */
    CorteAgua cerrarSector(CorteId corteId, SectorId sectorId, Instant horaReal);

    /** Confirma —o corrige la hora de— un cierre que solo sostenían los vecinos o los sensores. */
    CorteAgua confirmarCierre(CorteId corteId, SectorId sectorId, Instant horaReal);

    /** Anula un corte publicado por error: queda con su motivo y fuera del Índice y de las estadísticas. */
    CorteAgua anular(CorteId corteId, String motivo, ContextoDeAccion contexto);
}
