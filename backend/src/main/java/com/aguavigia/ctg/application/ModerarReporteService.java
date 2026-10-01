package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.EntidadNoEncontradaException;
import com.aguavigia.ctg.domain.ReporteCiudadano;
import com.aguavigia.ctg.domain.ReporteId;
import com.aguavigia.ctg.domain.port.in.ModerarReporteUseCase;
import com.aguavigia.ctg.domain.port.in.RecalcularSectorUseCase;
import com.aguavigia.ctg.domain.port.out.ReporteCiudadanoRepository;
import com.aguavigia.ctg.domain.port.out.TransaccionPort;

/**
 * RF018 (`ADR-023`) — la definición de "candidato a moderar" es el enum. Descartar un reporte sí tiene
 * consecuencia sobre el mapa: si el barrio cambió de estado por un quórum que incluía reportes de un
 * abusador, al descartarlos se vuelve a comprobar que los demás lo sostienen (D23). El descarte y esa
 * comprobación son una sola unidad: con el descarte confirmado y la comprobación fallida, el estado que
 * sostenía el abusador seguiría publicado y nada lo revisaría hasta que caduque.
 */
public class ModerarReporteService implements ModerarReporteUseCase {

    private final ReporteCiudadanoRepository reportes;
    private final RecalcularSectorUseCase recalcular;
    private final TransaccionPort transaccion;

    public ModerarReporteService(ReporteCiudadanoRepository reportes, RecalcularSectorUseCase recalcular,
                                 TransaccionPort transaccion) {
        this.reportes = reportes;
        this.recalcular = recalcular;
        this.transaccion = transaccion;
    }

    @Override
    public ReporteCiudadano aprobar(ReporteId id) {
        ReporteCiudadano reporte = buscarOLanzar(id);
        return reportes.guardar(reporte.aprobar());
    }

    @Override
    public ReporteCiudadano descartar(ReporteId id) {
        ReporteCiudadano reporte = buscarOLanzar(id);
        return transaccion.ejecutar(() -> {
            ReporteCiudadano descartado = reportes.guardar(reporte.descartar());
            recalcular.reevaluarTrasDescarte(descartado.sectorId());
            return descartado;
        });
    }

    private ReporteCiudadano buscarOLanzar(ReporteId id) {
        return reportes.buscarPorId(id)
                .orElseThrow(() -> new EntidadNoEncontradaException("No existe el reporte '" + id.valor() + "'"));
    }
}
