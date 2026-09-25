package com.aguavigia.ctg.infrastructure.persistence.mongo;

import com.aguavigia.ctg.domain.EstadoServicio;
import com.aguavigia.ctg.domain.EventoBitacora;
import com.aguavigia.ctg.domain.EventoId;
import com.aguavigia.ctg.domain.FiltroBitacora;
import com.aguavigia.ctg.domain.ReporteId;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.TipoEvento;
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

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataMongoTest
@Import(EventoBitacoraMongoAdapter.class)
class EventoBitacoraMongoAdapterTest {

    private static final Instant AHORA = Instant.parse("2026-08-08T15:30:00Z");

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7.0");

    @Autowired
    private EventoBitacoraMongoAdapter adaptador;

    @Autowired
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void limpiar() {
        mongoTemplate.getDb().getCollection("eventos_bitacora").drop();
    }

    @Test
    void debeGuardarUnEventoDeConsensoCiudadano() {
        EventoBitacora evento = new EventoBitacora(
                new EventoId("e1"), TipoEvento.CORTE_CONFIRMADO_POR_CIUDADANOS,
                new SectorId("bocagrande"), null, AHORA, "3 reportes confirmaron SIN_SERVICIO");

        adaptador.guardar(evento);

        org.bson.Document guardado = mongoTemplate.getDb().getCollection("eventos_bitacora")
                .find(new org.bson.Document("_id", "e1")).first();
        assertThat(guardado.getString("tipo")).isEqualTo("CORTE_CONFIRMADO_POR_CIUDADANOS");
        assertThat(guardado.getString("sectorId")).isEqualTo("bocagrande");
        assertThat(guardado.get("corteId")).isNull();
    }

    @Test
    void debeGuardarYRecuperarLosReportesQueSustentanElCambio() {
        adaptador.guardar(new EventoBitacora(new EventoId("e1"), TipoEvento.CORTE_CONFIRMADO_POR_CIUDADANOS,
                new SectorId("bocagrande"), null, AHORA, "3 reportes confirmaron SIN_SERVICIO",
                com.aguavigia.ctg.domain.EstadoServicio.SIN_SERVICIO, null, null,
                List.of(new com.aguavigia.ctg.domain.ReporteId("r1"), new com.aguavigia.ctg.domain.ReporteId("r2"))));

        EventoBitacora leido = adaptador.listar(FiltroBitacora.sinFiltro(), 0, 50).contenido().get(0);

        assertThat(leido.reportesSustento()).extracting(r -> r.valor()).containsExactly("r1", "r2");
    }

    /** Los eventos guardados antes de RF011 no traen el campo: deben leerse como sin sustento, no fallar. */
    @Test
    void unEventoAnteriorAlCampoDebeLeerseSinReportesDeSustento() {
        mongoTemplate.getDb().getCollection("eventos_bitacora").insertOne(new org.bson.Document()
                .append("_id", "viejo").append("tipo", "CORTE_ANUNCIADO").append("sectorId", "manga")
                .append("timestamp", java.util.Date.from(AHORA)).append("descripcion", "anuncio previo"));

        EventoBitacora leido = adaptador.listar(FiltroBitacora.sinFiltro(), 0, 50).contenido().get(0);

        assertThat(leido.reportesSustento()).isEmpty();
    }

    @Test
    void debeListarLosEventosDelMasRecienteAlMasViejo() {
        adaptador.guardar(new EventoBitacora(new EventoId("e1"), TipoEvento.CORTE_CONFIRMADO_POR_CIUDADANOS,
                new SectorId("bocagrande"), null, AHORA, "primero"));
        adaptador.guardar(new EventoBitacora(new EventoId("e2"), TipoEvento.CORTE_RESTABLECIDO,
                new SectorId("bocagrande"), null, AHORA.plusSeconds(60), "segundo"));

        List<EventoBitacora> eventos = adaptador.listar(FiltroBitacora.sinFiltro(), 0, 50).contenido();

        assertThat(eventos).extracting(e -> e.id().valor()).containsExactly("e2", "e1");
    }
    @Test
    void debeBuscarUnEventoPorIdConSusReportesDeSustento() {
        adaptador.guardar(new EventoBitacora(
                new EventoId("e-sustento"), TipoEvento.CORTE_CONFIRMADO_POR_CIUDADANOS,
                new SectorId("bocagrande"), null, AHORA, "consenso", EstadoServicio.SIN_SERVICIO, null, null,
                List.of(new ReporteId("r1"), new ReporteId("r2"))));

        assertThat(adaptador.buscarPorId(new EventoId("e-sustento")))
                .get().extracting(e -> e.reportesSustento().size()).isEqualTo(2);
        assertThat(adaptador.buscarPorId(new EventoId("no-existe"))).isEmpty();
    }

    private void sembrarTresBarriosYDosTipos() {
        adaptador.guardar(new EventoBitacora(new EventoId("m1"), TipoEvento.CORTE_ANUNCIADO,
                new SectorId("manga"), null, AHORA, "anuncio en manga"));
        adaptador.guardar(new EventoBitacora(new EventoId("m2"), TipoEvento.CORTE_RESTABLECIDO,
                new SectorId("manga"), null, AHORA.plusSeconds(3_600), "restablecido en manga"));
        adaptador.guardar(new EventoBitacora(new EventoId("b1"), TipoEvento.CORTE_ANUNCIADO,
                new SectorId("bocagrande"), null, AHORA.plusSeconds(7_200), "anuncio en bocagrande"));
    }

    /** El filtro se resuelve en Mongo: el total es el del barrio, no el de la página cargada. */
    @Test
    void debeFiltrarPorBarrioYContarSoloLosDelBarrio() {
        sembrarTresBarriosYDosTipos();

        var pagina = adaptador.listar(new FiltroBitacora(new SectorId("manga"), null, null, null), 0, 1);

        assertThat(pagina.contenido()).extracting(e -> e.id().valor()).containsExactly("m2");
        assertThat(pagina.totalElementos()).isEqualTo(2);
    }

    @Test
    void debeFiltrarPorTipo() {
        sembrarTresBarriosYDosTipos();

        var eventos = adaptador.listar(
                new FiltroBitacora(null, TipoEvento.CORTE_ANUNCIADO, null, null), 0, 50).contenido();

        assertThat(eventos).extracting(e -> e.id().valor()).containsExactly("b1", "m1");
    }

    @Test
    void elRangoDebeIncluirDesdeYExcluirHasta() {
        sembrarTresBarriosYDosTipos();

        var eventos = adaptador.listar(
                new FiltroBitacora(null, null, AHORA, AHORA.plusSeconds(7_200)), 0, 50).contenido();

        assertThat(eventos).extracting(e -> e.id().valor()).containsExactly("m2", "m1");
    }

    @Test
    void debeCombinarBarrioTipoYRango() {
        sembrarTresBarriosYDosTipos();

        var eventos = adaptador.listar(new FiltroBitacora(new SectorId("manga"), TipoEvento.CORTE_RESTABLECIDO,
                AHORA.plusSeconds(1), null), 0, 50).contenido();

        assertThat(eventos).extracting(e -> e.id().valor()).containsExactly("m2");
    }

    @Test
    void unFiltroSinCoincidenciasDebeDarUnaPaginaVacia() {
        sembrarTresBarriosYDosTipos();

        var pagina = adaptador.listar(new FiltroBitacora(new SectorId("no-existe"), null, null, null), 0, 50);

        assertThat(pagina.contenido()).isEmpty();
        assertThat(pagina.totalElementos()).isZero();
    }
}
