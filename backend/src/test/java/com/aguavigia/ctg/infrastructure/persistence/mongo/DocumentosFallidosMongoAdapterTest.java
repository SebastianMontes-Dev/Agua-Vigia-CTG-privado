package com.aguavigia.ctg.infrastructure.persistence.mongo;

import com.aguavigia.ctg.domain.DocumentoFallido;
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
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** Prueba de integración contra un MongoDB real (Testcontainers). Requiere Docker. */
@Testcontainers
@DataMongoTest
@Import(DocumentosFallidosMongoAdapter.class)
class DocumentosFallidosMongoAdapterTest {

    private static final Instant T0 = Instant.parse("2026-09-01T12:00:00Z");

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7.0");

    @Autowired
    private DocumentosFallidosMongoAdapter adaptador;
    @Autowired
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void limpiar() {
        mongoTemplate.getDb().getCollection("documentos_fallidos").drop();
    }

    private void guardar(String hash, Instant ultimoIntento, int reintentos) {
        mongoTemplate.save(new DocumentoFallidoDocumento(hash, "acuacar", "https://acuacar.com/" + hash, "Título " + hash,
                "java.lang.RuntimeException: Mongo caído", T0, ultimoIntento, reintentos));
    }

    @Test
    void sinDocumentosFallidosDebeDevolverUnaListaVacia() {
        assertThat(adaptador.masRecientes()).isEmpty();
    }

    @Test
    void debeDevolverTodosLosCamposDelDocumentoFallido() {
        guardar("h-1", T0.plusSeconds(60), 3);

        assertThat(adaptador.masRecientes()).containsExactly(new DocumentoFallido("acuacar", "https://acuacar.com/h-1",
                "Título h-1", "java.lang.RuntimeException: Mongo caído", T0, T0.plusSeconds(60), 3));
    }

    @Test
    void debeOrdenarPorElUltimoIntentoMasRecientePrimero() {
        guardar("h-viejo", T0.plusSeconds(10), 1);
        guardar("h-nuevo", T0.plusSeconds(500), 4);
        guardar("h-medio", T0.plusSeconds(100), 2);

        assertThat(adaptador.masRecientes()).extracting(DocumentoFallido::urlOriginal)
                .containsExactly("https://acuacar.com/h-nuevo", "https://acuacar.com/h-medio", "https://acuacar.com/h-viejo");
    }

    @Test
    void debeLimitarLaListaALosDoscientosMasRecientes() {
        IntStream.range(0, 230).forEach(i -> guardar("h-" + i, T0.plusSeconds(i), 1));

        List<DocumentoFallido> lista = adaptador.masRecientes();

        assertThat(lista).hasSize(200);
        assertThat(lista.get(0).urlOriginal()).isEqualTo("https://acuacar.com/h-229");
    }
}
