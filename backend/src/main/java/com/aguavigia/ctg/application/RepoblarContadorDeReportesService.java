package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.VotoReciente;
import com.aguavigia.ctg.domain.port.in.RepoblarContadorDeReportesUseCase;
import com.aguavigia.ctg.domain.port.out.ContadorReportesPort;
import com.aguavigia.ctg.domain.port.out.RelojPort;
import com.aguavigia.ctg.domain.port.out.ReporteCiudadanoRepository;

import java.time.Duration;
import java.util.List;

public class RepoblarContadorDeReportesService implements RepoblarContadorDeReportesUseCase {

    private final ReporteCiudadanoRepository reportes;
    private final ContadorReportesPort contador;
    private final RelojPort reloj;
    private final Duration ventana;

    public RepoblarContadorDeReportesService(ReporteCiudadanoRepository reportes, ContadorReportesPort contador,
                                             RelojPort reloj, Duration ventana) {
        this.reportes = reportes;
        this.contador = contador;
        this.reloj = reloj;
        this.ventana = ventana;
    }

    @Override
    public int repoblar() {
        List<VotoReciente> votos = reportes.votosRecientes(reloj.ahora().minus(ventana));
        if (votos.isEmpty()) {
            return 0;
        }
        contador.repoblar(votos);
        return votos.size();
    }
}
