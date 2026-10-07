package com.aguavigia.ctg.domain.port.in;

import com.aguavigia.ctg.domain.EstadoDelReloj;

import java.time.Duration;
import java.time.Instant;

/**
 * Mover el reloj de la instancia de simulación (D33). Solo existe donde la simulación está habilitada: en la instancia real no hay
 * caso de uso, ni ruta, ni reloj que mover.
 */
public interface ControlarRelojDeSimulacionUseCase {

    EstadoDelReloj consultar();

    /** Pone el reloj en ese instante, antes o después del actual. */
    EstadoDelReloj fijarEn(Instant instante);

    /** Adelanta el reloj una duración positiva (como mucho 366 días). */
    EstadoDelReloj avanzar(Duration duracion);

    /** Quita el desfase: el reloj vuelve a marcar la hora real. */
    EstadoDelReloj volverAlRelojReal();
}
