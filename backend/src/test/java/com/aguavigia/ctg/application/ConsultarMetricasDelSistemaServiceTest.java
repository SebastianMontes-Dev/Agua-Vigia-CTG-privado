package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.MetricasDelSistema;
import com.aguavigia.ctg.domain.port.out.MetricasDelSistemaPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class ConsultarMetricasDelSistemaServiceTest {

    @Test
    void devuelveLaInstantaneaDelPuerto() {
        MetricasDelSistemaPort puerto = mock(MetricasDelSistemaPort.class);
        MetricasDelSistema esperadas = new MetricasDelSistema(Instant.parse("2026-10-06T15:00:00Z"), Map.of("SIN_SERVICIO/VECINOS", 3L), 1,
                Map.of(), Map.of(), Map.of(), new MetricasDelSistema.TiempoHastaElCambio(0, 0, 0));
        given(puerto.instantanea()).willReturn(esperadas);

        assertThat(new ConsultarMetricasDelSistemaService(puerto).consultar()).isSameAs(esperadas);
    }
}
