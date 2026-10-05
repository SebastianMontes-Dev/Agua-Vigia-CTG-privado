package com.aguavigia.ctg.infrastructure.persistence.mongo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spring Data no crea índices solos (creación automática desactivada desde 3.0) — esta prueba
 * confirma que IndicesMongo.asegurarIndices() realmente los deja en Mongo, no solo que el método
 * no lance una excepción.
 */
@Testcontainers
@DataMongoTest
@Import(IndicesMongo.class)
class IndicesMongoTest {

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7.0");

    @Autowired
    private IndicesMongo indicesMongo;

    @Autowired
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void partirDeUnaColeccionSinIndices() {
        mongoTemplate.getDb().getCollection("reportes").drop();
        mongoTemplate.getDb().getCollection("dispositivos").drop();
    }

    private static Set<String> nombresDeIndices(List<IndexInfo> indices) {
        return indices.stream().map(IndexInfo::getName).collect(Collectors.toSet());
    }

    /** Las agregaciones del Indice de Cumplimiento filtran cortes cerrados (`finReal` no nulo). */
    @Test
    void debeAsegurarElIndiceDeFinRealDeLosCortes() {
        indicesMongo.asegurarIndices();

        Set<String> indicesCortes = nombresDeIndices(mongoTemplate.indexOps(CorteAguaDocumento.class).getIndexInfo());
        assertThat(indicesCortes).contains("sectoresAfectados_1", "finReal_1");
    }

    @Test
    void debeAsegurarLosIndicesDeReportesSuscripcionesYBitacora() {
        indicesMongo.asegurarIndices();

        Set<String> indicesReportes = nombresDeIndices(mongoTemplate.indexOps(ReporteCiudadanoDocumento.class).getIndexInfo());
        assertThat(indicesReportes).contains("sectorId_1_timestamp_-1", "sectorId_1_huella_1_timestamp_-1", "estadoModeracion_1_timestamp_1");

        Set<String> indicesSuscripciones = nombresDeIndices(mongoTemplate.indexOps(SuscripcionDocumento.class).getIndexInfo());
        assertThat(indicesSuscripciones).contains("tokenConfirmacion_1", "sectorIds_1");

        Set<String> indicesBitacora = nombresDeIndices(mongoTemplate.indexOps(EventoBitacoraDocumento.class).getIndexInfo());
        assertThat(indicesBitacora).contains("timestamp_-1", "sectorId_1_timestamp_-1", "tipo_1_timestamp_-1");
    }
    /**
     * La cola de moderación pide PENDIENTE (o sin campo) ordenada por antigüedad: con `estadoModeracion+timestamp`
     * Mongo lee solo la página; con el índice de un solo campo examinaba TODOS los pendientes y ordenaba en
     * memoria (84 000 reportes: 87 ms y creciendo). El de un solo campo queda como prefijo del compuesto.
     */
    @Test
    void noDebeDejarElIndiceDeEstadoModeracionSoloPorSerPrefijoDelCompuesto() {
        mongoTemplate.getCollection("reportes").createIndex(new org.bson.Document("estadoModeracion", 1));

        indicesMongo.asegurarIndices();

        Set<String> indicesReportes = nombresDeIndices(mongoTemplate.indexOps(ReporteCiudadanoDocumento.class).getIndexInfo());
        assertThat(indicesReportes).doesNotContain("estadoModeracion_1").contains("estadoModeracion_1_timestamp_1");
    }

    /** ADR-081: el listado del panel filtra por barrio y ordena por creadoEn descendente. */
    @Test
    void debeAsegurarElIndiceDeBarrioYFechaDeLasCuentas() {
        indicesMongo.asegurarIndices();

        Set<String> indicesUsuarios = nombresDeIndices(mongoTemplate.indexOps(UsuarioDocumento.class).getIndexInfo());
        assertThat(indicesUsuarios).contains("barrio_1_creadoEn_-1");
    }

