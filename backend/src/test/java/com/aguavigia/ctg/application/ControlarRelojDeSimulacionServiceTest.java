package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.EstadoDelReloj;
import com.aguavigia.ctg.domain.port.out.RelojControlablePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ControlarRelojDeSimulacionServiceTest {

    private static final Instant REAL = Instant.parse("2026-10-06T15:00:00Z");

    /** Un reloj con desfase sobre una hora real fija, como el de la simulación pero sin salir de la capa de aplicación. */
    private static final class RelojEnMemoria implements RelojControlablePort {
        private Duration desfase = Duration.ZERO;

        @Override
        public Instant ahora() {
            return REAL.plus(desfase);
        }

        @Override
        public void fijarEn(Instant instante) {
            desfase = Duration.between(REAL, instante);
        }

        @Override
        public void avanzar(Duration duracion) {
            desfase = desfase.plus(duracion);
        }

        @Override
        public Duration desfase() {
            return desfase;
        }

        @Override
        public void volverAlRelojReal() {
            desfase = Duration.ZERO;
        }
    }

    private RelojEnMemoria reloj;
    private ControlarRelojDeSimulacionService servicio;

    @BeforeEach
    void montar() {
        reloj = new RelojEnMemoria();
        servicio = new ControlarRelojDeSimulacionService(reloj);
    }

    @Test
    void sinMoverNadaElRelojMarcaLaHoraRealSinDesfase() {
        EstadoDelReloj estado = servicio.consultar();

        assertThat(estado.ahora()).isEqualTo(REAL);
        assertThat(estado.desfase()).isEqualTo(Duration.ZERO);
    }

    @Test
    void fijarEnUnInstanteDevuelveElEstadoYLoDejaPuesto() {
        Instant dia = Instant.parse("2026-11-01T13:00:00Z");

        EstadoDelReloj estado = servicio.fijarEn(dia);

        assertThat(estado.ahora()).isEqualTo(dia);
        assertThat(estado.desfase()).isEqualTo(Duration.between(REAL, dia));
        assertThat(reloj.ahora()).isEqualTo(dia);
    }

    @Test
    void avanzarSumaAlDesfaseYSeAcumula() {
        servicio.avanzar(Duration.ofHours(2));
        EstadoDelReloj estado = servicio.avanzar(Duration.ofMinutes(30));

        assertThat(estado.ahora()).isEqualTo(REAL.plus(Duration.ofMinutes(150)));
        assertThat(estado.desfase()).isEqualTo(Duration.ofMinutes(150));
    }

    @Test
    void avanzarUnaDuracionNulaONegativaSeRechaza() {
        assertThatThrownBy(() -> servicio.avanzar(Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> servicio.avanzar(Duration.ofMinutes(-5))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> servicio.avanzar(null)).isInstanceOf(IllegalArgumentException.class);
    }

    /** Un salto de siglos desbordaría o dejaría la base de simulación inservible: el simulador pide como mucho unos días. */
    @Test
    void avanzarMasDeUnAnoSeRechaza() {
        assertThatThrownBy(() -> servicio.avanzar(Duration.ofDays(367)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("366");
    }

    /** Un instante absurdamente lejano haría fallar `ahora()` con una excepción de fecha en cuanto corriera el reloj real. */
    @Test
    void fijarEnUnInstanteACasiUnAnoDeLaHoraRealOMasSeRechaza() {
        assertThatThrownBy(() -> servicio.fijarEn(REAL.plus(Duration.ofDays(367)))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("366");
        assertThatThrownBy(() -> servicio.fijarEn(REAL.minus(Duration.ofDays(367)))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> servicio.fijarEn(Instant.parse("+999999999-12-31T23:59:59Z"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fijarEnUnInstanteDentroDelAnoSeAcepta() {
        assertThat(servicio.fijarEn(REAL.plus(Duration.ofDays(300))).desfase()).isEqualTo(Duration.ofDays(300));
    }

    @Test
    void fijarEnUnInstanteNuloSeRechaza() {
        assertThatThrownBy(() -> servicio.fijarEn(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void volverAlRelojRealQuitaElDesfase() {
        servicio.avanzar(Duration.ofHours(5));

        EstadoDelReloj estado = servicio.volverAlRelojReal();

        assertThat(estado.desfase()).isEqualTo(Duration.ZERO);
        assertThat(estado.ahora()).isEqualTo(REAL);
    }
}
