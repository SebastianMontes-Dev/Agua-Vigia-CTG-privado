package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.ReporteId;
import com.aguavigia.ctg.domain.port.out.SubidaDeFotoRepository;
import com.aguavigia.ctg.domain.port.out.TokenDeSubidaPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class EmitirTokenDeSubidaServiceTest {

    private static final Instant AHORA = Instant.parse("2026-10-01T15:00:00Z");
    private static final ReporteId ID = new ReporteId("r-1");

    private SubidaDeFotoRepository subidas;
    private EmitirTokenDeSubidaService servicio;

    @BeforeEach
    void montar() {
        subidas = mock(SubidaDeFotoRepository.class);
        TokenDeSubidaPort tokens = mock(TokenDeSubidaPort.class);
        given(tokens.nuevo()).willReturn("token-en-claro");
        given(tokens.hash("token-en-claro")).willReturn("hash-del-token");
        servicio = new EmitirTokenDeSubidaService(subidas, tokens, () -> AHORA, Duration.ofMinutes(10));
    }

    @Test
    void debeDevolverElTokenEnClaroUnaSolaVez() {
        assertThat(servicio.emitir(ID)).isEqualTo("token-en-claro");
    }

    /** Se guarda el hash, nunca el token: leer la base no debe bastar para subir fotos. */
    @Test
    void debeGuardarSoloElHashConSuVencimiento() {
        servicio.emitir(ID);

        verify(subidas).guardar(ID, "hash-del-token", AHORA.plus(Duration.ofMinutes(10)));
    }
}
