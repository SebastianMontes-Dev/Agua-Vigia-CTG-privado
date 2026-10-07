package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.EstadoDelReloj;
import com.aguavigia.ctg.domain.port.in.ControlarRelojDeSimulacionUseCase;
import com.aguavigia.ctg.domain.port.out.RelojControlablePort;

import java.time.Duration;
import java.time.Instant;

public class ControlarRelojDeSimulacionService implements ControlarRelojDeSimulacionUseCase {

    /** Un salto mayor que esto desbordaría el reloj o dejaría la base de simulación fuera de todo rango útil. */
    private static final Duration SALTO_MAXIMO = Duration.ofDays(366);

    private final RelojControlablePort reloj;

    public ControlarRelojDeSimulacionService(RelojControlablePort reloj) {
        this.reloj = reloj;
    }

    @Override
    public EstadoDelReloj consultar() {
        return new EstadoDelReloj(reloj.ahora(), reloj.desfase());
    }

    @Override
    public EstadoDelReloj fijarEn(Instant instante) {
        if (instante == null) {
            throw new IllegalArgumentException("Falta el instante en el que fijar el reloj");
        }
        Instant realAhora = reloj.ahora().minus(reloj.desfase());
        if (Duration.between(realAhora, instante).abs().compareTo(SALTO_MAXIMO) > 0) {
            throw new IllegalArgumentException("No se puede fijar el reloj a más de 366 días de la hora real");
        }
        reloj.fijarEn(instante);
        return consultar();
    }

    @Override
    public EstadoDelReloj avanzar(Duration duracion) {
        if (duracion == null) {
            throw new IllegalArgumentException("Falta cuánto avanzar el reloj");
        }
        if (duracion.isZero() || duracion.isNegative()) {
            throw new IllegalArgumentException("Solo se puede avanzar el reloj una duración positiva; para retroceder, fíjalo en un instante");
        }
        if (duracion.compareTo(SALTO_MAXIMO) > 0) {
            throw new IllegalArgumentException("No se puede avanzar el reloj más de 366 días de una vez");
        }
        reloj.avanzar(duracion);
        return consultar();
    }

    @Override
    public EstadoDelReloj volverAlRelojReal() {
        reloj.volverAlRelojReal();
        return consultar();
    }
}
