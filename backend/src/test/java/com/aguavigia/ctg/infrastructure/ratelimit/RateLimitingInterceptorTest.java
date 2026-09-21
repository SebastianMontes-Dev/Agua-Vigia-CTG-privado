package com.aguavigia.ctg.infrastructure.ratelimit;

import com.aguavigia.ctg.domain.LimiteDePeticionesExcedidoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.data.redis.DataRedisTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * Contra Redis real, no mockeado: INCR+EXPIRE es exactamente el tipo de logica que un mock
 * podria dar por buena mientras el comando real hace otra cosa.
 */
@Testcontainers
@DataRedisTest
@Import(com.aguavigia.ctg.infrastructure.config.RedisConfig.class)
class RateLimitingInterceptorTest {

    @Container
    @ServiceConnection
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    private static final RateLimitProperties.Regla REGLA_DE_PRUEBA =
            new RateLimitProperties.Regla("/api/veedor/sesion", 3, 60);

    @Autowired
    @Qualifier("redisTemplate")
    private RedisTemplate<String, String> plantillaRedis;

    private RateLimitingInterceptor interceptor;

    @BeforeEach
    void preparar() {
        var factory = Objects.requireNonNull(plantillaRedis.getConnectionFactory());
        try (var conexion = factory.getConnection()) {
            conexion.serverCommands().flushDb();
        }
        interceptor = new RateLimitingInterceptor(plantillaRedis, REGLA_DE_PRUEBA);
    }

    private MockHttpServletRequest peticionDesde(String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(ip);
        return request;
    }

    @Test
    void debePermitirPeticionesDentroDelLimite() {
        for (int i = 0; i < 3; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            boolean continua = interceptor.preHandle(peticionDesde("10.0.0.1"), response, new Object());

            assertThat(continua).isTrue();
            assertThat(response.getStatus()).isEqualTo(200); // default de MockHttpServletResponse
        }
    }

    /**
     * El 429 y la cabecera Retry-After ya no los fija el interceptor: los arma
     * ManejadorGlobalDeErrores a partir de esta excepción, para que la respuesta tenga el mismo
     * formato RFC 7807 que el resto de la API (antes era el único punto que no lo cumplía).
     */
    @Test
    void debeLanzarLimiteDePeticionesExcedidoAlSuperarElLimite() {
        for (int i = 0; i < 3; i++) {
            interceptor.preHandle(peticionDesde("10.0.0.2"), new MockHttpServletResponse(), new Object());
        }

        assertThatThrownBy(() ->
                interceptor.preHandle(peticionDesde("10.0.0.2"), new MockHttpServletResponse(), new Object()))
                .isInstanceOf(LimiteDePeticionesExcedidoException.class)
                .satisfies(e -> assertThat(((LimiteDePeticionesExcedidoException) e).segundosParaReintentar())
                        .isGreaterThan(0));
    }

    /**
     * INCR y EXPIRE como dos comandos: si el segundo se perdia (corte de red, reinicio de Redis entre
     * ambos), la clave quedaba sin caducidad y esa IP se bloqueaba para siempre. Es un solo script
     * atomico, y ademas repara una clave que ya se hubiera quedado asi.
     */
    @Test
    void unaClaveSinCaducidadNoDebeBloquearParaSiempre() {
        String clave = "rate-limit:" + REGLA_DE_PRUEBA.ruta() + ":10.0.0.9";
        plantillaRedis.opsForValue().set(clave, "50");

        assertThatThrownBy(() ->
                interceptor.preHandle(peticionDesde("10.0.0.9"), new MockHttpServletResponse(), new Object()))
                .isInstanceOf(LimiteDePeticionesExcedidoException.class);

        Long ttl = plantillaRedis.getExpire(clave, TimeUnit.SECONDS);
        assertThat(ttl).isGreaterThan(0);
    }

    @Test
    void laPrimeraPeticionDebeDejarLaClaveConCaducidadDeLaVentana() {
        interceptor.preHandle(peticionDesde("10.0.0.10"), new MockHttpServletResponse(), new Object());

        Long ttl = plantillaRedis.getExpire("rate-limit:" + REGLA_DE_PRUEBA.ruta() + ":10.0.0.10", TimeUnit.SECONDS);
        assertThat(ttl).isBetween(1L, 60L);
    }

    /** Un Redis caido es un problema de infraestructura, no del cliente: no se le niega el servicio. */
    @Test
    @SuppressWarnings("unchecked")
    void conRedisCaidoDebeDejarPasarLaPeticion() {
        RedisTemplate<String, String> caido = mock(RedisTemplate.class);
        given(caido.execute(any(org.springframework.data.redis.core.script.RedisScript.class), anyList(), any(Object[].class)))
                .willThrow(new org.springframework.data.redis.RedisConnectionFailureException("sin conexion"));
        var interceptorConRedisCaido = new RateLimitingInterceptor(caido, REGLA_DE_PRUEBA);

        boolean continua = interceptorConRedisCaido.preHandle(
                peticionDesde("10.0.0.11"), new MockHttpServletResponse(), new Object());

        assertThat(continua).isTrue();
    }

    @Test
    void debeContarCadaIpPorSeparado() {
        for (int i = 0; i < 3; i++) {
            interceptor.preHandle(peticionDesde("10.0.0.3"), new MockHttpServletResponse(), new Object());
        }

        MockHttpServletResponse respuestaOtraIp = new MockHttpServletResponse();
        boolean continua = interceptor.preHandle(peticionDesde("10.0.0.4"), respuestaOtraIp, new Object());

        assertThat(continua).isTrue();
    }
}
