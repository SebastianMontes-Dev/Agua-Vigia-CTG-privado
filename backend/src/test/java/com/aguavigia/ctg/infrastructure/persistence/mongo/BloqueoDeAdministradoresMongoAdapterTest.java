package com.aguavigia.ctg.infrastructure.persistence.mongo;

import com.aguavigia.ctg.domain.port.out.RelojPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * Prueba de integración contra un MongoDB real (Testcontainers). Requiere Docker.
 */
@Testcontainers
@DataMongoTest
@Import({BloqueoDeAdministradoresMongoAdapter.class, BloqueoDeAdministradoresMongoAdapterTest.RelojControlable.class})
class BloqueoDeAdministradoresMongoAdapterTest {

    private static final Instant INICIO = Instant.parse("2026-09-23T12:00:00Z");

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7.0");

    static class RelojControlable {
        static final AtomicReference<Instant> AHORA = new AtomicReference<>(INICIO);

        @Bean
        RelojPort reloj() {
            return AHORA::get;
        }
    }

    @Autowired
    private BloqueoDeAdministradoresMongoAdapter adaptador;
    @Autowired
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void limpiar() {
        mongoTemplate.getDb().getCollection("bloqueos_administracion").drop();
        RelojControlable.AHORA.set(INICIO);
    }

    @Test
    void debeAdquirirElBloqueoLaPrimeraVez() {
        assertThat(adaptador.adquirir()).isPresent();
    }

    @Test
    void noDebeAdquirirElBloqueoMientrasSigaVigente() {
        adaptador.adquirir();

        assertThat(adaptador.adquirir()).isEmpty();
    }

    @Test
    void debePermitirAdquirirDeNuevoTrasLiberar() {
        String primero = adaptador.adquirir().orElseThrow();

        adaptador.liberar(primero);

        assertThat(adaptador.adquirir()).isPresent();
    }

    /** Si el proceso muere con el bloqueo tomado, no debe quedar tomado para siempre. */
    @Test
    void debePermitirAdquirirDeNuevoTrasExpirarSinLiberar() {
        adaptador.adquirir();
        RelojControlable.AHORA.set(INICIO.plusSeconds(30));

        assertThat(adaptador.adquirir()).isPresent();
    }

    /** Liberar con un token viejo no debe soltar el bloqueo de quien lo adquirió después. */
    @Test
    void liberarConUnTokenYaVencidoNoDebeAfectarAUnaAdquisicionPosterior() {
        String primero = adaptador.adquirir().orElseThrow();
        RelojControlable.AHORA.set(INICIO.plusSeconds(30));
        adaptador.adquirir().orElseThrow();

        adaptador.liberar(primero);

        assertThat(adaptador.adquirir()).isEmpty();
    }

    @Test
    void ejecutarExclusivoDebeDevolverElResultadoDeLaAccionYLiberarElBloqueo() {
        String resultado = adaptador.ejecutarExclusivo(() -> "listo");

        assertThat(resultado).isEqualTo("listo");
        assertThat(adaptador.adquirir()).isPresent();
    }

    @Test
    void ejecutarExclusivoDebeRechazarSiOtroCambioTieneElBloqueo() {
        adaptador.adquirir();

        assertThatIllegalStateException()
                .isThrownBy(() -> adaptador.ejecutarExclusivo(() -> "no debería ejecutarse"));
    }

    @Test
    void ejecutarExclusivoDebeLiberarElBloqueoAunqueLaAccionLance() {
        try {
            adaptador.ejecutarExclusivo(() -> {
                throw new RuntimeException("boom");
            });
        } catch (RuntimeException esperada) {
            // la de la propia acción; lo que importa es que el bloqueo haya quedado libre
        }

        assertThat(adaptador.adquirir()).isPresent();
    }
}
