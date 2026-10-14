package com.aguavigia.ctg.infrastructure.persistence.redis;

import com.aguavigia.ctg.domain.SectorId;
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

import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * Durante una avería masiva un sector recibe cientos de reportes por segundo; la reserva deja que solo
 * uno evalúe el consenso por intervalo y el resto queda pendiente para el barrido. Contra Redis real:
 * un mock daría por bueno un SET NX que el comando de verdad podría rechazar.
 */
@Testcontainers
@DataRedisTest
@Import(com.aguavigia.ctg.infrastructure.config.RedisConfig.class)
class RedisReservaDeEvaluacionAdapterTest {

    @Container
    @ServiceConnection
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    private static final SectorId BOCAGRANDE = new SectorId("bocagrande");
    private static final SectorId MANGA = new SectorId("manga");

    @Autowired
    @Qualifier("redisTemplate")
    private RedisTemplate<String, String> plantilla;

    private RedisReservaDeEvaluacionAdapter reserva;

    @BeforeEach
    void preparar() {
        try (var conexion = Objects.requireNonNull(plantilla.getConnectionFactory()).getConnection()) {
            conexion.serverCommands().flushDb();
        }
        reserva = new RedisReservaDeEvaluacionAdapter(plantilla, 300);
    }

    @Test
    void soloUnaEvaluacionPorIntervaloObtieneLaReserva() {
        assertThat(reserva.reservar(BOCAGRANDE)).isTrue();
        assertThat(reserva.reservar(BOCAGRANDE)).isFalse();
        assertThat(reserva.reservar(BOCAGRANDE)).isFalse();
    }

    @Test
    void laReservaDeUnSectorNoBloqueaAOtro() {
        assertThat(reserva.reservar(BOCAGRANDE)).isTrue();

        assertThat(reserva.reservar(MANGA)).isTrue();
    }

    @Test
    void laReservaVuelveAEstarLibreCuandoPasaElIntervalo() throws InterruptedException {
        assertThat(reserva.reservar(BOCAGRANDE)).isTrue();

        Thread.sleep(450);

        assertThat(reserva.reservar(BOCAGRANDE)).isTrue();
    }

    @Test
    void debeEntregarLosSectoresPendientesUnaSolaVezYSinDuplicados() {
        reserva.dejarPendiente(BOCAGRANDE);
        reserva.dejarPendiente(BOCAGRANDE);
        reserva.dejarPendiente(MANGA);

        assertThat(reserva.tomarPendientes()).containsExactlyInAnyOrder(BOCAGRANDE, MANGA);
        assertThat(reserva.tomarPendientes()).isEmpty();
    }

    @Test
    void conRedisCaidoDebeDejarEvaluarYNoLanzarExcepciones() {
        @SuppressWarnings("unchecked")
        RedisTemplate<String, String> caido = mock(RedisTemplate.class);
        given(caido.opsForValue()).willThrow(new RedisConnectionFailureException("sin conexión"));
        given(caido.opsForSet()).willThrow(new RedisConnectionFailureException("sin conexión"));
        RedisReservaDeEvaluacionAdapter sinRedis = new RedisReservaDeEvaluacionAdapter(caido, 1000);

        assertThat(sinRedis.reservar(BOCAGRANDE)).isTrue();
        sinRedis.dejarPendiente(BOCAGRANDE);
        assertThat(sinRedis.tomarPendientes()).isEmpty();
    }
}
