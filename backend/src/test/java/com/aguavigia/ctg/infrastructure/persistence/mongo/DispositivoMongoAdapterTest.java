package com.aguavigia.ctg.infrastructure.persistence.mongo;

import com.aguavigia.ctg.domain.Dispositivo;
import com.aguavigia.ctg.domain.DispositivoId;
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

import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Prueba de integración contra un MongoDB real (Testcontainers). Requiere Docker. */
@Testcontainers
@DataMongoTest
@Import(DispositivoMongoAdapter.class)
class DispositivoMongoAdapterTest {

    private static final Instant T0 = Instant.parse("2026-10-01T15:00:00Z");
    private static final DispositivoId ID = new DispositivoId("7f1c2b9e-4d3a-4e5b-8c6d-1a2b3c4d5e6f");

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7.0");

    @Autowired
    private DispositivoMongoAdapter adaptador;
    @Autowired
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void limpiar() {
        mongoTemplate.getDb().getCollection("dispositivos").drop();
    }

    private List<Document> guardados() {
        return mongoTemplate.getDb().getCollection("dispositivos").find().into(new java.util.ArrayList<>());
    }

    @Test
    void debeGuardarElDispositivoConSusFechas() {
        adaptador.guardar(new Dispositivo(ID, T0, T0.plusSeconds(30)));

        assertThat(guardados()).hasSize(1);
        Document documento = guardados().get(0);
        assertThat(((Date) documento.get("creadoEn")).toInstant()).isEqualTo(T0);
        assertThat(((Date) documento.get("ultimoVisto")).toInstant()).isEqualTo(T0.plusSeconds(30));
    }

    /** La base guarda el SHA-256 del id: copiar la colección no entrega ningún id que se pueda presentar. */
    @Test
    void elIdentificadorGuardadoDebeSerSuHashYNuncaElIdEnClaro() {
        adaptador.guardar(new Dispositivo(ID, T0, T0));

        Document documento = guardados().get(0);
        assertThat(documento.getString("_id")).matches("[0-9a-f]{64}").isNotEqualTo(ID.valor());
        assertThat(documento.values()).noneMatch(valor -> ID.valor().equals(valor));
    }

    @Test
    void elMismoIdDebeGuardarseEnElMismoDocumento() {
        adaptador.guardar(new Dispositivo(ID, T0, T0));
        adaptador.guardar(new Dispositivo(ID, T0, T0.plusSeconds(60)));

        assertThat(guardados()).hasSize(1);
    }

    @Test
    void debeEncontrarUnDispositivoGuardadoPorSuId() {
        adaptador.guardar(new Dispositivo(ID, T0, T0.plusSeconds(30)));

        Dispositivo leido = adaptador.buscarPorId(ID).orElseThrow();

        assertThat(leido.id()).isEqualTo(ID);
        assertThat(leido.creadoEn()).isEqualTo(T0);
        assertThat(leido.ultimoVisto()).isEqualTo(T0.plusSeconds(30));
    }

    @Test
    void unDispositivoQueNoExisteDebeVolverVacio() {
        assertThat(adaptador.buscarPorId(ID)).isEmpty();
    }

    @Test
    void registrarElUsoDebeActualizarSoloElUltimoVisto() {
        adaptador.guardar(new Dispositivo(ID, T0, T0));

        adaptador.registrarVisto(ID, T0.plusSeconds(3600));

        Dispositivo leido = adaptador.buscarPorId(ID).orElseThrow();
        assertThat(leido.creadoEn()).isEqualTo(T0);
        assertThat(leido.ultimoVisto()).isEqualTo(T0.plusSeconds(3600));
    }

    /** Registrar el uso de un dispositivo que ya venció no puede resucitarlo: la identidad se pide de nuevo. */
    @Test
    void registrarElUsoDeUnDispositivoInexistenteNoDebeCrearlo() {
        adaptador.registrarVisto(ID, T0);

        assertThat(guardados()).isEmpty();
    }

    @Test
    void dosIdsDistintosDebenGuardarseEnDocumentosDistintos() {
        adaptador.guardar(new Dispositivo(ID, T0, T0));
        adaptador.guardar(new Dispositivo(new DispositivoId("otro"), T0, T0));

        assertThat(guardados()).hasSize(2);
    }
}
