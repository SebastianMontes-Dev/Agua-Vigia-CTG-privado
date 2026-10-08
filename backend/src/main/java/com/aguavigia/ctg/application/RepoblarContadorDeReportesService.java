package com.aguavigia.ctg.application;

import com.aguavigia.ctg.compartido.reloj.Reloj;
import com.aguavigia.ctg.domain.VotoReciente;
import com.aguavigia.ctg.domain.port.in.RepoblarContadorDeReportesUseCase;
import com.aguavigia.ctg.domain.port.out.ContadorReportesPort;
import com.aguavigia.ctg.domain.port.out.ReporteCiudadanoRepository;

import java.time.Duration;
import java.util.List;

public class RepoblarContadorDeReportesService implements RepoblarContadorDeReportesUseCase {

    private final ReporteCiudadanoRepository reportes;
    private final ContadorReportesPort contador;
    private final Reloj reloj;
    private final Duration ventana;

    public RepoblarContadorDeReportesService(ReporteCiudadanoRepository reportes, ContadorReportesPort contador,
                                             Reloj reloj, Duration ventana) {
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
