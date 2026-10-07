package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.MetricasDelSistema;
import com.aguavigia.ctg.domain.port.in.ConsultarMetricasDelSistemaUseCase;
import com.aguavigia.ctg.domain.port.out.MetricasDelSistemaPort;

/** Las métricas de calibración (D37) para el panel: una lectura, sin cálculo propio. */
public class ConsultarMetricasDelSistemaService implements ConsultarMetricasDelSistemaUseCase {

    private final MetricasDelSistemaPort metricas;

    public ConsultarMetricasDelSistemaService(MetricasDelSistemaPort metricas) {
        this.metricas = metricas;
    }

    @Override
    public MetricasDelSistema consultar() {
        return metricas.instantanea();
    }
}
