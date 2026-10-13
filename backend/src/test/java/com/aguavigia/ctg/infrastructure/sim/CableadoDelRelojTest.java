package com.aguavigia.ctg.infrastructure.sim;

import com.aguavigia.ctg.compartido.reloj.RelojDelSistema;
import com.aguavigia.ctg.compartido.reloj.Reloj;
import com.aguavigia.ctg.domain.port.out.RelojControlablePort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Los dos relojes no pueden convivir: con la simulación habilitada habría dos {@link Reloj} y ningún servicio arrancaría; sin ella,
 * el de la simulación no debe existir (en la instancia real no hay reloj que mover).
 */
class CableadoDelRelojTest {

    @Configuration
    @Import({RelojDelSistema.class, RelojSimulado.class})
    static class Relojes {
        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }
    }

    private final ApplicationContextRunner contexto = new ApplicationContextRunner().withUserConfiguration(Relojes.class);

    @Test
    void sinSimulacionSoloExisteElRelojDelSistema() {
        contexto.run(c -> {
            assertThat(c).hasSingleBean(Reloj.class);
            assertThat(c.getBean(Reloj.class)).isInstanceOf(RelojDelSistema.class);
            assertThat(c).doesNotHaveBean(RelojControlablePort.class);
        });
    }

    @Test
    void conLaSimulacionExplicitamenteDeshabilitadaSoloExisteElRelojDelSistema() {
        contexto.withPropertyValues("aguavigia.sim.habilitada=false").run(c -> {
            assertThat(c).hasSingleBean(Reloj.class);
            assertThat(c).doesNotHaveBean(RelojControlablePort.class);
        });
    }

    @Test
    void conLaSimulacionHabilitadaSoloExisteElRelojSimulado() {
        contexto.withPropertyValues("aguavigia.sim.habilitada=true").run(c -> {
            assertThat(c).hasSingleBean(Reloj.class);
            assertThat(c.getBean(Reloj.class)).isInstanceOf(RelojSimulado.class);
            assertThat(c).hasSingleBean(RelojControlablePort.class);
        });
    }
}
