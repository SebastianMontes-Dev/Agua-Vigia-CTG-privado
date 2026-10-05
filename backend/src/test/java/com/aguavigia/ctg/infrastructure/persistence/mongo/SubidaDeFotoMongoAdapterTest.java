package com.aguavigia.ctg.infrastructure.persistence.mongo;

import com.aguavigia.ctg.domain.ReporteId;
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

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El token de subida es de un solo uso y de un reporte concreto: quien conozca el id de un reporte ajeno no puede
 * ponerle una foto sin el token que solo recibió su autor.
 */
@Testcontainers
@DataMongoTest
@Import(SubidaDeFotoMongoAdapter.class)
class SubidaDeFotoMongoAdapterTest {

    private static final Instant AHORA = Instant.parse("2026-10-01T15:00:00Z");
    private static final ReporteId REPORTE = new ReporteId("r-1");

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7.0");

    @Autowired
    private SubidaDeFotoMongoAdapter adaptador;
    @Autowired
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void limpiar() {
        mongoTemplate.getDb().getCollection("subidas_foto").drop();
    }

    @Test
    void unTokenVigenteDelMismoReporteDebeConsumirseUnaVez() {
        adaptador.guardar(REPORTE, "hash-1", AHORA.plusSeconds(600));

        assertThat(adaptador.consumir(REPORTE, "hash-1", AHORA)).isTrue();
        assertThat(adaptador.consumir(REPORTE, "hash-1", AHORA)).as("segundo uso").isFalse();
    }

    @Test
    void unTokenDeOtroReporteNoDebeServir() {
        adaptador.guardar(REPORTE, "hash-1", AHORA.plusSeconds(600));

        assertThat(adaptador.consumir(new ReporteId("r-2"), "hash-1", AHORA)).isFalse();
        assertThat(adaptador.consumir(REPORTE, "hash-1", AHORA)).as("sigue vigente para su reporte").isTrue();
    }

    @Test
    void unTokenVencidoNoDebeServir() {
        adaptador.guardar(REPORTE, "hash-1", AHORA.plusSeconds(600));

        assertThat(adaptador.consumir(REPORTE, "hash-1", AHORA.plusSeconds(601))).isFalse();
    }

    @Test
    void unTokenQueNoExisteNoDebeServir() {
        assertThat(adaptador.consumir(REPORTE, "no-existe", AHORA)).isFalse();
    }

    /** Un solo token vivo por reporte: pedir otro invalida el anterior, como en los enlaces de cuenta. */
    @Test
    void guardarOtroTokenDelMismoReporteDebeInvalidarElAnterior() {
        adaptador.guardar(REPORTE, "hash-1", AHORA.plusSeconds(600));
        adaptador.guardar(REPORTE, "hash-2", AHORA.plusSeconds(600));

        assertThat(adaptador.consumir(REPORTE, "hash-1", AHORA)).isFalse();
        assertThat(adaptador.consumir(REPORTE, "hash-2", AHORA)).isTrue();
    }
}
