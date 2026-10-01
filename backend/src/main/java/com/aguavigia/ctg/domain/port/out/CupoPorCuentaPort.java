package com.aguavigia.ctg.domain.port.out;

import com.aguavigia.ctg.domain.ResultadoDeCupo;

import java.time.Duration;

/**
 * Un número máximo de usos por clave dentro de una ventana (p. ej. 3 verificaciones de barrio por
 * cuenta al día). A diferencia de {@link ControlIntentosPort}, no bloquea ni cuenta fallos: cuenta
 * usos, sean buenos o malos.
 */
public interface CupoPorCuentaPort {

    /**
     * Consume un uso de {@code clave} si queda cupo. La ventana corre desde el primer uso y no se
     * renueva con los siguientes: si se renovara, quien sigue intentando nunca recuperaría el cupo.
     */
    ResultadoDeCupo consumir(String clave, int maximo, Duration ventana);
}
