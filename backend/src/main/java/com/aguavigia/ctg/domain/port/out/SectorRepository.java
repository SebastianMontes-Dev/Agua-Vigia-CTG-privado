package com.aguavigia.ctg.domain.port.out;

import com.aguavigia.ctg.domain.Coordenada;
import com.aguavigia.ctg.domain.EstadoServicio;
import com.aguavigia.ctg.domain.MarcasDeEstado;
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
     * La única escritura que debe usar el resolutor: publica {@code nuevo} con sus {@code marcas}
     * solo si el estado sigue siendo {@code esperado} (compare-and-set), y devuelve si lo hizo.
     * Ambos pueden ser nulos: «sin datos» es un estado válido, y volver a él limpia las fechas y las
     * marcas. Si el estado cambia y no es «sin datos» publica `SectorActualizadoEvent`, que manda
     * correo, push y SSE; si solo cambian las marcas (una disputa que se abre, una promesa que vence)
     * no avisa a nadie ni mueve la fecha del estado.
     */
    boolean publicarSiEs(SectorId id, EstadoServicio esperado, EstadoServicio nuevo, MarcasDeEstado marcas);

    /**
     * Marca que una fuente con autoridad sostuvo el estado sin cambiarlo (ADR-073), solo si el sector
     * sigue en {@code estado}; devuelve si lo marcó. No es un cambio: no avisa a los suscriptores ni
     * anexa nada a la bitácora.
     */
    boolean confirmarEstado(SectorId id, EstadoServicio estado);
}
