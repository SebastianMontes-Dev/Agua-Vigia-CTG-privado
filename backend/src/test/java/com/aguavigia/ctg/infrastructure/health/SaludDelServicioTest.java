package com.aguavigia.ctg.infrastructure.health;

import com.aguavigia.ctg.infrastructure.ingest.EstadoColectorRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Una fuente externa caída (acuacar.com, un RSS) es un problema de la ingesta, no del proceso: no
 * debe sacar a la réplica de rotación. RNF007 sigue en pie — el estado global de `/actuator/health`
 * refleja los colectores —, pero lo que usan Docker y el balanceador para decidir si la instancia
 * vive (`liveness`) o recibe tráfico (`readiness`) solo mira lo que la instancia necesita para servir:
 * Mongo y Redis.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.mail.host=localhost",
        "spring.mail.port=3025",
        "aguavigia.ingesta.user-agent=",
        "aguavigia.ingesta.intervalo-ms=86400000",
        "aguavigia.rate-limit.reglas=",
        "management.endpoint.health.show-details=always"
})
class SaludDelServicioTest {

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7.0");

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EstadoColectorRegistry colectores;

    private void tumbarUnColector() {
        for (int i = 0; i < 3; i++) {
            colectores.registrarFallo("acuacar-api", "HTTP 502");
        }
    }

    @Test
    void livenessYReadinessDebenSeguirArribaConUnColectorCaido() throws Exception {
        tumbarUnColector();

        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void elEstadoGlobalDebeSeguirReflejandoLosColectoresPorRnf007() throws Exception {
        tumbarUnColector();

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.components.colectores.status").value("DOWN"));
    }

    /** Un SMTP lento o caído no debe tumbar el estado del servicio: el correo es asíncrono y reintentable. */
    @Test
    void elCorreoNoDebeFormarParteDelEstadoDeSalud() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(jsonPath("$.components.mail").doesNotExist());
    }

    @Test
    void readinessDebeDependerDeMongoYRedis() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(jsonPath("$.components.mongo.status").value("UP"))
                .andExpect(jsonPath("$.components.redis.status").value("UP"));
    }
}
