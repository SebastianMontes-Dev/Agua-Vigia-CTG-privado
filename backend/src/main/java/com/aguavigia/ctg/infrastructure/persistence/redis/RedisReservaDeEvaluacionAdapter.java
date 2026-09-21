package com.aguavigia.ctg.infrastructure.persistence.redis;

import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.port.out.ReservaDeEvaluacionPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * `SET NX PX` por sector para la reserva y un SET de Redis para los pendientes (`SPOP` es atómico: con
 * varias réplicas cada sector pendiente lo toma una sola).
 *
 * Con Redis caído no se acota nada: se evalúa en cada petición, que es correcto aunque más caro. Perder
 * una evaluación sería peor que hacerla de más.
 */
@Component
public class RedisReservaDeEvaluacionAdapter implements ReservaDeEvaluacionPort {

    private static final Logger log = LoggerFactory.getLogger(RedisReservaDeEvaluacionAdapter.class);
    private static final String PREFIJO_RESERVA = "aguavigia:consenso:reserva:";
    private static final String PENDIENTES = "aguavigia:consenso:pendientes";
    private static final int MAXIMO_POR_BARRIDO = 500;

    private final RedisTemplate<String, String> redis;
    private final Duration intervalo;

    public RedisReservaDeEvaluacionAdapter(@Qualifier("redisTemplate") RedisTemplate<String, String> redis,
                                           @Value("${aguavigia.consenso.intervalo-evaluacion-ms:1000}") long intervaloMs) {
        this.redis = redis;
        this.intervalo = Duration.ofMillis(intervaloMs);
    }

    @Override
    public boolean reservar(SectorId sectorId) {
        try {
            return !Boolean.FALSE.equals(redis.opsForValue().setIfAbsent(PREFIJO_RESERVA + sectorId.valor(), "1", intervalo));
        } catch (RuntimeException redisNoDisponible) {
            log.warn("Sin Redis para acotar evaluaciones de consenso; se evalúa en cada reporte: {}", redisNoDisponible.toString());
            return true;
        }
    }

    @Override
    public void dejarPendiente(SectorId sectorId) {
        try {
            redis.opsForSet().add(PENDIENTES, sectorId.valor());
        } catch (RuntimeException redisNoDisponible) {
            log.warn("No se pudo dejar pendiente el sector '{}': {}", sectorId.valor(), redisNoDisponible.toString());
        }
    }

    @Override
    public List<SectorId> tomarPendientes() {
        try {
            List<String> tomados = redis.opsForSet().pop(PENDIENTES, MAXIMO_POR_BARRIDO);
            return tomados == null ? List.of() : tomados.stream().map(SectorId::new).toList();
        } catch (RuntimeException redisNoDisponible) {
            log.warn("No se pudieron leer los sectores pendientes de evaluar: {}", redisNoDisponible.toString());
            return List.of();
        }
    }
}
