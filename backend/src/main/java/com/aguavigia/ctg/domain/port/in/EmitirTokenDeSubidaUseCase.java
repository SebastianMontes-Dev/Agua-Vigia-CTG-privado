package com.aguavigia.ctg.domain.port.in;

import com.aguavigia.ctg.domain.ReporteId;

public interface EmitirTokenDeSubidaUseCase {

    /**
     * El token con el que su autor puede subir la foto de este reporte, una sola vez y durante unos minutos. Se devuelve
     * en claro esta única vez; pedir otro invalida el anterior.
     */
    String emitir(ReporteId reporte);
}
