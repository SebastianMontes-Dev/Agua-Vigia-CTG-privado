package com.aguavigia.ctg.domain.port.in;

import com.aguavigia.ctg.domain.ReporteCiudadano;
import com.aguavigia.ctg.domain.ReporteId;

public interface DescartarFotoUseCase {

    /**
     * El veedor descarta la foto de un reporte sin descartar el reporte: deja de servirse al público y el panel
     * sigue viéndola. Un reporte sin foto no tiene nada que descartar (conflicto).
     */
    ReporteCiudadano descartarFoto(ReporteId reporte);
}
