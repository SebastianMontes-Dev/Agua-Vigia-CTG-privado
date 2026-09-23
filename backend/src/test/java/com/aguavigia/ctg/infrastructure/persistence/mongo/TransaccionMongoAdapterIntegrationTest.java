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
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

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
