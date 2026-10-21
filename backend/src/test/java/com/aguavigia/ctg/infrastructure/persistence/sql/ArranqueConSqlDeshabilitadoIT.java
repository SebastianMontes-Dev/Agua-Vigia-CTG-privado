package com.aguavigia.ctg.infrastructure.persistence.sql;

import com.aguavigia.ctg.domain.port.out.ConsultaAnaliticaPort;
import com.aguavigia.ctg.domain.port.out.TransaccionPort;
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
 * Con `aguavigia.sql.habilitado=false` (el valor por defecto, y lo que usan todas las demás pruebas) el backend es exactamente el de
 * antes de PostgreSQL: ningún bean de JDBC ni de Flyway, y la analítica responde 503 sin tocar ninguna otra base (ADR-100).
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
        "aguavigia.sql.habilitado=false"
})
class ArranqueConSqlDeshabilitadoIT {

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
    void noDebeExistirNingunBeanDeJdbcNiDeFlyway() {
        assertThat(contexto.getBeanNamesForType(DataSource.class)).isEmpty();
        assertThat(contexto.getBeanNamesForType(org.springframework.jdbc.core.JdbcTemplate.class)).isEmpty();
        assertThat(contexto.getBeanNamesForType(org.springframework.jdbc.core.simple.JdbcClient.class)).isEmpty();
        assertThat(contexto.getBeanNamesForType(org.flywaydb.core.Flyway.class)).isEmpty();
        assertThat(contexto.getBeanNamesForType(AlmacenAnaliticoPostgresAdapter.class)).isEmpty();
        assertThat(contexto.getBeanNamesForType(CopiaAnaliticaJob.class)).isEmpty();
    }

    @Test
    void elUnicoGestorDeTransaccionesDebeSeguirSiendoElDeMongo() {
        assertThat(contexto.getBeansOfType(PlatformTransactionManager.class)).hasSize(1)
                .values().first().isInstanceOf(MongoTransactionManager.class);
        assertThat(contexto.getBean(TransaccionPort.class)).isNotNull();
    }

    @Test
    void laConsultaAnaliticaDebeSerLaApagadaYResponder503() throws Exception {
        assertThat(contexto.getBean(ConsultaAnaliticaPort.class)).isInstanceOf(ConsultaAnaliticaApagadaAdapter.class);

        mockMvc.perform(get("/api/analitica/cumplimiento"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.type").value("https://aguavigia.example/errores/servicio-no-disponible"));
    }

    @Test
    void elRestoDelSistemaDebeSeguirUpYRespondiendo() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mockMvc.perform(get("/api/sectores")).andExpect(status().isOk());
    }
}
