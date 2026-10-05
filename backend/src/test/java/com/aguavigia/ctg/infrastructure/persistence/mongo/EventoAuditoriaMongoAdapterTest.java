package com.aguavigia.ctg.infrastructure.persistence.mongo;

import com.aguavigia.ctg.domain.AccionAuditada;
import com.aguavigia.ctg.domain.AuditoriaId;
import com.aguavigia.ctg.domain.EventoAuditoria;
import com.aguavigia.ctg.domain.Pagina;
import com.aguavigia.ctg.domain.UsuarioId;
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

/** Prueba de integración contra un MongoDB real (Testcontainers). Requiere Docker. */
@Testcontainers
@DataMongoTest
@Import(EventoAuditoriaMongoAdapter.class)
class EventoAuditoriaMongoAdapterTest {

    private static final Instant T0 = Instant.parse("2026-09-01T12:00:00Z");

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7.0");

    @Autowired
    private EventoAuditoriaMongoAdapter adaptador;
    @Autowired
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void limpiar() {
        mongoTemplate.getDb().getCollection("auditoria_cuentas").drop();
    }

    private static EventoAuditoria evento(String id, AccionAuditada accion, Instant cuando) {
        return new EventoAuditoria(new AuditoriaId(id), accion, new UsuarioId("admin-1"), "admin@aguavigia.local",
                new UsuarioId("u-9"), "ana@ejemplo.org", "Detalle de " + id, "10.0.0.7", cuando);
    }

    @Test
    void debeGuardarYDevolverElEventoConTodosSusCampos() {
        EventoAuditoria original = evento("e-1", AccionAuditada.CUENTA_APROBADA, T0);

        assertThat(adaptador.registrar(original)).isEqualTo(original);

        EventoAuditoria leido = adaptador.listar(0, 10).contenido().get(0);
        assertThat(leido).isEqualTo(original);
    }

    /** El sistema actúa sin autor (la siembra del ADMIN, los enlaces de correo): esos campos son nulos y deben volver nulos. */
    @Test
    void debeConservarLosCamposOpcionalesNulos() {
        EventoAuditoria sinAutor = new EventoAuditoria(new AuditoriaId("e-2"), AccionAuditada.CUENTA_REGISTRADA,
                null, null, new UsuarioId("u-1"), "ana@ejemplo.org", "Auto-registro", "sistema", T0);

        adaptador.registrar(sinAutor);

        EventoAuditoria leido = adaptador.listar(0, 10).contenido().get(0);
        assertThat(leido.autorId()).isNull();
        assertThat(leido.autorCorreo()).isNull();
        assertThat(leido).isEqualTo(sinAutor);
    }

    /** `venceEn` es la fecha en que Mongo borra el evento (TTL); sin ella el evento se conserva. */
    @Test
    void debeConservarLaFechaDeVencimientoSiLaTrae() {
        EventoAuditoria conVencimiento = new EventoAuditoria(new AuditoriaId("e-3"), AccionAuditada.BARRIO_VERIFICADO,
                new UsuarioId("v-1"), "vecina@ejemplo.org", new UsuarioId("v-1"), "vecina@ejemplo.org", "Detalle",
                "190.20.30.0/24", T0, T0.plusSeconds(86_400));

        adaptador.registrar(conVencimiento);

        assertThat(adaptador.listar(0, 10).contenido().get(0)).isEqualTo(conVencimiento);
        assertThat(adaptador.listar(0, 10).contenido().get(0).venceEn()).isEqualTo(T0.plusSeconds(86_400));
    }

    @Test
    void debeListarLoMasRecientePrimero() {
        adaptador.registrar(evento("e-viejo", AccionAuditada.CUENTA_REGISTRADA, T0));
        adaptador.registrar(evento("e-nuevo", AccionAuditada.CUENTA_APROBADA, T0.plusSeconds(600)));
        adaptador.registrar(evento("e-medio", AccionAuditada.CORREO_VERIFICADO, T0.plusSeconds(60)));

        assertThat(adaptador.listar(0, 10).contenido())
                .extracting(e -> e.id().valor())
                .containsExactly("e-nuevo", "e-medio", "e-viejo");
    }

    @Test
    void debePaginarConElTotalDeEventos() {
        for (int i = 0; i < 5; i++) {
            adaptador.registrar(evento("e-" + i, AccionAuditada.SESION_INICIADA, T0.plusSeconds(i)));
        }

        Pagina<EventoAuditoria> segunda = adaptador.listar(1, 2);

        assertThat(segunda.contenido()).extracting(e -> e.id().valor()).containsExactly("e-2", "e-1");
        assertThat(segunda.pagina()).isEqualTo(1);
        assertThat(segunda.tamano()).isEqualTo(2);
        assertThat(segunda.totalElementos()).isEqualTo(5);
    }

    @Test
    void sinEventosDebeDevolverUnaPaginaVacia() {
        Pagina<EventoAuditoria> pagina = adaptador.listar(0, 10);

        assertThat(pagina.contenido()).isEmpty();
        assertThat(pagina.totalElementos()).isZero();
    }
}
