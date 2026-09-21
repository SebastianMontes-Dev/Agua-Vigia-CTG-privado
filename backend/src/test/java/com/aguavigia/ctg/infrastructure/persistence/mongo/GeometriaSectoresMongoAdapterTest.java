package com.aguavigia.ctg.infrastructure.persistence.mongo;

import com.aguavigia.ctg.domain.GeometriaSector;
import com.aguavigia.ctg.domain.SectorId;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Requiere Docker, como el resto de las pruebas de adaptadores Mongo. */
@Testcontainers
@DataMongoTest
@Import(GeometriaSectoresMongoAdapter.class)
class GeometriaSectoresMongoAdapterTest {

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7.0");

    @Autowired
    private GeometriaSectoresMongoAdapter adaptador;

    @Autowired
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void limpiar() {
        mongoTemplate.getDb().getCollection("sectores").drop();
    }

    private void sembrar(String slug, String nombre, org.bson.Document geometria) {
        org.bson.Document documento = new org.bson.Document("slug", slug).append("nombre", nombre);
        if (geometria != null) {
            documento.append("geometry", geometria);
        }
        mongoTemplate.getDb().getCollection("sectores").insertOne(documento);
    }

    @Test
    void debeListarLaGeometriaDeCadaSectorConSuIdYNombre() {
        sembrar("manga", "MANGA", new org.bson.Document("type", "Polygon")
                .append("coordinates", List.of(List.of(
                        List.of(-75.55, 10.40), List.of(-75.54, 10.40),
                        List.of(-75.54, 10.41), List.of(-75.55, 10.40)))));

        List<GeometriaSector> resultado = adaptador.listar();

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).id()).isEqualTo(new SectorId("manga"));
        assertThat(resultado.get(0).nombre()).isEqualTo("MANGA");
        assertThat(resultado.get(0).geometria()).containsEntry("type", "Polygon");
    }

    @Test
    void debeOmitirLosSectoresQueNoTienenGeometria() {
        sembrar("sin-forma", "SIN FORMA", null);

        assertThat(adaptador.listar()).isEmpty();
    }

    @Test
    void debeDevolverLosSectoresOrdenadosPorNombre() {
        org.bson.Document punto = new org.bson.Document("type", "Point").append("coordinates", List.of(-75.5, 10.4));
        sembrar("zapatero", "ZAPATERO", punto);
        sembrar("bocagrande", "BOCAGRANDE", punto);

        assertThat(adaptador.listar()).extracting(g -> g.id().valor()).containsExactly("bocagrande", "zapatero");
    }
}
