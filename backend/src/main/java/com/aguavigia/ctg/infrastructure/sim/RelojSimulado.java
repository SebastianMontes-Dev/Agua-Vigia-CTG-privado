package com.aguavigia.ctg.infrastructure.sim;

import com.aguavigia.ctg.domain.port.out.RelojControlablePort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * El reloj de la instancia de simulación (D33): el real más un desfase que el simulador mueve ({@code POST /api/sim/reloj}), para
 * comprimir las horas de un corte en minutos de presentación. Solo existe con {@code aguavigia.sim.habilitada=true}; en la instancia
 * real quien manda es {@link com.aguavigia.ctg.infrastructure.RelojDelSistema}.
 *
 * Los TTL de Redis y de Mongo siguen en tiempo real: lo que se acelera es lo que mira {@code RelojPort}, que es todo el dominio.
 */
@Component
@ConditionalOnProperty(prefix = "aguavigia.sim", name = "habilitada", havingValue = "true")
public class RelojSimulado implements RelojControlablePort {

    private final Clock real;
    private volatile Duration desfase = Duration.ZERO;

    @Autowired
    public RelojSimulado(Clock real) {
        this.real = real;
    }

    @Override
    public Instant ahora() {
        return real.instant().plus(desfase);
    }

    @Override
    public synchronized void fijarEn(Instant instante) {
        desfase = Duration.between(real.instant(), instante);
    }

    @Override
    public synchronized void avanzar(Duration duracion) {
        if (duracion == null || duracion.isZero() || duracion.isNegative()) {
            throw new IllegalArgumentException("Solo se puede avanzar el reloj una duración positiva; para retroceder, fíjalo en un instante");
        }
        desfase = desfase.plus(duracion);
    }

    @Override
    public Duration desfase() {
        return desfase;
    }

    @Override
    public synchronized void volverAlRelojReal() {
        desfase = Duration.ZERO;
    }
}
