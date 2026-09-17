package com.aguavigia.ctg.domain.port.out;

import com.aguavigia.ctg.domain.AgregadoDuraciones;
import com.aguavigia.ctg.domain.CorteAgua;
import com.aguavigia.ctg.domain.CorteId;
import com.aguavigia.ctg.domain.EstadoCorte;
import com.aguavigia.ctg.domain.OrigenCorte;
import com.aguavigia.ctg.domain.PuntoAgregadoMensual;
import com.aguavigia.ctg.domain.SectorId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CorteAguaRepository {

    Optional<CorteAgua> buscarPorId(CorteId id);

    List<CorteAgua> listarPorSector(SectorId sectorId);

    /** Todo corte que afecte a CUALQUIERA de estos sectores, en una sola consulta (`$in`). */
    List<CorteAgua> listarPorSectores(List<SectorId> sectorIds);

    /** Todos los cortes, sin filtrar por sector — insumo del índice global (RF021). */
    List<CorteAgua> listarTodos();

    CorteAgua guardar(CorteAgua corte);

    /**
     * Anexa {@code sectorId} a `sectoresAfectados` del corte {@code id} de forma atómica
     * (equivalente a {@code $addToSet}), creando el documento con los demás campos si todavía no
     * existe. Reemplaza el patrón leer→modificar→guardar: dos aprobaciones del mismo boletín
     * procesadas a la vez (varios veedores en la cola, `ADR-039`) ya no pueden pisarse una lista de
     * sectores calculada en memoria — Mongo serializa las escrituras sobre un mismo documento.
     */
    void anexarSectorAlCorte(CorteId id, SectorId sectorId, Instant inicio, Instant finPrometido,
                              String causa, OrigenCorte origen, EstadoCorte estado);

    /**
     * Suma de duraciones (prometida/real) de los cortes cerrados, calculada por el adaptador sin
     * traer cada corte a memoria. {@code sectorId} nulo agrega todos los sectores (índice global,
     * RF021); con valor, solo los cortes que afectan ese sector.
     */
    AgregadoDuraciones agregarCerrados(SectorId sectorId);

    /**
     * La misma agregación de {@link #agregarCerrados}, agrupada por mes en hora de Cartagena
     * (RF024). {@code desde}/{@code hasta} acotan por la hora real de restablecimiento; cualquiera
     * de los dos puede ser nulo para no acotar por ese extremo. Resultado ordenado cronológicamente.
     */
    List<PuntoAgregadoMensual> agregarCerradosPorMes(SectorId sectorId, Instant desde, Instant hasta);
}
