package com.aguavigia.ctg.infrastructure.telegram;

import com.aguavigia.ctg.domain.port.in.AtenderTelegramUseCase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TelegramSondeoJobTest {

    private final AtenderTelegramUseCase atender = mock(AtenderTelegramUseCase.class);
    private final TelegramSondeoJob job = new TelegramSondeoJob(atender);

    @Test
    void cadaSondeoDebeAtenderLosMensajesNuevos() {
        job.sondear();
        job.sondear();

        verify(atender, times(2)).atender();
    }

    /** El planificador tiene un solo hilo: una excepción suelta lo dejaría sin sondear ni barrer el consenso. */
    @Test
    void unFalloDeTelegramNoDebeEscaparDelPlanificador() {
        when(atender.atender()).thenThrow(new IllegalStateException("Telegram no responde"));

        assertThatCode(job::sondear).doesNotThrowAnyException();
    }
}
