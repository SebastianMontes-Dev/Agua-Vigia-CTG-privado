package com.aguavigia.ctg.infrastructure.security;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TiempoConstanteAdapterTest {

    private static final Duration MINIMO = Duration.ofMillis(80);

    private final TiempoConstanteAdapter adaptador = new TiempoConstanteAdapter(MINIMO);

    private static long milisegundosQueTarda(Runnable accion) {
        long inicio = System.nanoTime();
        accion.run();
        return Duration.ofNanos(System.nanoTime() - inicio).toMillis();
    }

    @Test
    void debeEsperarLaDuracionMinimaCuandoLaAccionEsInstantanea() {
        AtomicBoolean ejecutada = new AtomicBoolean();

        long ms = milisegundosQueTarda(() -> adaptador.ejecutar(() -> ejecutada.set(true)));

        assertThat(ejecutada).isTrue();
        assertThat(ms).isGreaterThanOrEqualTo(MINIMO.toMillis());
    }

    @Test
    void debeDurarLoMismoConUnaAccionRapidaQueConUnaLentaSiNoSuperaElMinimo() {
        long rapida = milisegundosQueTarda(() -> adaptador.ejecutar(() -> { }));
        long lenta = milisegundosQueTarda(() -> adaptador.ejecutar(() -> dormir(30)));

        assertThat(rapida).isGreaterThanOrEqualTo(MINIMO.toMillis());
        assertThat(lenta).isGreaterThanOrEqualTo(MINIMO.toMillis());
        assertThat(Math.abs(lenta - rapida)).isLessThan(40);
    }

    @Test
    void noDebeAgregarEsperaSiLaAccionYaSuperaElMinimo() {
        long ms = milisegundosQueTarda(() -> adaptador.ejecutar(() -> dormir(150)));

        assertThat(ms).isGreaterThanOrEqualTo(150).isLessThan(150 + MINIMO.toMillis());
    }

    @Test
    void debeEsperarYPropagarLaExcepcionSiLaAccionFalla() {
        long inicio = System.nanoTime();

        assertThatThrownBy(() -> adaptador.ejecutar(() -> {
            throw new IllegalStateException("falla");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(Duration.ofNanos(System.nanoTime() - inicio).toMillis())
                .isGreaterThanOrEqualTo(MINIMO.toMillis());
    }

    private static void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
