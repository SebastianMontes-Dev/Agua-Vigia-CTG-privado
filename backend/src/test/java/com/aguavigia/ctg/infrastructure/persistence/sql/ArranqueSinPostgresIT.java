package com.aguavigia.ctg.infrastructure.persistence.sql;

import com.aguavigia.ctg.domain.port.out.ConsultaAnaliticaPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La regla 4 de ADR-100: con la copia habilitada y PostgreSQL inalcanzable el backend arranca, está `UP`, sigue respondiendo lo de siempre
 * y solo la analítica contesta 503. El puerto 1 de localhost no tiene a nadie escuchando.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.mail.host=localhost",
        "spring.mail.port=3025",
        "aguavigia.ingesta.user-agent=",
        "aguavigia.ingesta.intervalo-ms=86400000",
        "aguavigia.rate-limit.reglas=",
        "aguavigia.sql.habilitado=true",
        "aguavigia.sql.url=jdbc:postgresql://127.0.0.1:1/aguavigia_analitica?connectTimeout=1",
        // Que el job no llegue a correr durante la prueba: el 503 de abajo es de la consulta, no de una copia a medias.
        "aguavigia.sql.copia-retraso-inicial-ms=86400000"
})
class ArranqueSinPostgresIT {

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7.0");

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @Autowired
    private ApplicationContext contexto;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void elContextoDebeArrancarConLosBeansDeSqlPeroSinConectar() {
        assertThat(contexto.getBeanNamesForType(DataSource.class)).containsExactly("analiticaDataSource");
        assertThat(contexto.getBean(ConsultaAnaliticaPort.class)).isInstanceOf(ConsultaAnaliticaPostgresAdapter.class);
        assertThat(contexto.getBeanNamesForType(CopiaAnaliticaJob.class)).hasSize(1);
    }

    @Test
    void elUnicoGestorDeTransaccionesDebeSeguirSiendoElDeMongoAunqueSqlEsteHabilitado() {
        assertThat(contexto.getBeansOfType(PlatformTransactionManager.class)).hasSize(1)
                .values().first().isInstanceOf(MongoTransactionManager.class);
    }

    @Test
    void elSistemaDebeEstarUpYResponderLoDeSiempre() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mockMvc.perform(get("/actuator/health"))
                .andExpect(jsonPath("$.status").value("UP"));
        mockMvc.perform(get("/api/sectores")).andExpect(status().isOk());
        // Mongo sin cortes cerrados responde 400 (no hay nada que medir); lo que importa es que contesta Mongo y no un 503 de SQL.
        mockMvc.perform(get("/api/cumplimiento")).andExpect(status().isBadRequest());
    }

    @Test
    void laAnaliticaDebeResponder503ConProblemDetail() throws Exception {
        mockMvc.perform(get("/api/analitica/cumplimiento"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.type").value("https://aguavigia.example/errores/servicio-no-disponible"));
        mockMvc.perform(get("/api/analitica/cumplimiento/mensual")).andExpect(status().isServiceUnavailable());
    }
}
