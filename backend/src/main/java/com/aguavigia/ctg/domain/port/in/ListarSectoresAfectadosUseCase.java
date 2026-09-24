package com.aguavigia.ctg.domain.port.in;

import com.aguavigia.ctg.domain.Sector;

import java.util.List;

public interface ListarSectoresAfectadosUseCase {

    /** Sectores con estado verificado distinto de CON_SERVICIO. Los de estado desconocido no aparecen (ADR-014). */
    List<Sector> listar();
}
