package com.aguavigia.ctg.domain.port.in;

import com.aguavigia.ctg.domain.ReportesPendientes;

/** RF018 — la cola de moderación del veedor, con la señal de red de cada reporte. */
public interface ListarReportesPendientesUseCase {

    ReportesPendientes listar(int pagina, int tamano);
}
