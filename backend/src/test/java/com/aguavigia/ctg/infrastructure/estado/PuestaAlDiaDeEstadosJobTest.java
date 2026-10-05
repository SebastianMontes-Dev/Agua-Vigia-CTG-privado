package com.aguavigia.ctg.infrastructure.estado;

import com.aguavigia.ctg.domain.port.in.ExpirarCortesVencidosUseCase;
import com.aguavigia.ctg.domain.port.in.PonerAlDiaSectoresUseCase;
import com.aguavigia.ctg.domain.port.in.RepoblarContadorDeReportesUseCase;
import com.aguavigia.ctg.infrastructure.scheduling.EjecucionUnica;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class PuestaAlDiaDeEstadosJobTest {

    private ExpirarCortesVencidosUseCase expirar;
    private PonerAlDiaSectoresUseCase ponerAlDia;
    private RepoblarContadorDeReportesUseCase repoblarContador;
    private EjecucionUnica ejecucionUnica;
    private PuestaAlDiaDeEstadosJob job;

    @BeforeEach
    void montar() {
        expirar = mock(ExpirarCortesVencidosUseCase.class);
        ponerAlDia = mock(PonerAlDiaSectoresUseCase.class);
        repoblarContador = mock(RepoblarContadorDeReportesUseCase.class);
        ejecucionUnica = mock(EjecucionUnica.class);
        job = new PuestaAlDiaDeEstadosJob(expirar, ponerAlDia, repoblarContador, ejecucionUnica);
    }

    /** Expirar primero: así el recálculo ya ve el corte EXPIRADO y el barrio vuelve a «sin datos» en la misma pasada. */
    @Test
    void expiraLosCortesVencidosAntesDePonerAlDiaLosBarrios() {
        job.ponerAlDia();

        InOrder orden = inOrder(expirar, ponerAlDia);
        orden.verify(expirar).expirarVencidos();
        orden.verify(ponerAlDia).ponerAlDia();
    }

    @Test
    void siExpirarFallaIgualPoneAlDiaLosBarrios() {
        doThrow(new IllegalStateException("Mongo caído")).when(expirar).expirarVencidos();

        assertThatCode(job::ponerAlDia).doesNotThrowAnyException();

        verify(ponerAlDia).ponerAlDia();
    }

    /** Un fallo no puede matar el hilo del planificador: Spring dejaría de reprogramar la tarea en silencio. */
    @Test
    void unFalloNoSePropagaAlPlanificador() {
        doThrow(new IllegalStateException("Mongo caído")).when(ponerAlDia).ponerAlDia();

        assertThatCode(job::ponerAlDia).doesNotThrowAnyException();
    }

    @Test
    void laPasadaPeriodicaSeEjecutaEnUnaSolaReplica() {
        job.ponerAlDiaEnUnaReplica();

        verify(ejecucionUnica).ejecutar(eq("puesta-al-dia"), any(Duration.class), any(Duration.class), any(Runnable.class));
        verify(expirar, never()).expirarVencidos();
    }

    /**
     * D29: el contador de Redis recupera los votos de la ventana antes de que nada se recalcule, al arrancar y en cada pasada: si Redis se
     * reinicia con el backend arriba, el contador queda en cero hasta que alguien reporte, y el primer reporte no alcanzaría el listón.
     */
    @Test
    void repueblaElContadorAntesDeExpirarYDeRecalcular() {
        job.ponerAlDia();

        InOrder orden = inOrder(repoblarContador, expirar, ponerAlDia);
        orden.verify(repoblarContador).repoblar();
        orden.verify(expirar).expirarVencidos();
        orden.verify(ponerAlDia).ponerAlDia();
    }

    @Test
    void siRepoblarFallaIgualSeExpiraYSePoneAlDia() {
        doThrow(new IllegalStateException("Redis caído")).when(repoblarContador).repoblar();

        assertThatCode(job::ponerAlDia).doesNotThrowAnyException();

        verify(expirar).expirarVencidos();
        verify(ponerAlDia).ponerAlDia();
    }

    @Test
    void alArrancarTambienPasaPorLaEjecucionUnica() {
        job.alArrancar();

        verify(ejecucionUnica).ejecutar(eq("puesta-al-dia"), any(Duration.class), any(Duration.class), any(Runnable.class));
    }
}
