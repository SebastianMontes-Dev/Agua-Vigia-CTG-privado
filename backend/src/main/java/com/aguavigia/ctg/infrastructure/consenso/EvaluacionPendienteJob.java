package com.aguavigia.ctg.infrastructure.consenso;

import com.aguavigia.ctg.domain.port.in.EvaluarConsensoUseCase;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Evalúa el consenso de los sectores que quedaron pendientes porque otra petición ya lo había evaluado
 * en el último intervalo. Sin este barrido, el reporte que cruza el umbral podía no evaluarse hasta que
 * llegara otro. Corre en todas las réplicas a la vez: cada sector pendiente lo toma una sola.
 */
@Component
public class EvaluacionPendienteJob {

    private final EvaluarConsensoUseCase consenso;

    public EvaluacionPendienteJob(EvaluarConsensoUseCase consenso) {
        this.consenso = consenso;
    }

    @Scheduled(fixedDelayString = "${aguavigia.consenso.barrido-ms:1000}")
    public void barrer() {
        consenso.evaluarPendientes();
    }
}
