package com.aguavigia.ctg.domain.port.in;

import com.aguavigia.ctg.domain.CorteAgua;
import com.aguavigia.ctg.domain.Pagina;
import com.aguavigia.ctg.domain.SectorId;

public interface ConsultarHistorialDeCortesUseCase {

    /** Cortes del sector, del más reciente al más antiguo. Lanza {@code EntidadNoEncontradaException} si el sector no existe. */
    Pagina<CorteAgua> listar(SectorId sectorId, Integer pagina, Integer tamano);
}
