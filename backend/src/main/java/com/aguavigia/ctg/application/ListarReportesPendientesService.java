package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.Pagina;
import com.aguavigia.ctg.domain.RedEnRafaga;
import com.aguavigia.ctg.domain.ReporteCiudadano;
import com.aguavigia.ctg.domain.ReportesPendientes;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.port.in.ListarReportesPendientesUseCase;
import com.aguavigia.ctg.domain.port.out.RelojPort;
import com.aguavigia.ctg.domain.port.out.ReporteCiudadanoRepository;

import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * La cola de moderación con la señal de red (D9). Una ráfaga desde una sola red no bloquea nada —sin tope duro por IP: el CGNAT
 * móvil lo haría inalcanzable para gente legítima—, pero distingue el spam de una avería real, así que el veedor mira primero ahí.
 */
public class ListarReportesPendientesService implements ListarReportesPendientesUseCase {

    private final ReporteCiudadanoRepository reportes;
    private final RelojPort reloj;
    private final Duration ventana;
    private final int minimoDeReportes;

    public ListarReportesPendientesService(ReporteCiudadanoRepository reportes, RelojPort reloj, Duration ventana,
                                           int minimoDeReportes) {
        this.reportes = reportes;
        this.reloj = reloj;
        this.ventana = ventana;
        this.minimoDeReportes = minimoDeReportes;
    }

    @Override
    public ReportesPendientes listar(int pagina, int tamano) {
        Pagina<ReporteCiudadano> cola = reportes.listarPendientes(pagina, tamano);
        if (cola.contenido().isEmpty()) {
            return new ReportesPendientes(cola, Set.of());
        }
        Set<SectorId> sectores = cola.contenido().stream().map(ReporteCiudadano::sectorId).collect(Collectors.toSet());
        Set<RedEnRafaga> redes = reportes.redesEnRafaga(sectores, reloj.ahora().minus(ventana), minimoDeReportes);
        return new ReportesPendientes(cola, redes);
    }
}
