package com.aguavigia.ctg.compartido.reloj;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/**
 * Adaptador de Reloj. El Clock se inyecta para que las pruebas puedan fijar el instante
 * con Clock.fixed(...) sin tocar el reloj real de la maquina.
 *
 * No existe en la instancia de simulación (aguavigia.sim.habilitada=true): allí manda
 * {@link com.aguavigia.ctg.infrastructure.sim.RelojSimulado}, y dos Reloj impedirían arrancar.
 */
@Component
@ConditionalOnProperty(prefix = "aguavigia.sim", name = "habilitada", havingValue = "false", matchIfMissing = true)
public class RelojDelSistema implements Reloj {

    private final Clock clock;

    public RelojDelSistema(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Instant ahora() {
        return Instant.now(clock);
    }
}
