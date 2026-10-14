package com.aguavigia.ctg.infrastructure.ingest;

import com.aguavigia.ctg.domain.port.in.ActualizarEstadosPorVentanaUseCase;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * El bloqueo mínimo de «una sola réplica» impedía que el barrido corriera más de una vez cada 30 s aunque el intervalo fuera de 5: la
 * simulación (ventanas-intervalo-ms=5000) no se aceleraba. El mínimo no puede superar al intervalo.
 */
class PlanificadorDeVentanasBloqueoTest {

    private static Duration minimoPedido(long intervaloMs) {
        var ejecucionUnica = mock(com.aguavigia.ctg.infrastructure.scheduling.EjecucionUnica.class);
        new PlanificadorDeVentanas(mock(ActualizarEstadosPorVentanaUseCase.class), ejecucionUnica, intervaloMs)
                .revisarVentanasEnUnaReplica();
        var maximo = org.mockito.ArgumentCaptor.forClass(Duration.class);
        var minimo = org.mockito.ArgumentCaptor.forClass(Duration.class);
        verify(ejecucionUnica).ejecutar(eq("ventanas"), maximo.capture(), minimo.capture(), any(Runnable.class));
        return minimo.getValue();
    }

    @Test
    void conElIntervaloPorDefectoElBloqueoMinimoSigueSiendoDe30Segundos() {
        assertThat(minimoPedido(60_000)).isEqualTo(Duration.ofSeconds(30));
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
