package com.aguavigia.ctg.compartido.config;

import com.aguavigia.ctg.compartido.secretos.ConfigSistema;
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

/** Contra un MongoDB real: el conversor que usa Spring Data escribe de verdad el nombre fijado por {@link ClaseGuardadaComo}. Requiere Docker. */
@Testcontainers
@DataMongoTest
@Import(MongoClaseGuardadaConfig.class)
class MongoClaseGuardadaConfigTest {

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7.0");

    @Autowired
    private MongoTemplate mongoTemplate;

    @Test
    void unDocumentoGuardadoPorElConversorDeSpringLlevaElNombreDeClaseDeAntes() {
        mongoTemplate.getDb().getCollection("config_sistema").drop();
        var documento = new ConfigSistema();
        documento.setId("prueba-clase");
        documento.setValor("x");

        mongoTemplate.save(documento);

        var crudo = mongoTemplate.getDb().getCollection("config_sistema").find(new org.bson.Document("_id", "prueba-clase")).first();
        assertThat(crudo).isNotNull();
        assertThat(crudo.getString("_class")).isEqualTo("com.aguavigia.ctg.infrastructure.persistence.mongo.ConfigSistemaDocumento");
        assertThat(mongoTemplate.findById("prueba-clase", ConfigSistema.class).getValor()).isEqualTo("x");
    }
}
