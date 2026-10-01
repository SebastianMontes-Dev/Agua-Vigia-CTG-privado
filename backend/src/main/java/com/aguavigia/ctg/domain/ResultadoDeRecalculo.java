package com.aguavigia.ctg.domain;

import java.util.List;

/**
 * Qué pasó al recalcular un barrio.
 *
 * @param publicado             lo que quedó publicado, haya cambiado o no
 * @param cambioElEstado        este recálculo movió el estado (y no otro proceso que ganó la carrera)
 * @param reportesQueSustentan  los reportes de vecinos que justifican el cambio; vacío si no fue por vecinos
 */
public record ResultadoDeRecalculo(EstadoPublicado publicado, boolean cambioElEstado,
                                   List<ReporteId> reportesQueSustentan) {

    public ResultadoDeRecalculo {
        reportesQueSustentan = List.copyOf(reportesQueSustentan);
    }

    public static ResultadoDeRecalculo sinCambio(EstadoPublicado publicado) {
        return new ResultadoDeRecalculo(publicado, false, List.of());
    }
}
