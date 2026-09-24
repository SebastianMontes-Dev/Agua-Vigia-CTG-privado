package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.EstadoServicio;
import com.aguavigia.ctg.domain.Sector;
import com.aguavigia.ctg.domain.port.in.ListarSectoresAfectadosUseCase;
import com.aguavigia.ctg.domain.port.out.SectorRepository;

import java.util.List;

public class ListarSectoresAfectadosService implements ListarSectoresAfectadosUseCase {

    private final SectorRepository sectores;

    public ListarSectoresAfectadosService(SectorRepository sectores) {
        this.sectores = sectores;
    }

    @Override
    public List<Sector> listar() {
        return sectores.listarTodos().stream()
                .filter(sector -> sector.estadoActual() != null
                        && sector.estadoActual() != EstadoServicio.CON_SERVICIO)
                .toList();
    }
}
