package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.Dispositivo;
import com.aguavigia.ctg.domain.DispositivoId;
import com.aguavigia.ctg.domain.port.out.DispositivoRepository;
import com.aguavigia.ctg.domain.port.out.FirmaDeDispositivosPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class EmitirTokenDeDispositivoServiceTest {

    private static final Instant AHORA = Instant.parse("2026-10-01T15:00:00Z");

    private DispositivoRepository dispositivos;
    private FirmaDeDispositivosPort firma;
    private EmitirTokenDeDispositivoService servicio;

    @BeforeEach
    void montar() {
        dispositivos = mock(DispositivoRepository.class);
        firma = mock(FirmaDeDispositivosPort.class);
        given(dispositivos.guardar(any())).willAnswer(invocacion -> invocacion.getArgument(0));
        given(firma.emitir(any())).willAnswer(invocacion ->
                "token-de-" + ((DispositivoId) invocacion.getArgument(0)).valor());
        servicio = new EmitirTokenDeDispositivoService(dispositivos, firma, () -> AHORA);
    }

    private Dispositivo guardadoEnLaPrimeraLlamada() {
        ArgumentCaptor<Dispositivo> guardado = ArgumentCaptor.forClass(Dispositivo.class);
        verify(dispositivos).guardar(guardado.capture());
        return guardado.getValue();
    }

    @Test
    void debeGuardarUnDispositivoNuevoConLaHoraActual() {
        servicio.emitir();

        Dispositivo guardado = guardadoEnLaPrimeraLlamada();
        assertThat(guardado.creadoEn()).isEqualTo(AHORA);
        assertThat(guardado.ultimoVisto()).isEqualTo(AHORA);
    }

    @Test
    void debeDevolverElTokenFirmadoDelDispositivoQueGuardo() {
        String token = servicio.emitir();

        assertThat(token).isEqualTo("token-de-" + guardadoEnLaPrimeraLlamada().id().valor());
    }

    @Test
    void debeGenerarUnIdAleatorioConFormatoDeUuid() {
        servicio.emitir();

        assertThat(guardadoEnLaPrimeraLlamada().id().valor())
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    /** Cada llamada es una identidad nueva: pedir dos tokens no puede dar el mismo dispositivo. */
    @Test
    void cadaEmisionDebeCrearUnDispositivoDistinto() {
        String primero = servicio.emitir();
        String segundo = servicio.emitir();

        assertThat(primero).isNotEqualTo(segundo);
        verify(dispositivos, times(2)).guardar(any());
    }
}
