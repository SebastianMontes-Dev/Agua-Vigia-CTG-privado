package com.aguavigia.ctg.infrastructure.persistence.sql;

import com.aguavigia.ctg.domain.ResultadoDeCopia;
import com.aguavigia.ctg.domain.port.in.CopiarAnaliticaUseCase;
import com.aguavigia.ctg.infrastructure.scheduling.EjecucionUnica;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class CopiaAnaliticaJobTest {

    /** Ejecuta la tarea en el acto y anota con qué nombre y bloqueos se pidió. */
    private static final class EjecucionInmediata implements EjecucionUnica {
        String nombre;
        Duration bloqueoMinimo;

        @Override
        public void ejecutar(String nombre, Duration bloqueoMaximo, Duration bloqueoMinimo, Runnable tarea) {
            this.nombre = nombre;
            this.bloqueoMinimo = bloqueoMinimo;
            tarea.run();
        }
    }

    @Test
    void debeCopiarPasandoPorLaEjecucionUnica() {
        AtomicInteger copias = new AtomicInteger();
        CopiarAnaliticaUseCase copiar = () -> {
            copias.incrementAndGet();
            return ResultadoDeCopia.OK;
        };
        EjecucionInmediata ejecucion = new EjecucionInmediata();

        new CopiaAnaliticaJob(copiar, ejecucion, 300_000).copiarEnUnaReplica();

        assertThat(copias).hasValue(1);
        assertThat(ejecucion.nombre).isEqualTo("copia-analitica");
        assertThat(ejecucion.bloqueoMinimo).isEqualTo(Duration.ofMinutes(1));
    }

    @Test
    void elBloqueoMinimoNoDebeSuperarAlIntervalo() {
        EjecucionInmediata ejecucion = new EjecucionInmediata();

        new CopiaAnaliticaJob(() -> ResultadoDeCopia.OK, ejecucion, 5_000).copiarEnUnaReplica();

        assertThat(ejecucion.bloqueoMinimo).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void noDebePropagarUnFalloParaQueSpringSigaReprogramandoLaTarea() {
        CopiarAnaliticaUseCase queFalla = () -> {
            throw new IllegalStateException("no debería pasar");
        };

        assertThatCode(() -> new CopiaAnaliticaJob(queFalla, new EjecucionInmediata(), 300_000).copiarEnUnaReplica())
                .doesNotThrowAnyException();
    }
}
