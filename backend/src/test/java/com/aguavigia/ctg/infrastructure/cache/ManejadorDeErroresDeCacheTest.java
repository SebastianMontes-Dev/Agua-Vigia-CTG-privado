package com.aguavigia.ctg.infrastructure.cache;

import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

/**
 * El caché es una optimización: si Redis cae, una lectura debe pasar de largo hacia Mongo en vez de
 * convertir GET /api/sectores en un 500.
 */
class ManejadorDeErroresDeCacheTest {

    private final ManejadorDeErroresDeCache manejador = new ManejadorDeErroresDeCache();
    private final Cache cache = mock(Cache.class);
    private final RuntimeException redisCaido = new RuntimeException("Unable to connect to Redis");

    @Test
    void unaLecturaFallidaNoDebeLanzar() {
        assertThatCode(() -> manejador.handleCacheGetError(redisCaido, cache, "todos")).doesNotThrowAnyException();
    }

    @Test
    void unaEscrituraFallidaNoDebeLanzar() {
        assertThatCode(() -> manejador.handleCachePutError(redisCaido, cache, "todos", "valor"))
                .doesNotThrowAnyException();
    }

    @Test
    void unaInvalidacionFallidaNoDebeLanzarPeroQuedaAcotadaPorElTtl() {
        assertThatCode(() -> manejador.handleCacheEvictError(redisCaido, cache, "todos")).doesNotThrowAnyException();
        assertThatCode(() -> manejador.handleCacheClearError(redisCaido, cache)).doesNotThrowAnyException();
    }
}
