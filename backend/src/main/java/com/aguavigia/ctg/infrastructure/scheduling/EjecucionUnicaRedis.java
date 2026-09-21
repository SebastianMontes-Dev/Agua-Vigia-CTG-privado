package com.aguavigia.ctg.infrastructure.scheduling;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Bloqueo distribuido sobre el Redis que ya usa el proyecto (`SET NX PX`). Cada ejecución lleva un
 * token propio y solo lo suelta quien lo puso: si la tarea dura más que `bloqueoMaximo` y el bloqueo
 * caduca, otra réplica lo toma, y al terminar la primera no puede borrar el de la segunda.
 *
 * Con Redis caído se OMITE la tarea en vez de ejecutarla: sin Redis no se puede saber si otra
 * réplica ya corre, y un duplicado es peor que esperar al siguiente ciclo.
 */
@Component
public class EjecucionUnicaRedis implements EjecucionUnica {

    private static final Logger log = LoggerFactory.getLogger(EjecucionUnicaRedis.class);

    /** Suelta el bloqueo si es nuestro; si queda tiempo mínimo por cumplir, lo acorta a ese tiempo en vez de borrarlo. */
    private static final DefaultRedisScript<Long> SOLTAR = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) ~= ARGV[1] then
                return 0
            end
            local restante = tonumber(ARGV[2])
            if restante > 0 then
                return redis.call('PEXPIRE', KEYS[1], restante)
            end
            return redis.call('DEL', KEYS[1])
            """, Long.class);

    private final RedisTemplate<String, String> redis;

    public EjecucionUnicaRedis(@Qualifier("redisTemplate") RedisTemplate<String, String> redis) {
        this.redis = redis;
    }

    @Override
    public void ejecutar(String nombre, Duration bloqueoMaximo, Duration bloqueoMinimo, Runnable tarea) {
        String clave = "tarea-unica:" + nombre;
        String token = UUID.randomUUID().toString();

        Boolean obtenido;
        try {
            obtenido = redis.opsForValue().setIfAbsent(clave, token, bloqueoMaximo);
        } catch (RuntimeException redisNoDisponible) {
            log.warn("Tarea '{}' omitida: sin Redis no se puede comprobar si otra réplica ya la ejecuta: {}",
                    nombre, NestedExceptionUtils.getMostSpecificCause(redisNoDisponible).toString());
            return;
        }
        if (!Boolean.TRUE.equals(obtenido)) {
            log.debug("Tarea '{}' omitida: la ejecuta otra réplica", nombre);
            return;
        }

        long inicio = System.nanoTime();
        try {
            tarea.run();
        } finally {
            long transcurridoMs = Duration.ofNanos(System.nanoTime() - inicio).toMillis();
            long restanteMs = Math.max(0, bloqueoMinimo.toMillis() - transcurridoMs);
            try {
                redis.execute(SOLTAR, List.of(clave), token, String.valueOf(restanteMs));
            } catch (RuntimeException noSePudoSoltar) {
                // El bloqueo caduca solo al cumplirse `bloqueoMaximo`; no se enmascara el resultado de la tarea.
                log.warn("No se pudo soltar el bloqueo de '{}': caduca por sí solo: {}", nombre,
                        NestedExceptionUtils.getMostSpecificCause(noSePudoSoltar).toString());
            }
        }
    }
}
