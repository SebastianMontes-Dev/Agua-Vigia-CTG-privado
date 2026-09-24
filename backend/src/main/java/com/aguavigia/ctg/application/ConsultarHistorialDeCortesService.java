package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.CorteAgua;
import com.aguavigia.ctg.domain.EntidadNoEncontradaException;
import com.aguavigia.ctg.domain.Pagina;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.port.in.ConsultarHistorialDeCortesUseCase;
import com.aguavigia.ctg.domain.port.out.CorteAguaRepository;
import com.aguavigia.ctg.domain.port.out.SectorRepository;

import java.util.Comparator;
import java.util.List;

public class ConsultarHistorialDeCortesService implements ConsultarHistorialDeCortesUseCase {

    private final SectorRepository sectores;
    private final CorteAguaRepository cortes;

    public ConsultarHistorialDeCortesService(SectorRepository sectores, CorteAguaRepository cortes) {
        this.sectores = sectores;
        this.cortes = cortes;
    }

    @Override
    public Pagina<CorteAgua> listar(SectorId sectorId, Integer pagina, Integer tamano) {
        sectores.buscarPorId(sectorId).orElseThrow(() ->
                new EntidadNoEncontradaException("No existe el sector '" + sectorId.valor() + "'"));

        List<CorteAgua> ordenados = cortes.listarPorSector(sectorId).stream()
                .sorted(Comparator.comparing((CorteAgua corte) -> corte.ventana().inicio()).reversed())
                .toList();
        return Pagina.deLista(ordenados, Pagina.paginaValida(pagina), Pagina.tamanoValido(tamano));
    }
}
