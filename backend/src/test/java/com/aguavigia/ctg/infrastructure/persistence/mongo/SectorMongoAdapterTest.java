package com.aguavigia.ctg.infrastructure.persistence.mongo;

import com.aguavigia.ctg.domain.Coordenada;
import com.aguavigia.ctg.domain.EstadoServicio;
import com.aguavigia.ctg.domain.Sector;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.port.out.RelojPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba de integracion del adaptador contra un MongoDB real.
 * Se llama *Test y no *IT a proposito: el pom no configura failsafe, asi que un *IT no lo
 * ejecutaria nadie ni en local ni en el CI. Requiere Docker.
 */
@Testcontainers
@DataMongoTest
@Import({SectorMongoAdapter.class, SectorMongoAdapterTest.RelojFijo.class, SectorMongoAdapterTest.CacheDePrueba.class})
class SectorMongoAdapterTest {

    private static final Instant INSTANTE_FIJO = Instant.parse("2026-08-08T15:30:00Z");

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7.0");

    static class RelojFijo {
        @Bean
        RelojPort reloj() {
            return () -> INSTANTE_FIJO;
        }
    }

    /** Sin Redis en esta prueba de slice — solo hace falta un CacheManager para satisfacer el constructor. */
    static class CacheDePrueba {
        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("sectores");
        }
    }

    @Autowired
    private SectorMongoAdapter adaptador;

    @Autowired
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void limpiar() {
        mongoTemplate.getDb().getCollection("sectores").drop();
    }

    /** Documento tal como lo deja scripts/sembrar-sectores.mjs: sin estadoActual. */
    private void sembrar(String slug, String nombre, Integer poblacion) {
        org.bson.Document documento = new org.bson.Document()
                .append("slug", slug)
                .append("nombre", nombre)
                .append("poblacion", poblacion)
                .append("geometry", new org.bson.Document("type", "Polygon")
                        .append("coordinates", List.of(List.of(
                                List.of(-75.55, 10.40), List.of(-75.54, 10.40),
                                List.of(-75.54, 10.41), List.of(-75.55, 10.40)))));
        mongoTemplate.getDb().getCollection("sectores").insertOne(documento);
    }

    @Test
    void debeListarLosSectoresOrdenadosPorNombre() {
        sembrar("manga", "MANGA", 5000);
        sembrar("bocagrande", "BOCAGRANDE", 12000);

        List<Sector> sectores = adaptador.listarTodos();

        assertThat(sectores).extracting(s -> s.nombre()).containsExactly("BOCAGRANDE", "MANGA");
    }

    @Test
    void debeDevolverEstadoNuloCuandoElSectorNoTieneEstadoRegistrado() {
        sembrar("bocagrande", "BOCAGRANDE", 12000);

        Sector sector = adaptador.buscarPorId(new SectorId("bocagrande")).orElseThrow();

        // ADR-014: sin dato verificado no se afirma CON_SERVICIO.
        assertThat(sector.estadoActual()).isNull();
    }

    @Test
    void debeConservarLaPoblacionNulaDeLosBarriosSinDatoCensal() {
        sembrar("isla-fuerte", "ISLA FUERTE", null);

        Sector sector = adaptador.buscarPorId(new SectorId("isla-fuerte")).orElseThrow();

        assertThat(sector.poblacion()).isNull();
    }

    @Test
    void debeTratarUnEstadoDesconocidoComoAusenciaDeDato() {
        sembrar("manga", "MANGA", 5000);
        mongoTemplate.getDb().getCollection("sectores")
                .updateOne(new org.bson.Document("slug", "manga"),
                        new org.bson.Document("$set", new org.bson.Document("estadoActual", "INUNDADO")));

        Sector sector = adaptador.buscarPorId(new SectorId("manga")).orElseThrow();

        assertThat(sector.estadoActual()).isNull();
    }

    @Test
    void debeSellarLaFechaAlRegistrarUnCambioDeEstado() {
        sembrar("manga", "MANGA", 5000);
        Sector sector = adaptador.buscarPorId(new SectorId("manga")).orElseThrow();

        adaptador.guardar(sector.conEstado(EstadoServicio.SIN_SERVICIO));

        org.bson.Document guardado = mongoTemplate.getDb().getCollection("sectores")
                .find(new org.bson.Document("slug", "manga")).first();
        assertThat(guardado.getString("estadoActual")).isEqualTo("SIN_SERVICIO");
        assertThat(guardado.getDate("estadoActualizadoEn").toInstant()).isEqualTo(INSTANTE_FIJO);
    }

    /** RF003 — sin esto la fecha se escribe en Mongo y se pierde al mapear a dominio, y el mapa
     * nunca puede decir "actualizado hace X". */
    @Test
    void debeDevolverLaFechaDelEstadoAlLeerElSector() {
        sembrar("manga", "MANGA", 5000);
        Sector sector = adaptador.buscarPorId(new SectorId("manga")).orElseThrow();
        adaptador.guardar(sector.conEstado(EstadoServicio.SIN_SERVICIO));

        Sector releido = adaptador.buscarPorId(new SectorId("manga")).orElseThrow();

        assertThat(releido.estadoActual()).isEqualTo(EstadoServicio.SIN_SERVICIO);
        assertThat(releido.estadoActualizadoEn()).isEqualTo(INSTANTE_FIJO);
    }

    @Test
    void unSectorSinEstadoNoDebeTraerFechaDeEstado() {
        sembrar("manga", "MANGA", 5000);

        Sector sector = adaptador.buscarPorId(new SectorId("manga")).orElseThrow();

        assertThat(sector.estadoActual()).isNull();
        assertThat(sector.estadoActualizadoEn()).isNull();
    }

    /** El evento que dispara el correo tiene que llevar ya la fecha nueva: NotificarSuscripcionesService
     * lo recibe sin volver a consultar Mongo. */
    @Test
    void elSectorDevueltoPorGuardarDebeTraerLaFechaRecienSellada() {
        sembrar("manga", "MANGA", 5000);
        Sector sector = adaptador.buscarPorId(new SectorId("manga")).orElseThrow();

        Sector guardado = adaptador.guardar(sector.conEstado(EstadoServicio.SIN_SERVICIO));

        assertThat(guardado.estadoActualizadoEn()).isEqualTo(INSTANTE_FIJO);
    }

    @Test
    void guardarNoDebePerderLaGeometriaSembradaPorD5() {
        sembrar("manga", "MANGA", 5000);
        Sector sector = adaptador.buscarPorId(new SectorId("manga")).orElseThrow();

        adaptador.guardar(sector.conEstado(EstadoServicio.PRESION_BAJA));

        org.bson.Document guardado = mongoTemplate.getDb().getCollection("sectores")
                .find(new org.bson.Document("slug", "manga")).first();
        assertThat(guardado.get("geometry")).isNotNull();
    }

    @Test
    void buscarPorIdDebeDevolverVacioCuandoElSectorNoExiste() {
        assertThat(adaptador.buscarPorId(new SectorId("no-existe"))).isEmpty();
    }

    @Test
    void cambiarEstadoSiEsDebeCambiarCuandoElEstadoEsElEsperado() {
        sembrar("manga", "MANGA", 5000);
        adaptador.guardar(adaptador.buscarPorId(new SectorId("manga")).orElseThrow()
                .conEstado(EstadoServicio.CON_SERVICIO));

        boolean cambio = adaptador.cambiarEstadoSiEs(
                new SectorId("manga"), EstadoServicio.CON_SERVICIO, EstadoServicio.SIN_SERVICIO);

        assertThat(cambio).isTrue();
        Sector leido = adaptador.buscarPorId(new SectorId("manga")).orElseThrow();
        assertThat(leido.estadoActual()).isEqualTo(EstadoServicio.SIN_SERVICIO);
        assertThat(leido.estadoActualizadoEn()).isEqualTo(INSTANTE_FIJO);
    }

    @Test
    void cambiarEstadoSiEsNoDebeCambiarSiElEstadoYaNoEsElEsperado() {
        sembrar("manga", "MANGA", 5000);
        adaptador.guardar(adaptador.buscarPorId(new SectorId("manga")).orElseThrow()
                .conEstado(EstadoServicio.PRESION_BAJA));

        boolean cambio = adaptador.cambiarEstadoSiEs(
                new SectorId("manga"), EstadoServicio.CON_SERVICIO, EstadoServicio.SIN_SERVICIO);

        assertThat(cambio).isFalse();
        assertThat(adaptador.buscarPorId(new SectorId("manga")).orElseThrow().estadoActual())
                .isEqualTo(EstadoServicio.PRESION_BAJA);
    }

    /** Un sector recién sembrado no tiene `estadoActual`: «esperado nulo» debe casar con eso. */
    @Test
    void cambiarEstadoSiEsDebeAceptarUnEsperadoNuloParaUnSectorSinEstado() {
        sembrar("manga", "MANGA", 5000);

        boolean cambio = adaptador.cambiarEstadoSiEs(new SectorId("manga"), null, EstadoServicio.SIN_SERVICIO);

        assertThat(cambio).isTrue();
    }

    /** La carrera real: N peticiones leen el mismo estado y las N intentan cambiarlo. Solo una debe ganar. */
    @Test
    void cambiarEstadoSiEsConPeticionesSimultaneasDebeTenerUnUnicoGanador() throws Exception {
        sembrar("manga", "MANGA", 5000);
        adaptador.guardar(adaptador.buscarPorId(new SectorId("manga")).orElseThrow()
                .conEstado(EstadoServicio.CON_SERVICIO));
        int peticiones = 16;
        var listos = new java.util.concurrent.CountDownLatch(peticiones);
        var salida = new java.util.concurrent.CountDownLatch(1);
        var ganadores = new java.util.concurrent.atomic.AtomicInteger();

        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(peticiones)) {
            var tareas = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < peticiones; i++) {
                tareas.add(pool.submit(() -> {
                    listos.countDown();
                    salida.await();
                    if (adaptador.cambiarEstadoSiEs(
                            new SectorId("manga"), EstadoServicio.CON_SERVICIO, EstadoServicio.SIN_SERVICIO)) {
                        ganadores.incrementAndGet();
                    }
                    return null;
                }));
            }
            listos.await();
            salida.countDown();
            for (var tarea : tareas) {
                tarea.get(30, java.util.concurrent.TimeUnit.SECONDS);
            }
        }

        assertThat(ganadores.get()).isEqualTo(1);
    }

    private static final Instant AYER = INSTANTE_FIJO.minusSeconds(86_400);

    /** Sector con un estado registrado ayer, escrito directo en Mongo para no depender del reloj fijo. */
    private void sembrarConEstadoDeAyer(String slug, EstadoServicio estado) {
        sembrar(slug, slug.toUpperCase(), 5000);
        mongoTemplate.getDb().getCollection("sectores").updateOne(new org.bson.Document("slug", slug),
                new org.bson.Document("$set", new org.bson.Document("estadoActual", estado.name())
                        .append("estadoActualizadoEn", java.util.Date.from(AYER))));
    }

    @Test
    void cambiarElEstadoDebeContarTambienComoVerificacion() {
        sembrar("manga", "MANGA", 5000);

        adaptador.guardar(adaptador.buscarPorId(new SectorId("manga")).orElseThrow()
                .conEstado(EstadoServicio.SIN_SERVICIO));

        assertThat(adaptador.buscarPorId(new SectorId("manga")).orElseThrow().estadoVerificadoEn())
                .isEqualTo(INSTANTE_FIJO);
    }

    /** Documentos escritos antes de ADR-073: sin el campo, la verificación conocida es el cambio. */
    @Test
    void unDocumentoSinVerificacionDebeLeerseConLaFechaDelCambio() {
        sembrarConEstadoDeAyer("manga", EstadoServicio.CON_SERVICIO);

        Sector sector = adaptador.buscarPorId(new SectorId("manga")).orElseThrow();

        assertThat(sector.estadoVerificadoEn()).isEqualTo(AYER);
    }

    @Test
    void confirmarElEstadoVigenteDebeRenovarLaVerificacionSinTocarElCambio() {
        sembrarConEstadoDeAyer("manga", EstadoServicio.CON_SERVICIO);

        boolean marcado = adaptador.confirmarEstado(new SectorId("manga"), EstadoServicio.CON_SERVICIO);

        assertThat(marcado).isTrue();
        Sector sector = adaptador.buscarPorId(new SectorId("manga")).orElseThrow();
        assertThat(sector.estadoActualizadoEn()).isEqualTo(AYER);
        assertThat(sector.estadoVerificadoEn()).isEqualTo(INSTANTE_FIJO);
    }

    /** Otro proceso cambió el estado entre la lectura y la confirmación: no se verifica un estado que ya no rige. */
    @Test
    void confirmarUnEstadoQueYaNoRigeNoDebeMarcarNada() {
        sembrarConEstadoDeAyer("manga", EstadoServicio.SIN_SERVICIO);

        boolean marcado = adaptador.confirmarEstado(new SectorId("manga"), EstadoServicio.CON_SERVICIO);

        assertThat(marcado).isFalse();
        assertThat(adaptador.buscarPorId(new SectorId("manga")).orElseThrow().estadoVerificadoEn()).isEqualTo(AYER);
    }

    @Test
    void confirmarEnUnSectorSinEstadoNoDebeMarcarNada() {
        sembrar("manga", "MANGA", 5000);

        assertThat(adaptador.confirmarEstado(new SectorId("manga"), EstadoServicio.CON_SERVICIO)).isFalse();
        assertThat(adaptador.buscarPorId(new SectorId("manga")).orElseThrow().estadoVerificadoEn()).isNull();
    }

    @Test
    void buscarPorCoordenadaDebeDevolverElSectorCuyoPoligonoLaContiene() {
        sembrar("manga", "MANGA", 5000);

        var encontrado = adaptador.buscarPorCoordenada(new Coordenada(10.405, -75.541));

        assertThat(encontrado).isPresent();
        assertThat(encontrado.get().id()).isEqualTo(new SectorId("manga"));
    }

    @Test
    void buscarPorCoordenadaDebeDevolverVacioCuandoCaeFueraDeTodoSector() {
        sembrar("manga", "MANGA", 5000);

        assertThat(adaptador.buscarPorCoordenada(new Coordenada(10.50, -75.60))).isEmpty();
    }

    /** `zona-industrial` es el único MultiPolygon de los 211 (ver SectorDocumento). */
    @Test
    void buscarPorCoordenadaDebeFuncionarConUnMultiPolygon() {
        mongoTemplate.getDb().getCollection("sectores").insertOne(new org.bson.Document()
                .append("slug", "la-boquilla")
                .append("nombre", "LA BOQUILLA")
                .append("geometry", new org.bson.Document("type", "MultiPolygon")
                        .append("coordinates", List.of(List.of(List.of(
                                List.of(-75.49, 10.45), List.of(-75.48, 10.45),
                                List.of(-75.48, 10.46), List.of(-75.49, 10.46),
                                List.of(-75.49, 10.45))))))) ;

        var encontrado = adaptador.buscarPorCoordenada(new Coordenada(10.455, -75.485));

        assertThat(encontrado).isPresent();
        assertThat(encontrado.get().id()).isEqualTo(new SectorId("la-boquilla"));
    }
}
