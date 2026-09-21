package com.aguavigia.ctg.infrastructure.consenso;

import com.aguavigia.ctg.domain.port.in.EvaluarConsensoUseCase;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class EvaluacionPendienteJobTest {

    @Test
    void cadaBarridoDebePedirleAlCasoDeUsoQueEvaluePendientes() {
        EvaluarConsensoUseCase consenso = mock(EvaluarConsensoUseCase.class);

        new EvaluacionPendienteJob(consenso).barrer();

        verify(consenso).evaluarPendientes();
    }
}