    @Test
    void asegurarIndicesDebeSerIdempotente() {
        indicesMongo.asegurarIndices();
        indicesMongo.asegurarIndices();

        Set<String> indicesReportes = nombresDeIndices(mongoTemplate.indexOps(ReporteCiudadanoDocumento.class).getIndexInfo());
        assertThat(indicesReportes).contains("estadoModeracion_1_timestamp_1");
    }
    /**
     * Retención de reportes (decisión del dueño, 2026-09-21): 12 meses. Mongo los borra solo con un índice TTL
     * sobre `timestamp`. Los eventos de la bitácora son permanentes y conservan los ids de sus reportes de
     * sustento, que pasado el año apuntan a reportes que ya no existen.
     */
    @Test
    void debeExpirarLosReportesAlCabodeUnAnoPorDefecto() {
        indicesMongo.asegurarIndices();

        var ttl = mongoTemplate.indexOps(ReporteCiudadanoDocumento.class).getIndexInfo().stream()
                .filter(indice -> "timestamp_1".equals(indice.getName())).findFirst();
        assertThat(ttl).isPresent();
        assertThat(ttl.get().getExpireAfter()).contains(java.time.Duration.ofDays(365));
    }

    @Test
    void conRetencionCeroLosReportesNoDebenExpirar() {
        new IndicesMongo(mongoTemplate, 0, 365).asegurarIndices();

        Set<String> indicesReportes = nombresDeIndices(mongoTemplate.indexOps(ReporteCiudadanoDocumento.class).getIndexInfo());
        assertThat(indicesReportes).doesNotContain("timestamp_1");
    }

    /**
     * Un dispositivo no tiene dueño que lo reclame ni utilidad una vez inactivo: Mongo lo borra solo 12 meses
     * después de su último uso (D19), contados desde `ultimoVisto` y no desde su creación.
     */
    @Test
    void debeExpirarLosDispositivosAlAnoDeSuUltimoUsoPorDefecto() {
        indicesMongo.asegurarIndices();

        var ttl = mongoTemplate.indexOps(DispositivoDocumento.class).getIndexInfo().stream()
                .filter(indice -> "ultimoVisto_1".equals(indice.getName())).findFirst();
        assertThat(ttl).isPresent();
        assertThat(ttl.get().getExpireAfter()).contains(java.time.Duration.ofDays(365));
    }

    /**
     * Solo caducan los eventos que traen `venceEn` (los de la ciudadanía): los del panel no lo llevan y se conservan,
     * porque son la evidencia de quién hizo qué.
     */
    @Test
    void debeExpirarLosEventosDeAuditoriaQueTraenFechaDeVencimiento() {
        indicesMongo.asegurarIndices();

        var ttl = mongoTemplate.indexOps(EventoAuditoriaDocumento.class).getIndexInfo().stream()
                .filter(indice -> "venceEn_1".equals(indice.getName())).findFirst();
        assertThat(ttl).isPresent();
        assertThat(ttl.get().getExpireAfter()).contains(java.time.Duration.ZERO);
    }

    /** Un token de subida que nadie usó no debe quedarse en la base: Mongo lo borra solo al llegar su fecha. */
    @Test
    void debeExpirarLosTokensDeSubidaDeFotoAlLlegarSuFecha() {
        indicesMongo.asegurarIndices();

        var ttl = mongoTemplate.indexOps(SubidaDeFotoDocumento.class).getIndexInfo().stream()
                .filter(indice -> "venceEn_1".equals(indice.getName())).findFirst();
        assertThat(ttl).isPresent();
        assertThat(ttl.get().getExpireAfter()).contains(java.time.Duration.ZERO);
    }

    /** Servir una foto busca el reporte por su URL: sin índice sería un recorrido de toda la colección. */
    @Test
    void debeAsegurarElIndiceDeLaUrlDeLaFotoDeLosReportes() {
        indicesMongo.asegurarIndices();

        Set<String> indicesReportes = nombresDeIndices(mongoTemplate.indexOps(ReporteCiudadanoDocumento.class).getIndexInfo());
        assertThat(indicesReportes).contains("fotoUrl_1");
    }

    @Test
    void conRetencionDeDispositivosCeroNoDebenExpirar() {
        new IndicesMongo(mongoTemplate, 365, 0).asegurarIndices();

        Set<String> indicesDispositivos = nombresDeIndices(mongoTemplate.indexOps(DispositivoDocumento.class).getIndexInfo());
        assertThat(indicesDispositivos).doesNotContain("ultimoVisto_1");
    }
}
