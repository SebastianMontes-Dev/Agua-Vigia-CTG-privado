package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.HuellaDispositivo;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.VotoReciente;
import com.aguavigia.ctg.domain.port.out.ContadorReportesPort;
import com.aguavigia.ctg.domain.port.out.ReporteCiudadanoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.anyCollection;

/** D29: tras un reinicio o un Redis vaciado, el contador vuelve a saber lo que Mongo ya guardó. */
class RepoblarContadorDeReportesServiceTest {

    private static final Instant AHORA = Instant.parse("2026-10-02T08:00:00Z");

    private ReporteCiudadanoRepository reportes;
    private ContadorReportesPort contador;
    private RepoblarContadorDeReportesService servicio;

    @BeforeEach
    void montar() {
        reportes = mock(ReporteCiudadanoRepository.class);
        contador = mock(ContadorReportesPort.class);
        servicio = new RepoblarContadorDeReportesService(reportes, contador, () -> AHORA, Duration.ofMinutes(30));
    }

    @Test
    void devuelveAlContadorLosVotosDeLaVentana() {
        List<VotoReciente> votos = List.of(
                new VotoReciente(new SectorId("crespo"), new HuellaDispositivo("a"), AHORA.minusSeconds(60)),
                new VotoReciente(new SectorId("manga"), new HuellaDispositivo("b"), AHORA.minusSeconds(120)));
        given(reportes.votosRecientes(AHORA.minus(Duration.ofMinutes(30)))).willReturn(votos);

        assertThat(servicio.repoblar()).isEqualTo(2);

        verify(contador).repoblar(votos);
    }

    @Test
    void sinVotosNoTocaAlContador() {
        given(reportes.votosRecientes(AHORA.minus(Duration.ofMinutes(30)))).willReturn(List.of());

        assertThat(servicio.repoblar()).isZero();

        verify(contador, never()).repoblar(anyCollection());
    }
}
