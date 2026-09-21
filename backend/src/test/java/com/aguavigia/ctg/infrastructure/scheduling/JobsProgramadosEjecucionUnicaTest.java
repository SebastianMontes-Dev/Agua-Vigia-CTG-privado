package com.aguavigia.ctg.infrastructure.scheduling;

import com.aguavigia.ctg.domain.port.in.ActualizarEstadosPorVentanaUseCase;
import com.aguavigia.ctg.domain.port.in.RegistrarPropuestaIngestaUseCase;
import com.aguavigia.ctg.domain.port.out.AlmacenamientoPort;
import com.aguavigia.ctg.domain.port.out.RelojPort;
import com.aguavigia.ctg.domain.port.out.ReporteCiudadanoRepository;
import com.aguavigia.ctg.domain.port.out.SectorRepository;
import com.aguavigia.ctg.infrastructure.ingest.AcuacarApiCollector;
import com.aguavigia.ctg.infrastructure.ingest.DeduplicadorReciente;
import com.aguavigia.ctg.infrastructure.ingest.EstadoColectorRegistry;
import com.aguavigia.ctg.infrastructure.ingest.HeuristicaExtractor;
import com.aguavigia.ctg.infrastructure.ingest.PipelineOrquestador;
import com.aguavigia.ctg.infrastructure.ingest.PlanificadorDeVentanas;
import com.aguavigia.ctg.infrastructure.ingest.RssCollector;
import com.aguavigia.ctg.infrastructure.mantenimiento.LimpiezaFotosHuerfanasJob;
import com.aguavigia.ctg.infrastructure.mantenimiento.MantenimientoProperties;
import com.aguavigia.ctg.infrastructure.mantenimiento.PurgaEvidenciaAntiguaJob;
import com.aguavigia.ctg.infrastructure.persistence.mongo.MarcaDeIngestaMongoRepository;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * El punto de entrada `@Scheduled` de cada job debe pasar por {@link EjecucionUnica}: si alguien lo
 * vuelve a llamar directo, con varias réplicas el job corre N veces por ciclo. Cada tarea lleva un
 * nombre distinto para que un job lento no bloquee a los demás.
 */
class JobsProgramadosEjecucionUnicaTest {

    private final EjecucionUnica ejecucionUnica = mock(EjecucionUnica.class);

    @Test
    void laIngestaDebeEjecutarseEnUnaSolaReplica() {
        var orquestador = new PipelineOrquestador(mock(AcuacarApiCollector.class), mock(RssCollector.class),
                mock(DeduplicadorReciente.class), mock(HeuristicaExtractor.class), mock(SectorRepository.class),
                mock(RegistrarPropuestaIngestaUseCase.class), mock(EstadoColectorRegistry.class),
                mock(MarcaDeIngestaMongoRepository.class), mock(RelojPort.class), ejecucionUnica);

        orquestador.ejecutarCicloEnUnaReplica();

        verify(ejecucionUnica).ejecutar(eq("ingesta"), any(Duration.class), any(Duration.class), any(Runnable.class));
    }

    @Test
    void elBarridoDeVentanasDebeEjecutarseEnUnaSolaReplica() {
        new PlanificadorDeVentanas(mock(ActualizarEstadosPorVentanaUseCase.class), ejecucionUnica)
                .revisarVentanasEnUnaReplica();

        verify(ejecucionUnica).ejecutar(eq("ventanas"), any(Duration.class), any(Duration.class), any(Runnable.class));
    }

    @Test
    void laLimpiezaDeFotosDebeEjecutarseEnUnaSolaReplica() {
        new LimpiezaFotosHuerfanasJob(mock(AlmacenamientoPort.class), mock(ReporteCiudadanoRepository.class),
                new MantenimientoProperties(null, null), ejecucionUnica).limpiarEnUnaReplica();

        verify(ejecucionUnica).ejecutar(eq("fotos-huerfanas"), any(Duration.class), any(Duration.class), any(Runnable.class));
    }

    @Test
    void laPurgaDeEvidenciaDebeEjecutarseEnUnaSolaReplica() {
        new PurgaEvidenciaAntiguaJob(mock(AlmacenamientoPort.class), mock(ReporteCiudadanoRepository.class),
                mock(RelojPort.class), new MantenimientoProperties(null, null), ejecucionUnica).purgarEnUnaReplica();

        verify(ejecucionUnica).ejecutar(eq("purga-evidencia"), any(Duration.class), any(Duration.class), any(Runnable.class));
    }
}
