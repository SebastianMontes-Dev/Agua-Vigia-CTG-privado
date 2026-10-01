package com.aguavigia.ctg.domain.port.in;

import com.aguavigia.ctg.domain.CorteAgua;

import java.util.List;

/**
 * La cola de «vencidos» del veedor: los cortes cuya promesa ya pasó sin cierre y los que tienen un cierre
 * provisional que solo sostienen los vecinos o los sensores y nadie ha confirmado.
 */
public interface ListarCortesVencidosUseCase {

    /** Del más antiguo al más reciente, sin repetir ninguno. */
    List<CorteAgua> listar();
}
