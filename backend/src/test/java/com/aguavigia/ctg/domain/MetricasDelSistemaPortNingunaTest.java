package com.aguavigia.ctg.domain;

import com.aguavigia.ctg.domain.port.out.MetricasDelSistemaPort;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Quien construye un servicio sin pedir métricas recibe esta: medir no puede tumbar a nadie, y pedirle una instantánea es un error de cableado. */
class MetricasDelSistemaPortNingunaTest {

    @Test
    void noCuentaNadaYNingunaLlamadaFalla() {
        MetricasDelSistemaPort ninguna = MetricasDelSistemaPort.NINGUNA;

        assertThatCode(() -> {
            ninguna.cambioDeEstado(new SectorId("manga"), EstadoServicio.SIN_SERVICIO, OrigenEstado.VECINOS);
            ninguna.disputaAbierta();
            ninguna.quorumRechazadoPorComposicion(new SectorId("manga"), TipoReporte.SIN_AGUA);
            ninguna.reporteRecibido(NivelDeVerificacion.NINGUNA);
            ninguna.falloDeColector("acuacar");
            ninguna.tiempoHastaElCambioDeEstado(Duration.ofSeconds(30));
        }).doesNotThrowAnyException();
    }

    @Test
    void pedirleUnaInstantaneaEsUnErrorDeCableado() {
        assertThatThrownBy(MetricasDelSistemaPort.NINGUNA::instantanea).isInstanceOf(UnsupportedOperationException.class);
    }
}
