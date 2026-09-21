package com.aguavigia.ctg.infrastructure.scheduling;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.data.redis.DataRedisTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * Con varias réplicas, cada una dispara sus propios `@Scheduled`: sin esto, el ciclo de ingesta
 * corría N veces por intervalo y generaba propuestas duplicadas. Contra Redis real: un mock daría
 * por bueno un SET NX que el comando de verdad podría rechazar.
 */
@Testcontainers
@DataRedisTest
@Import(com.aguavigia.ctg.infrastructure.config.RedisConfig.class)
class EjecucionUnicaRedisTest {

    @Container
    @ServiceConnection
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    private static final Duration SIN_MINIMO = Duration.ZERO;

    @Autowired
    @Qualifier("redisTemplate")
    private RedisTemplate<String, String> plantilla;

    private EjecucionUnicaRedis ejecucion;

    @BeforeEach
    void preparar() {
        try (var conexion = Objects.requireNonNull(plantilla.getConnectionFactory()).getConnection()) {
            conexion.serverCommands().flushDb();
        }
        ejecucion = new EjecucionUnicaRedis(plantilla);
    }

    @Test
    void debeEjecutarLaTareaSiNadieTieneElBloqueo() {
        AtomicInteger veces = new AtomicInteger();

        ejecucion.ejecutar("ingesta", Duration.ofMinutes(1), SIN_MINIMO, veces::incrementAndGet);

        assertThat(veces.get()).isEqualTo(1);
    }

    @Test
    void unaSegundaInstanciaNoDebeEjecutarMientrasLaPrimeraTieneElBloqueo() throws Exception {
        CountDownLatch primeraDentro = new CountDownLatch(1);
        CountDownLatch soltarPrimera = new CountDownLatch(1);
        AtomicInteger veces = new AtomicInteger();

        Thread primera = new Thread(() -> ejecucion.ejecutar("ingesta", Duration.ofMinutes(1), SIN_MINIMO, () -> {
            veces.incrementAndGet();
            primeraDentro.countDown();
            try {
                soltarPrimera.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
        primera.start();
        assertThat(primeraDentro.await(10, TimeUnit.SECONDS)).isTrue();

        new EjecucionUnicaRedis(plantilla).ejecutar("ingesta", Duration.ofMinutes(1), SIN_MINIMO, veces::incrementAndGet);

        soltarPrimera.countDown();
        primera.join(10_000);
        assertThat(veces.get()).isEqualTo(1);
    }

    @Test
    void debeLiberarElBloqueoAlTerminarParaQueLaSiguienteRondaCorra() {
        AtomicInteger veces = new AtomicInteger();

        ejecucion.ejecutar("ventanas", Duration.ofMinutes(1), SIN_MINIMO, veces::incrementAndGet);
        ejecucion.ejecutar("ventanas", Duration.ofMinutes(1), SIN_MINIMO, veces::incrementAndGet);

        assertThat(veces.get()).isEqualTo(2);
    }

    /**
     * Un job de 50 ms disparado por dos réplicas con unos cientos de ms de diferencia correría dos
     * veces si el bloqueo se soltara al terminar. El mínimo lo retiene hasta que pase el intervalo.
     */
    @Test
    void debeRetenerElBloqueoElTiempoMinimoAunqueLaTareaTermineAntes() {
        AtomicInteger veces = new AtomicInteger();

        ejecucion.ejecutar("ventanas", Duration.ofMinutes(1), Duration.ofSeconds(30), veces::incrementAndGet);
        new EjecucionUnicaRedis(plantilla).ejecutar("ventanas", Duration.ofMinutes(1), Duration.ofSeconds(30),
                veces::incrementAndGet);

        assertThat(veces.get()).isEqualTo(1);
    }

    @Test
    void bloqueosDeTareasDistintasNoDebenEstorbarse() {
        AtomicInteger veces = new AtomicInteger();

        ejecucion.ejecutar("ingesta", Duration.ofMinutes(1), Duration.ofSeconds(30), veces::incrementAndGet);
        ejecucion.ejecutar("ventanas", Duration.ofMinutes(1), Duration.ofSeconds(30), veces::incrementAndGet);

        assertThat(veces.get()).isEqualTo(2);
    }

    @Test
    void debeLiberarElBloqueoAunqueLaTareaFalleYPropagarElError() {
        assertThatThrownBy(() -> ejecucion.ejecutar("ingesta", Duration.ofMinutes(1), SIN_MINIMO, () -> {
            throw new IllegalStateException("fallo del job");
        })).isInstanceOf(IllegalStateException.class);

        AtomicInteger veces = new AtomicInteger();
        ejecucion.ejecutar("ingesta", Duration.ofMinutes(1), SIN_MINIMO, veces::incrementAndGet);
        assertThat(veces.get()).isEqualTo(1);
    }

    /** Sin Redis no se puede saber si otra réplica ya corre: se omite el ciclo en vez de arriesgar un duplicado. */
    @Test
    @SuppressWarnings("unchecked")
    void conRedisCaidoDebeOmitirLaTareaSinLanzar() {
        RedisTemplate<String, String> caido = mock(RedisTemplate.class);
        given(caido.opsForValue()).willThrow(new RedisConnectionFailureException("sin conexion"));
        AtomicInteger veces = new AtomicInteger();

        new EjecucionUnicaRedis(caido).ejecutar("ingesta", Duration.ofMinutes(1), SIN_MINIMO, veces::incrementAndGet);

        assertThat(veces.get()).isZero();
    }
}
