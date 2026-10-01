package com.aguavigia.ctg.infrastructure.persistence.redis;

import com.aguavigia.ctg.domain.ResultadoDeCupo;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Testcontainers
class RedisCupoPorCuentaAdapterTest {

    private static final Duration UN_DIA = Duration.ofDays(1);

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private static LettuceConnectionFactory fabrica;
    private static StringRedisTemplate plantilla;
    private RedisCupoPorCuentaAdapter adaptador;

    @BeforeAll
    static void conectar() {
        fabrica = new LettuceConnectionFactory(new RedisStandaloneConfiguration(redis.getHost(), redis.getMappedPort(6379)));
        fabrica.afterPropertiesSet();
        plantilla = new StringRedisTemplate(fabrica);
        plantilla.afterPropertiesSet();
    }

    @AfterAll
    static void desconectar() {
        fabrica.destroy();
    }

    @BeforeEach
    void limpiar() {
        Set<String> claves = plantilla.keys("*");
        if (claves != null && !claves.isEmpty()) {
            plantilla.delete(claves);
        }
        adaptador = new RedisCupoPorCuentaAdapter(plantilla);
    }

    @Test
    void debeConcederLosUsosHastaElMaximoYNegarElSiguiente() {
        for (int i = 0; i < 3; i++) {
            assertThat(adaptador.consumir("barrio:v-1", 3, UN_DIA).concedido()).isTrue();
        }

        ResultadoDeCupo cuarto = adaptador.consumir("barrio:v-1", 3, UN_DIA);

        assertThat(cuarto.concedido()).isFalse();
    }

    @Test
    void elCupoAgotadoDebeDecirCuantoFaltaParaQueSeRenueve() {
        for (int i = 0; i < 3; i++) {
            adaptador.consumir("barrio:v-1", 3, UN_DIA);
        }

        ResultadoDeCupo agotado = adaptador.consumir("barrio:v-1", 3, UN_DIA);

        assertThat(agotado.hastaRenovar()).isPositive().isLessThanOrEqualTo(UN_DIA);
    }

    @Test
    void unaClaveNoDebeGastarElCupoDeOtra() {
        for (int i = 0; i < 3; i++) {
            adaptador.consumir("barrio:v-1", 3, UN_DIA);
        }

        assertThat(adaptador.consumir("barrio:v-2", 3, UN_DIA).concedido()).isTrue();
    }

    /** La ventana corre desde el primer uso: si cada intento la renovara, nadie recuperaría nunca el cupo. */
    @Test
    void losUsosSiguientesNoDebenRenovarLaVentana() throws Exception {
        adaptador.consumir("barrio:v-1", 3, Duration.ofSeconds(100));
        Thread.sleep(1100);
        adaptador.consumir("barrio:v-1", 3, Duration.ofSeconds(100));

        Long segundosRestantes = plantilla.getExpire(
                plantilla.keys("*").iterator().next(), java.util.concurrent.TimeUnit.SECONDS);

        assertThat(segundosRestantes).isLessThan(100L);
    }

    @Test
    void laClaveNoDebeGuardarseEnClaro() {
        adaptador.consumir("barrio:correo-sensible@ejemplo.org", 3, UN_DIA);

        assertThat(plantilla.keys("*")).noneMatch(clave -> clave.contains("correo-sensible"));
    }

    /** Falla abierto, como el resto de contadores de Redis: sin Redis no se bloquea a nadie por un límite blando. */
    @Test
    @SuppressWarnings("unchecked")
    void conRedisCaidoDebeConcederEnVezDeNegar() {
        RedisTemplate<String, String> caido = mock(RedisTemplate.class);
        ValueOperations<String, String> operaciones = mock(ValueOperations.class);
        when(caido.opsForValue()).thenReturn(operaciones);
        when(operaciones.increment(anyString())).thenThrow(new RedisConnectionFailureException("Redis caído"));

        assertThat(new RedisCupoPorCuentaAdapter(caido).consumir("barrio:v-1", 3, UN_DIA).concedido()).isTrue();
    }
}
