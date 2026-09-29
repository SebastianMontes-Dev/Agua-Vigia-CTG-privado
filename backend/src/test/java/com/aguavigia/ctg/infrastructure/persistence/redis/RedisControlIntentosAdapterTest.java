package com.aguavigia.ctg.infrastructure.persistence.redis;

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
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Testcontainers
class RedisControlIntentosAdapterTest {

    private static final Duration VENTANA = Duration.ofMinutes(15);
    private static final Duration BLOQUEO = Duration.ofMinutes(15);

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private static LettuceConnectionFactory fabrica;
    private static StringRedisTemplate plantilla;
    private RedisControlIntentosAdapter adaptador;

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
        adaptador = new RedisControlIntentosAdapter(plantilla);
    }

    @Test
    void mientrasNoSeAlcanceElMaximoNoDebeHaberBloqueo() {
        for (int i = 0; i < 4; i++) {
            adaptador.registrarFallo("ana@ejemplo.org", VENTANA, 5, BLOQUEO);
        }

        assertThat(adaptador.bloqueoVigente("ana@ejemplo.org")).isEmpty();
    }

    @Test
    void alLlegarAlMaximoDebeBloquearPorLaDuracionIndicada() {
        for (int i = 0; i < 5; i++) {
            adaptador.registrarFallo("ana@ejemplo.org", VENTANA, 5, BLOQUEO);
        }

        Duration restante = adaptador.bloqueoVigente("ana@ejemplo.org").orElseThrow();
        assertThat(restante).isPositive().isLessThanOrEqualTo(BLOQUEO).isGreaterThan(BLOQUEO.minusSeconds(30));
    }

    @Test
    void elBloqueoDeUnaCuentaNoDebeAfectarAOtra() {
        for (int i = 0; i < 5; i++) {
            adaptador.registrarFallo("ana@ejemplo.org", VENTANA, 5, BLOQUEO);
        }

        assertThat(adaptador.bloqueoVigente("beto@ejemplo.org")).isEmpty();
    }

    /** La ventana corre desde el primer fallo: renovarla en cada intento haría permanente el bloqueo de quien insiste. */
    @Test
    void laVentanaDebeContarseDesdeElPrimerFalloYNoRenovarseEnCadaIntento() {
        adaptador.registrarFallo("ana@ejemplo.org", Duration.ofSeconds(60), 5, BLOQUEO);
        String clave = plantilla.keys("login:fallos:*").iterator().next();
        Long ttlInicial = plantilla.getExpire(clave);

        adaptador.registrarFallo("ana@ejemplo.org", Duration.ofHours(10), 5, BLOQUEO);

        assertThat(plantilla.getExpire(clave)).isLessThanOrEqualTo(ttlInicial).isLessThanOrEqualTo(60L);
    }

    @Test
    void alBloquearDebeBorrarseElContadorDeFallos() {
        for (int i = 0; i < 5; i++) {
            adaptador.registrarFallo("ana@ejemplo.org", VENTANA, 5, BLOQUEO);
        }

        assertThat(plantilla.keys("login:fallos:*")).isEmpty();
        assertThat(plantilla.keys("login:bloqueo:*")).hasSize(1);
    }

    @Test
    void limpiarIntentosDebeQuitarFallosYBloqueo() {
        for (int i = 0; i < 5; i++) {
            adaptador.registrarFallo("ana@ejemplo.org", VENTANA, 5, BLOQUEO);
        }

        adaptador.limpiarIntentos("ana@ejemplo.org");

        assertThat(adaptador.bloqueoVigente("ana@ejemplo.org")).isEmpty();
        assertThat(plantilla.keys("login:*")).isEmpty();
    }

    /** Redis suele ser el componente con menos controles: no debe guardar la lista de correos atacados. */
    @Test
    void ningunaClaveDebeContenerElCorreoEnClaro() {
        adaptador.registrarFallo("ana@ejemplo.org", VENTANA, 5, BLOQUEO);
        adaptador.consumirPorPrimeraVez("ana@ejemplo.org:123456", VENTANA);

        Set<String> claves = plantilla.keys("*");
        assertThat(claves).isNotEmpty().noneMatch(clave -> clave.contains("ana") || clave.contains("ejemplo"));
    }

    @Test
    void unCodigoSoloDebeConsumirsePorPrimeraVezDentroDeLaVentana() {
        assertThat(adaptador.consumirPorPrimeraVez("ana@ejemplo.org:123456", VENTANA)).isTrue();
        assertThat(adaptador.consumirPorPrimeraVez("ana@ejemplo.org:123456", VENTANA)).isFalse();
        assertThat(adaptador.consumirPorPrimeraVez("ana@ejemplo.org:654321", VENTANA)).isTrue();
    }

    @Test
    void unCodigoVuelveAPoderUsarseCuandoVenceLaVentana() throws InterruptedException {
        assertThat(adaptador.consumirPorPrimeraVez("beto@ejemplo.org:111111", Duration.ofMillis(300))).isTrue();
        Thread.sleep(500);

        assertThat(adaptador.consumirPorPrimeraVez("beto@ejemplo.org:111111", Duration.ofMillis(300))).isTrue();
    }

    // ── Falla abierto: sin Redis la cuenta queda protegida por su contraseña, no fuera del sistema ──────

    @SuppressWarnings("unchecked")
    private RedisControlIntentosAdapter adaptadorConRedisCaido() {
        RedisTemplate<String, String> caido = mock(RedisTemplate.class);
        ValueOperations<String, String> operaciones = mock(ValueOperations.class);
        RedisConnectionFailureException fallo = new RedisConnectionFailureException("Redis caído");
        when(caido.opsForValue()).thenReturn(operaciones);
        when(operaciones.increment(anyString())).thenThrow(fallo);
        when(operaciones.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenThrow(fallo);
        when(caido.getExpire(anyString(), any(TimeUnit.class))).thenThrow(fallo);
        when(caido.delete(anyString())).thenThrow(fallo);
        return new RedisControlIntentosAdapter(caido);
    }

    @Test
    void sinRedisRegistrarUnFalloNoDebeLanzarNada() {
        adaptadorConRedisCaido().registrarFallo("ana@ejemplo.org", VENTANA, 5, BLOQUEO);
    }

    @Test
    void sinRedisNoDebeHaberBloqueoVigente() {
        assertThat(adaptadorConRedisCaido().bloqueoVigente("ana@ejemplo.org")).isEmpty();
    }

    @Test
    void sinRedisLimpiarIntentosNoDebeLanzarNada() {
        adaptadorConRedisCaido().limpiarIntentos("ana@ejemplo.org");
    }

    @Test
    void sinRedisUnCodigoDebeTomarseComoPrimeraVez() {
        assertThat(adaptadorConRedisCaido().consumirPorPrimeraVez("ana@ejemplo.org:123456", VENTANA)).isTrue();
    }
}
