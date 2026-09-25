package com.aguavigia.ctg.domain.port.out;

import com.aguavigia.ctg.domain.Coordenada;
import com.aguavigia.ctg.domain.EstadoServicio;
import com.aguavigia.ctg.domain.Sector;
import com.aguavigia.ctg.domain.SectorId;

import java.util.List;
import java.util.Optional;

public interface SectorRepository {

    Optional<Sector> buscarPorId(SectorId id);

    /** RF007 — el sector cuyo polígono contiene la coordenada, o vacío si cae fuera de Cartagena. */
    Optional<Sector> buscarPorCoordenada(Coordenada coordenada);

    List<Sector> listarTodos();

    Sector guardar(Sector sector);

    /**
     * Cambia el estado solo si sigue siendo `esperado` (compare-and-set), y devuelve si lo cambió.
     * El consenso lee el estado, decide y escribe: dos reportes simultáneos leían el mismo estado, los
     * dos "cambiaban" y los dos anexaban su evento a la bitácora. Con esto solo el primero gana.
     * `esperado` puede ser nulo (sector sin estado verificado todavía).
     */
    boolean cambiarEstadoSiEs(SectorId id, EstadoServicio esperado, EstadoServicio nuevo);

    /**
     * Marca que una fuente con autoridad sostuvo el estado sin cambiarlo (ADR-073), solo si el sector
     * sigue en {@code estado}; devuelve si lo marcó. No es un cambio: no avisa a los suscriptores ni
     * anexa nada a la bitácora.
     */
    boolean confirmarEstado(SectorId id, EstadoServicio estado);
}
