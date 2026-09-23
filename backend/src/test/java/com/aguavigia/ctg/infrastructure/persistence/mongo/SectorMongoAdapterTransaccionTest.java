package com.aguavigia.ctg.infrastructure.persistence.mongo;

import com.aguavigia.ctg.application.SectorActualizadoEvent;
import com.aguavigia.ctg.domain.EstadoServicio;
import com.aguavigia.ctg.domain.Sector;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.port.out.SectorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.interceptor.SimpleKey;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contexto real de Spring (no `@DataMongoTest`): la caché ("sectores", Redis) y la transacción
 * (`MongoTransactionManager`) participan a la vez, que es justo lo que se prueba — que
 * `SectorMongoAdapter` difiere el evento y la invalidación de caché hasta que la transacción que
 * lo envuelve confirme (Fase 3 de `plan-validacion-backend.md`).
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SectorMongoAdapterTransaccionTest {

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer(DockerImageName.parse("mongo:7.0"));

    @Container
    @ServiceConnection
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @TestConfiguration
    static class CapturadorDeEventos {
        @Bean
        RecolectorDeEventos recolectorDeEventos() {
            return new RecolectorDeEventos();
        }
    }

    static class RecolectorDeEventos {
        final List<SectorActualizadoEvent> eventos = new CopyOnWriteArrayList<>();

        @EventListener
        public void alActualizarSector(SectorActualizadoEvent event) {
            eventos.add(event);
        }
    }

    @Autowired
    private SectorRepository sectores;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private RecolectorDeEventos recolector;

    @Autowired
    private MongoTemplate mongoTemplate;

    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void montar() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        recolector.eventos.clear();
        mongoTemplate.getDb().getCollection("sectores").drop();
        Cache cache = cacheManager.getCache("sectores");
        if (cache != null) {
            cache.clear();
        }
    }

    private Cache cacheDeSectores() {
        return cacheManager.getCache("sectores");
    }

    @Test
    void debePublicarElEventoInmediatamenteCuandoNoHayTransaccionActiva() {
        sectores.guardar(new Sector(new SectorId("manga"), "Manga", 1000, EstadoServicio.SIN_SERVICIO));

        assertThat(recolector.eventos).hasSize(1);
    }

    @Test
    void debeDiferirElEventoHastaQueLaTransaccionConfirme() {
        transactionTemplate.execute(status -> {
            sectores.guardar(new Sector(new SectorId("manga"), "Manga", 1000, EstadoServicio.SIN_SERVICIO));
            assertThat(recolector.eventos)
                    .as("el evento no debe publicarse mientras la transacción sigue abierta")
                    .isEmpty();
            return null;
        });

        assertThat(recolector.eventos)
                .as("al confirmar la transacción, el evento diferido debe publicarse")
                .hasSize(1);
    }

    @Test
    void noDebePublicarElEventoSiLaTransaccionSeRevierte() {
        assertThatThrownBy(() -> transactionTemplate.execute(status -> {
            sectores.guardar(new Sector(new SectorId("manga"), "Manga", 1000, EstadoServicio.SIN_SERVICIO));
            throw new IllegalStateException("falla tras guardar el sector");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(recolector.eventos)
                .as("un evento cuya transacción se revirtió nunca debe llegar a los suscriptores")
                .isEmpty();
    }

    @Test
    void debeDiferirLaInvalidacionDeCacheHastaQueLaTransaccionConfirme() {
        sectores.listarTodos();
        assertThat(cacheDeSectores().get(SimpleKey.EMPTY)).isNotNull();

        transactionTemplate.execute(status -> {
            sectores.guardar(new Sector(new SectorId("manga"), "Manga", 1000, EstadoServicio.SIN_SERVICIO));
            assertThat(cacheDeSectores().get(SimpleKey.EMPTY))
                    .as("la caché no debe invalidarse mientras la transacción sigue abierta")
                    .isNotNull();
            return null;
        });

        assertThat(cacheDeSectores().get(SimpleKey.EMPTY))
                .as("al confirmar, la caché debe quedar invalidada")
                .isNull();
    }
}
