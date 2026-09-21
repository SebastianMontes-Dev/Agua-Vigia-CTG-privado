package com.aguavigia.ctg.infrastructure.ratelimit;

import com.aguavigia.ctg.domain.LimiteDePeticionesExcedidoException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

/**
 * Una instancia por regla (RateLimitWebConfig crea una y la registra solo para su patron de
 * ruta), asi cada una solo necesita saber su propio limite — no interpreta cual regla aplica.
 *
 * Clave por IP del cliente, no por huella de dispositivo. request.getRemoteAddr() es la fuente
 * correcta aqui — no X-Forwarded-For leido a mano — porque server.forward-headers-strategy:
 * framework (application.yml) ya activa el ForwardedHeaderFilter de Spring, que reescribe
 * getRemoteAddr() con el valor de X-Forwarded-For que pone nginx (infra/nginx/nginx.conf) antes de
 * que la peticion llegue aqui. En produccion el puerto del backend no se expone al host
 * (docker-compose.prod.yml), asi que nginx es el unico que puede setear ese header.
 */
public class RateLimitingInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RateLimitingInterceptor.class);

    /**
     * Cuenta y fija la caducidad en un solo paso atómico. Con INCR y EXPIRE como dos comandos, si el
     * segundo se perdía (corte de red, reinicio de Redis entre ambos) la clave quedaba sin
     * caducidad y esa IP se bloqueaba para siempre. El script además repara una clave que ya se
     * hubiera quedado así (TTL < 0) y devuelve el tiempo restante para no gastar otra ida y vuelta
     * al rechazar. Devuelve {conteo, ttlEnSegundos}.
     */
    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> CONTAR_Y_CADUCAR = new DefaultRedisScript<>("""
            local conteo = redis.call('INCR', KEYS[1])
            local ttl = redis.call('TTL', KEYS[1])
            if conteo == 1 or ttl < 0 then
                redis.call('EXPIRE', KEYS[1], ARGV[1])
                ttl = tonumber(ARGV[1])
            end
            return {conteo, ttl}
            """, List.class);

    private final RedisTemplate<String, String> redis;
    private final RateLimitProperties.Regla regla;

    public RateLimitingInterceptor(RedisTemplate<String, String> redis, RateLimitProperties.Regla regla) {
        this.redis = redis;
        this.regla = regla;
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                              @NonNull HttpServletResponse response,
                              @NonNull Object handler) {
        String clave = "rate-limit:" + regla.ruta() + ":" + request.getRemoteAddr();

        List<?> resultado;
        try {
            resultado = redis.execute(CONTAR_Y_CADUCAR, List.of(clave), String.valueOf(regla.ventanaSegundos()));
        } catch (RuntimeException redisNoDisponible) {
            // Redis caído es un problema de infraestructura ajeno al cliente: no se le niega el
            // servicio por él (mismo criterio que el resto de la infraestructura — fallar sin
            // interrumpir). Los frenos por cuenta y por dispositivo siguen vivos en Mongo.
            log.warn("Rate limit sin Redis en {}: se deja pasar la petición: {}", regla.ruta(),
                    NestedExceptionUtils.getMostSpecificCause(redisNoDisponible).toString());
            return true;
        }
        if (resultado == null || resultado.size() < 2) {
            return true;
        }

        long conteo = ((Number) resultado.get(0)).longValue();
        long segundosRestantes = ((Number) resultado.get(1)).longValue();
        if (conteo > regla.limite()) {
            long segundosParaReintentar = segundosRestantes > 0 ? segundosRestantes : regla.ventanaSegundos();
            throw new LimiteDePeticionesExcedidoException(
                    "Demasiadas peticiones a " + regla.ruta() + ". Intenta de nuevo en unos minutos.",
                    segundosParaReintentar);
        }

        return true;
    }
}
