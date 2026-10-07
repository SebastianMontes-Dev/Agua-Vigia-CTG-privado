package com.aguavigia.ctg.infrastructure.estado;

import com.aguavigia.ctg.domain.port.in.ExpirarCortesVencidosUseCase;
import com.aguavigia.ctg.domain.port.in.PonerAlDiaSectoresUseCase;
import com.aguavigia.ctg.domain.port.in.RepoblarContadorDeReportesUseCase;
import com.aguavigia.ctg.infrastructure.scheduling.EjecucionUnica;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * El bloqueo mínimo de «una sola réplica» era de 1 minuto fijo: con un intervalo de 5 s (la simulación) la expiración de cortes y la
 * puesta al día seguían corriendo una vez por minuto. El mínimo no puede superar al intervalo.
 */
class PuestaAlDiaBloqueoTest {

    private static Duration minimoPedido(long intervaloMs) {
        EjecucionUnica ejecucionUnica = mock(EjecucionUnica.class);
        new PuestaAlDiaDeEstadosJob(mock(ExpirarCortesVencidosUseCase.class), mock(PonerAlDiaSectoresUseCase.class),
                mock(RepoblarContadorDeReportesUseCase.class), ejecucionUnica, intervaloMs).ponerAlDiaEnUnaReplica();
        ArgumentCaptor<Duration> minimo = ArgumentCaptor.forClass(Duration.class);
        verify(ejecucionUnica).ejecutar(eq("puesta-al-dia"), any(Duration.class), minimo.capture(), any(Runnable.class));
        return minimo.getValue();
    }

    @Test
    void conElIntervaloPorDefectoElBloqueoMinimoSigueSiendoDeUnMinuto() {
        assertThat(minimoPedido(300_000)).isEqualTo(Duration.ofMinutes(1));
    }

    @Test
    void conUnIntervaloCortoElBloqueoMinimoNoLoSupera() {
        assertThat(minimoPedido(5_000)).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void unIntervaloMuyCortoNuncaDaUnBloqueoNuloONegativo() {
        assertThat(minimoPedido(0)).isPositive();
    }
}
