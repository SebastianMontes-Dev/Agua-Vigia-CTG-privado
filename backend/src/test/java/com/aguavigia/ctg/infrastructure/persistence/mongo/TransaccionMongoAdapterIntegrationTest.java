package com.aguavigia.ctg.infrastructure.persistence.mongo;

import com.aguavigia.ctg.infrastructure.config.MongoTransaccionConfig;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Prueba de integración contra un Mongo real (Testcontainers arranca su *replica set* de un nodo por
 * defecto, mismo hecho verificado en `ADR-063`): demuestra que {@link TransaccionMongoAdapter}
 * agrupa escrituras en dos colecciones distintas ("sectores" y "eventos_bitacora" aquí, sin usar los
 * documentos de dominio reales — lo que se prueba es la atomicidad de la unidad de trabajo, no el
 * mapeo de cada adaptador) como una sola unidad: todo o nada.
 */
@Testcontainers
@DataMongoTest
@Import({TransaccionMongoAdapter.class, MongoTransaccionConfig.class})
class TransaccionMongoAdapterIntegrationTest {

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7.0");

    @Autowired
    private TransaccionMongoAdapter transaccion;

    @Autowired
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void limpiar() {
        mongoTemplate.getDb().getCollection("sectores").drop();
        mongoTemplate.getDb().getCollection("eventos_bitacora").drop();
    }

    @Test
    void debeConfirmarLasDosEscriturasCuandoLaAccionTermineBien() {
        transaccion.ejecutar(() -> {
            mongoTemplate.getCollection("sectores").insertOne(new Document("slug", "manga").append("estadoActual", "SIN_SERVICIO"));
            mongoTemplate.getCollection("eventos_bitacora").insertOne(new Document("sectorId", "manga"));
            return null;
        });

        assertThat(mongoTemplate.getCollection("sectores").countDocuments()).isEqualTo(1);
        assertThat(mongoTemplate.getCollection("eventos_bitacora").countDocuments()).isEqualTo(1);
    }

    @Test
    void unaFallaTrasLaPrimeraEscrituraDebeRevertirTambienLaPrimera() {
        assertThatThrownBy(() -> transaccion.ejecutar(() -> {
            mongoTemplate.getCollection("sectores").insertOne(new Document("slug", "manga").append("estadoActual", "SIN_SERVICIO"));
            throw new IllegalStateException("la bitácora no se pudo anexar");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(mongoTemplate.getCollection("sectores").countDocuments())
                .as("el sector no debe quedar guardado si el evento de bitácora no se anexó")
                .isEqualTo(0);
        assertThat(mongoTemplate.getCollection("eventos_bitacora").countDocuments()).isEqualTo(0);
    }

    /**
     * Dos transacciones reales escriben el mismo documento: la segunda recibe un conflicto de escritura de Mongo,
     * que `MongoTemplate` traduce a una excepción de Spring. El adaptador debe reconocerlo como transitorio y
     * reintentar; antes solo miraba `MongoException` y la perdedora salía como error.
     */
    @Test
    void dosTransaccionesConcurrentesSobreElMismoDocumentoDebenTerminarAmbasPorElReintento() throws Exception {
        mongoTemplate.getCollection("sectores").insertOne(new Document("slug", "manga").append("n", 0));
        Query todos = new Query();
        CountDownLatch aEscribio = new CountDownLatch(1);
        CountDownLatch bFallo = new CountDownLatch(1);
        CountDownLatch aConfirmo = new CountDownLatch(1);
        AtomicInteger intentosDeB = new AtomicInteger();

        ExecutorService hilos = Executors.newFixedThreadPool(2);
        try {
            Future<?> a = hilos.submit(() -> {
                transaccion.ejecutar(() -> {
                    mongoTemplate.updateFirst(todos, new Update().inc("n", 1), "sectores");
                    aEscribio.countDown();
                    esperar(bFallo);
                    return null;
                });
                aConfirmo.countDown();
            });
            Future<?> b = hilos.submit(() -> {
                esperar(aEscribio);
                transaccion.ejecutar(() -> {
                    if (intentosDeB.incrementAndGet() > 1) {
                        esperar(aConfirmo);
                    }
                    try {
                        mongoTemplate.updateFirst(todos, new Update().inc("n", 1), "sectores");
                    } catch (RuntimeException falla) {
                        bFallo.countDown();
                        throw falla;
                    }
                    return null;
                });
            });

            a.get(30, TimeUnit.SECONDS);
            b.get(30, TimeUnit.SECONDS);
        } finally {
            hilos.shutdownNow();
        }

        assertThat(intentosDeB.get()).as("B choca con A una vez y reintenta").isEqualTo(2);
        assertThat(mongoTemplate.getCollection("sectores").find().first().getInteger("n")).isEqualTo(2);
    }

    private static void esperar(CountDownLatch cerrojo) {
        try {
            if (!cerrojo.await(20, TimeUnit.SECONDS)) {
                throw new IllegalStateException("la otra transacción no avanzó a tiempo");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    @Test
    void unaTransaccionConfirmadaNoAfectaEscriturasDeOtraFallidaPosterior() {
        transaccion.ejecutar(() -> {
            mongoTemplate.getCollection("sectores").insertOne(new Document("slug", "manga").append("estadoActual", "SIN_SERVICIO"));
            mongoTemplate.getCollection("eventos_bitacora").insertOne(new Document("sectorId", "manga"));
            return null;
        });

        assertThatCode(() -> transaccion.ejecutar(() -> {
            mongoTemplate.getCollection("sectores").insertOne(new Document("slug", "pie-de-la-popa").append("estadoActual", "SIN_SERVICIO"));
            throw new IllegalStateException("falla en el segundo sector");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(mongoTemplate.getCollection("sectores").countDocuments())
                .as("solo el sector de la primera transacción, ya confirmada, debe sobrevivir")
                .isEqualTo(1);
        assertThat(mongoTemplate.getCollection("eventos_bitacora").countDocuments()).isEqualTo(1);
    }
}
