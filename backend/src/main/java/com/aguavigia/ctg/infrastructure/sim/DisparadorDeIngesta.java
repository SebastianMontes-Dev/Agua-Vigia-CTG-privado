package com.aguavigia.ctg.infrastructure.sim;

import com.aguavigia.ctg.domain.port.out.CicloDeIngestaPort;
import com.aguavigia.ctg.infrastructure.ingest.PipelineOrquestador;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Corre el ciclo de ingesta en el acto, para que un boletín simulado llegue al mapa sin esperar al siguiente intervalo. */
@Component
@ConditionalOnProperty(prefix = "aguavigia.sim", name = "habilitada", havingValue = "true")
public class DisparadorDeIngesta implements CicloDeIngestaPort {

    private final PipelineOrquestador pipeline;

    public DisparadorDeIngesta(PipelineOrquestador pipeline) {
        this.pipeline = pipeline;
    }

    @Override
    public void ejecutarAhora() {
        pipeline.ejecutarCiclo();
    }
}
