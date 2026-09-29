package com.aguavigia.ctg.infrastructure.persistence.redis;

import com.aguavigia.ctg.domain.UsuarioId;
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
import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Testcontainers
class RedisRevocacionSesionAdapterTest {

    private static final UsuarioId ANA = new UsuarioId("u-ana");
    private static final Instant MOMENTO = Instant.parse("2026-09-29T12:00:00.123Z");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private static LettuceConnectionFactory fabrica;
    private static StringRedisTemplate plantilla;
    private RedisRevocacionSesionAdapter adaptador;

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
        adaptador = new RedisRevocacionSesionAdapter(plantilla);
    }

    @Test
    void unaCuentaSinRevocacionNoDebeTenerMarca() {
        assertThat(adaptador.revocadasAntesDe(ANA)).isEmpty();
    }

    @Test
    void debeGuardarElInstanteDeRevocacionConPrecisionDeMilisegundo() {
        adaptador.revocarSesionesAnterioresA(ANA, MOMENTO);

        assertThat(adaptador.revocadasAntesDe(ANA)).contains(MOMENTO);
    }

    @Test
    void laRevocacionDeUnaCuentaNoDebeAfectarAOtra() {
        adaptador.revocarSesionesAnterioresA(ANA, MOMENTO);

        assertThat(adaptador.revocadasAntesDe(new UsuarioId("u-beto"))).isEmpty();
    }

    @Test
    void revocarDeNuevoDebeMoverLaMarcaAlInstanteMasReciente() {
        adaptador.revocarSesionesAnterioresA(ANA, MOMENTO);
        adaptador.revocarSesionesAnterioresA(ANA, MOMENTO.plusSeconds(60));

        assertThat(adaptador.revocadasAntesDe(ANA)).contains(MOMENTO.plusSeconds(60));
    }

    /** Con menos vida que los tokens (8 h, RNF011) la revocación se desharía sola. */
    @Test
    void laMarcaDebeVivirMasQueUnTokenDeOchoHoras() {
        adaptador.revocarSesionesAnterioresA(ANA, MOMENTO);

        Long segundos = plantilla.getExpire("sesion:revocada:u-ana");
        assertThat(Duration.ofSeconds(segundos)).isGreaterThan(Duration.ofHours(8)).isLessThanOrEqualTo(Duration.ofHours(9));
    }

    // ── Falla cerrado: sin Redis no se puede saber si una sesión sigue valiendo ─────────────────────────

    @SuppressWarnings("unchecked")
    @Test
    void sinRedisConsultarLaRevocacionDebeFallarCerrado() {
        RedisTemplate<String, String> caido = mock(RedisTemplate.class);
        ValueOperations<String, String> operaciones = mock(ValueOperations.class);
        when(caido.opsForValue()).thenReturn(operaciones);
        when(operaciones.get(anyString())).thenThrow(new RedisConnectionFailureException("Redis caído"));

        assertThatThrownBy(() -> new RedisRevocacionSesionAdapter(caido).revocadasAntesDe(ANA))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Redis");
    }

    @Test
    void unaMarcaIlegibleTampocoDebeDarseComoSesionValida() {
        plantilla.opsForValue().set("sesion:revocada:u-ana", "no-es-un-numero");

        assertThatThrownBy(() -> adaptador.revocadasAntesDe(ANA)).isInstanceOf(IllegalStateException.class);
    }
}
