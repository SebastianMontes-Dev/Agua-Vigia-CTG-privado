package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.BoletinSimulado;
import com.aguavigia.ctg.domain.port.out.BuzonDeBoletinesSimuladosPort;
import com.aguavigia.ctg.domain.port.out.CicloDeIngestaPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class InyectarBoletinSimuladoServiceTest {

    private static final Instant AHORA = Instant.parse("2026-11-02T13:20:00Z");

    private BuzonDeBoletinesSimuladosPort buzon;
    private CicloDeIngestaPort ciclo;
    private InyectarBoletinSimuladoService servicio;

    @BeforeEach
    void montar() {
        buzon = mock(BuzonDeBoletinesSimuladosPort.class);
        ciclo = mock(CicloDeIngestaPort.class);
        servicio = new InyectarBoletinSimuladoService(buzon, ciclo, () -> AHORA);
    }

    private static BoletinSimulado boletin(Instant fecha) {
        return new BoletinSimulado(7L, fecha, "https://simulacion.local/boletin/7", "[SIMULACIÓN] Suspensión del servicio",
                "<p>Manga, 10:00 a 16:00</p>", null);
    }

    /** El boletín tiene que estar en el buzón antes de que corra el ciclo, o el ciclo no lo vería hasta el siguiente. */
    @Test
    void debeEncolarElBoletinYSoloDespuesDispararElCiclo() {
        servicio.inyectar(boletin(Instant.parse("2026-11-02T13:00:00Z")));

        InOrder orden = inOrder(buzon, ciclo);
        orden.verify(buzon).encolar(org.mockito.ArgumentMatchers.any());
        orden.verify(ciclo).ejecutarAhora();
    }

    @Test
    void sinFechaUsaLaHoraDelRelojDeLaSimulacion() {
        BoletinSimulado inyectado = servicio.inyectar(boletin(null));

        assertThat(inyectado.fecha()).isEqualTo(AHORA);
        ArgumentCaptor<BoletinSimulado> captor = ArgumentCaptor.forClass(BoletinSimulado.class);
        verify(buzon).encolar(captor.capture());
        assertThat(captor.getValue().fecha()).isEqualTo(AHORA);
    }

    @Test
    void conFechaLaRespeta() {
        Instant fecha = Instant.parse("2026-11-01T08:00:00Z");

        assertThat(servicio.inyectar(boletin(fecha)).fecha()).isEqualTo(fecha);
    }

    @Test
    void unBoletinNuloNoTocaNada() {
        assertThatThrownBy(() -> servicio.inyectar(null)).isInstanceOf(IllegalArgumentException.class);
        verify(buzon, never()).encolar(org.mockito.ArgumentMatchers.any());
        verify(ciclo, never()).ejecutarAhora();
    }

    @Test
    void unBoletinSinTituloOSinContenidoSeRechazaAlConstruirse() {
        assertThatThrownBy(() -> new BoletinSimulado(1L, null, null, " ", "<p>x</p>", null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("título");
        assertThatThrownBy(() -> new BoletinSimulado(1L, null, null, "t", "", null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("contenido");
    }
}
