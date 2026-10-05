package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.EntidadNoEncontradaException;
import com.aguavigia.ctg.domain.ReporteCiudadano;
import com.aguavigia.ctg.domain.ReporteId;
import com.aguavigia.ctg.domain.port.in.DescartarFotoUseCase;
import com.aguavigia.ctg.domain.port.out.ReporteCiudadanoRepository;

public class DescartarFotoService implements DescartarFotoUseCase {

    private final ReporteCiudadanoRepository reportes;

    public DescartarFotoService(ReporteCiudadanoRepository reportes) {
        this.reportes = reportes;
    }

    @Override
    public ReporteCiudadano descartarFoto(ReporteId id) {
        ReporteCiudadano reporte = reportes.buscarPorId(id)
                .orElseThrow(() -> new EntidadNoEncontradaException("No existe el reporte '" + id.valor() + "'"));
        if (reporte.fotoUrl() == null) {
            throw new IllegalStateException("El reporte '" + id.valor() + "' no tiene foto que descartar.");
        }
        // La foto nunca vota: descartarla no reevalúa el barrio, solo deja de servirse al público. Se escribe solo ese
        // campo: guardar el documento entero podía revertir una aprobación o una confirmación simultáneas.
        reportes.marcarFotoDescartada(id);
        return reportes.buscarPorId(id).orElse(reporte.descartarFoto());
    }
}
