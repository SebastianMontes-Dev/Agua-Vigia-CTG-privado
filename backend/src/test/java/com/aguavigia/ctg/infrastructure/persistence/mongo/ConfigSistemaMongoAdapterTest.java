package com.aguavigia.ctg.infrastructure.persistence.mongo;

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
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/** Prueba de integración contra un MongoDB real (Testcontainers). Requiere Docker. */
@Testcontainers
@DataMongoTest
@Import(ConfigSistemaMongoAdapter.class)
class ConfigSistemaMongoAdapterTest {

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7.0");

    @Autowired
    private MongoTemplate mongoTemplate;

    private ConfigSistemaMongoAdapter adaptador;

    /** Una instancia por prueba: el adaptador guarda en memoria lo ya leído, y aquí se vacía la colección entre pruebas. */
    @BeforeEach
    void limpiar() {
        mongoTemplate.getDb().getCollection("config_sistema").drop();
        adaptador = new ConfigSistemaMongoAdapter(mongoTemplate);
    }

    @Test
    void laPrimeraVezDebeGenerarUnSecretoLargoYAleatorio() {
        String secreto = adaptador.obtenerOCrear("dispositivos");

        // 32 bytes en base64url sin relleno = 43 caracteres.
        assertThat(secreto).hasSizeGreaterThanOrEqualTo(43).matches("[A-Za-z0-9_-]+");
    }

    @Test
    void laSegundaLlamadaDebeDevolverElMismoSecreto() {
        assertThat(adaptador.obtenerOCrear("dispositivos")).isEqualTo(adaptador.obtenerOCrear("dispositivos"));
    }

    /** Un reinicio no puede invalidar todos los tokens emitidos: el secreto vive en la base, no en memoria. */
    @Test
    void unaInstanciaNuevaDebeLeerElSecretoYaGuardado() {
        String original = adaptador.obtenerOCrear("dispositivos");

        String leidoPorOtraInstancia = new ConfigSistemaMongoAdapter(mongoTemplate).obtenerOCrear("dispositivos");

        assertThat(leidoPorOtraInstancia).isEqualTo(original);
    }

    @Test
    void nombresDistintosDebenTenerSecretosDistintos() {
        assertThat(adaptador.obtenerOCrear("dispositivos")).isNotEqualTo(adaptador.obtenerOCrear("otro"));
    }

    /** Dos instancias arrancando a la vez deben acabar con el mismo secreto, no con uno cada una. */
    @Test
    void llamadasSimultaneasDebenAcabarConUnSoloSecreto() throws Exception {
        ExecutorService hilos = Executors.newFixedThreadPool(8);
        try {
            List<Callable<String>> tareas = java.util.stream.IntStream.range(0, 8)
                    .<Callable<String>>mapToObj(i -> () -> new ConfigSistemaMongoAdapter(mongoTemplate)
                            .obtenerOCrear("dispositivos"))
                    .toList();

            List<Future<String>> resultados = hilos.invokeAll(tareas);

            java.util.Set<String> distintos = new java.util.HashSet<>();
            for (Future<String> resultado : resultados) {
                distintos.add(resultado.get());
            }
            assertThat(distintos).hasSize(1);
        } finally {
            hilos.shutdownNow();
        }
    }
}
