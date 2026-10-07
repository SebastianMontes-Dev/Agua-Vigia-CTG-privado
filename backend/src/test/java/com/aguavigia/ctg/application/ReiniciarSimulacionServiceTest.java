package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.port.out.BuzonDeBoletinesSimuladosPort;
import com.aguavigia.ctg.domain.port.out.RelojControlablePort;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ReiniciarSimulacionServiceTest {

    /** Lo que vive en memoria del backend: los boletines que esperaban a la ingesta y el desfase del reloj. */
    @Test
    void debeVaciarElBuzonYVolverAlRelojReal() {
        BuzonDeBoletinesSimuladosPort buzon = mock(BuzonDeBoletinesSimuladosPort.class);
        RelojControlablePort reloj = mock(RelojControlablePort.class);

        new ReiniciarSimulacionService(buzon, reloj).reiniciar();

        verify(buzon).vaciar();
        verify(reloj).volverAlRelojReal();
    }
}
