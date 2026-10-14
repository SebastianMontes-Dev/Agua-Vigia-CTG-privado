package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.CorteAgua;
import com.aguavigia.ctg.domain.CorteId;
import com.aguavigia.ctg.domain.port.in.ListarCortesVencidosUseCase;
import com.aguavigia.ctg.domain.port.out.CorteAguaRepository;
import com.aguavigia.ctg.domain.port.out.RelojPort;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class ListarCortesVencidosService implements ListarCortesVencidosUseCase {

    private final CorteAguaRepository cortes;
    private final RelojPort reloj;

    public ListarCortesVencidosService(CorteAguaRepository cortes, RelojPort reloj) {
        this.cortes = cortes;
        this.reloj = reloj;
    }

    @Override
    public List<CorteAgua> listar() {
        Instant ahora = reloj.ahora();
        Stream<CorteAgua> promesaVencida = cortes.listarAbiertos().stream()
                .filter(corte -> !ahora.isBefore(corte.ventana().finPrometido()));

        Map<CorteId, CorteAgua> sinRepetir = new LinkedHashMap<>();
        Stream.concat(promesaVencida, cortes.listarConCierresProvisionales().stream())
                .forEach(corte -> sinRepetir.putIfAbsent(corte.id(), corte));
        return sinRepetir.values().stream()
                .sorted(Comparator.comparing((CorteAgua corte) -> corte.ventana().finPrometido())
                        .thenComparing(corte -> corte.id().valor()))
                .toList();
    }
}
