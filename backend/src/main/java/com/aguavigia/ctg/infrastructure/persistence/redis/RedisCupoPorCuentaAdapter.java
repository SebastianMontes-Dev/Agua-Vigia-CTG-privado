package com.aguavigia.ctg.infrastructure.persistence.redis;

import com.aguavigia.ctg.domain.ResultadoDeCupo;
import com.aguavigia.ctg.domain.port.out.CupoPorCuentaPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;

/**
 * Contador por clave con la ventana fija desde el primer uso: el EXPIRE se pone solo en el primer
 * INCR (misma razón que en RedisControlIntentosAdapter). La clave lleva el SHA-256 del valor, que
 * puede contener un identificador de cuenta.
 *
 * **Falla abierto** cuando Redis no responde, como los demás contadores: es un límite blando, y
 * negarle el servicio a todos porque la caché cayó sería peor que dejar pasar unos intentos más.
 */
@Component
public class RedisCupoPorCuentaAdapter implements CupoPorCuentaPort {

    private static final Logger log = LoggerFactory.getLogger(RedisCupoPorCuentaAdapter.class);

    private static final String PREFIJO = "cupo:";

    private final RedisTemplate<String, String> redis;

    public RedisCupoPorCuentaAdapter(@Qualifier("redisTemplate") RedisTemplate<String, String> redis) {
        this.redis = redis;
    }

    @Override
    public ResultadoDeCupo consumir(String clave, int maximo, Duration ventana) {
        String llave = PREFIJO + huella(clave);
        try {
            Long usos = redis.opsForValue().increment(llave);
            if (usos == null) {
                return ResultadoDeCupo.conCupo();
            }
            if (usos == 1L) {
                redis.expire(llave, ventana);
            }
            if (usos <= maximo) {
                return ResultadoDeCupo.conCupo();
            }
            Long segundos = redis.getExpire(llave, TimeUnit.SECONDS);
            // -1 = la clave existe sin TTL (el proceso cayó entre el INCR y el EXPIRE): sin esto el
            // cupo quedaría agotado para siempre.
            if (segundos != null && segundos == -1L) {
                redis.expire(llave, ventana);
            }
            return ResultadoDeCupo.agotado(segundos == null || segundos <= 0 ? ventana : Duration.ofSeconds(segundos));
        } catch (DataAccessException redisCaido) {
            log.warn("No se pudo consumir el cupo: {}", redisCaido.getMessage());
            return ResultadoDeCupo.conCupo();
        }
    }

    private static String huella(String valor) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha256.digest(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException imposibleEnCualquierJvm) {
            throw new IllegalStateException("SHA-256 no disponible", imposibleEnCualquierJvm);
        }
    }
}
