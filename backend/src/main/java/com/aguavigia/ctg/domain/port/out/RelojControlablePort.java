package com.aguavigia.ctg.domain.port.out;

import java.time.Duration;
import java.time.Instant;

/**
 * El reloj de la instancia de simulación, con lo que el simulador necesita para moverlo (D33). Solo existe donde la simulación está
 * habilitada: en la instancia real no hay ningún reloj que mover.
 */
public interface RelojControlablePort extends RelojPort {

    /** Pone el reloj en ese instante, antes o después del actual; sigue andando a la velocidad real desde ahí. */
    void fijarEn(Instant instante);

    /** Adelanta el reloj. Una duración negativa o nula es un error: retroceder es {@link #fijarEn}. */
    void avanzar(Duration duracion);

    /** Cuánto se adelanta (o atrasa) este reloj respecto del real. */
    Duration desfase();

    /** Quita el desfase. */
    void volverAlRelojReal();
}
