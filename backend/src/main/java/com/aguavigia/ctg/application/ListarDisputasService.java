package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.Sector;
import com.aguavigia.ctg.domain.port.in.ListarDisputasUseCase;
import com.aguavigia.ctg.domain.port.out.SectorRepository;

import java.util.Comparator;
import java.util.List;

public class ListarDisputasService implements ListarDisputasUseCase {

    private final SectorRepository sectores;

    public ListarDisputasService(SectorRepository sectores) {
        this.sectores = sectores;
    }

    @Override
    public List<Sector> listar() {
        return sectores.listarTodos().stream()
                .filter(sector -> sector.marcas().enDisputa())
                .sorted(Comparator.comparingInt((Sector sector) -> sector.marcas().reportesEnContra()).reversed()
                        .thenComparing(sector -> sector.id().valor()))
                .toList();
    }
}
