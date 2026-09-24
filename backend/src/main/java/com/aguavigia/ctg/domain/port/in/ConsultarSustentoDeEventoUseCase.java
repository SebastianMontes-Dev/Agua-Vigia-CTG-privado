package com.aguavigia.ctg.domain.port.in;

import com.aguavigia.ctg.domain.EventoId;
import com.aguavigia.ctg.domain.Pagina;
import com.aguavigia.ctg.domain.ReporteId;

public interface ConsultarSustentoDeEventoUseCase {

    /** Reportes que sostuvieron un evento de consenso (RF011). Lanza {@code EntidadNoEncontradaException} si no existe el evento. */
    Pagina<ReporteId> sustento(EventoId eventoId, Integer pagina, Integer tamano);
}
