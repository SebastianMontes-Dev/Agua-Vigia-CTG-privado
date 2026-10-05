package com.aguavigia.ctg.domain;

import java.util.Set;

/** Una página de la cola de moderación con la señal de red de cada reporte (D9): dónde mirar primero, no un bloqueo. */
public record ReportesPendientes(Pagina<ReporteCiudadano> pagina, Set<RedEnRafaga> redesEnRafaga) {

    public ReportesPendientes {
        if (pagina == null) {
            throw new IllegalArgumentException("La cola debe traer su página");
        }
        redesEnRafaga = redesEnRafaga == null ? Set.of() : Set.copyOf(redesEnRafaga);
    }

    /** El reporte viene de una red que ya hizo ráfaga en su barrio. Sin red conocida, nunca. */
    public boolean enRafaga(ReporteCiudadano reporte) {
        return reporte.redHash() != null
                && redesEnRafaga.contains(new RedEnRafaga(reporte.sectorId(), reporte.redHash()));
    }
}
