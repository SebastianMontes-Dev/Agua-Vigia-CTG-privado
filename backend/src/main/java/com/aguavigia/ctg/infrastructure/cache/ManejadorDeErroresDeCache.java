package com.aguavigia.ctg.infrastructure.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;

/**
 * Un fallo del caché no debe tumbar la petición: es una optimización, y la fuente de verdad (Mongo)
 * sigue ahí. Sin esto, con Redis caído `GET /api/sectores` respondía 500 en vez de leer de Mongo.
 *
 * Una invalidación fallida deja una entrada vieja, pero acotada por el TTL del caché (15 s en
 * `sectores`), así que se degrada en vez de fallar.
 */
public class ManejadorDeErroresDeCache implements CacheErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(ManejadorDeErroresDeCache.class);

    @Override
    public void handleCacheGetError(RuntimeException error, Cache cache, Object clave) {
        log.warn("Caché '{}' no disponible al leer; se consulta la fuente de verdad: {}", cache.getName(), error.toString());
    }

    @Override
    public void handleCachePutError(RuntimeException error, Cache cache, Object clave, Object valor) {
        log.warn("Caché '{}' no disponible al escribir; se sirve sin cachear: {}", cache.getName(), error.toString());
    }

    @Override
    public void handleCacheEvictError(RuntimeException error, Cache cache, Object clave) {
        log.warn("Caché '{}' no disponible al invalidar; la entrada caduca sola por su TTL: {}", cache.getName(), error.toString());
    }

    @Override
    public void handleCacheClearError(RuntimeException error, Cache cache) {
        log.warn("Caché '{}' no disponible al vaciar; las entradas caducan solas por su TTL: {}", cache.getName(), error.toString());
    }
}
