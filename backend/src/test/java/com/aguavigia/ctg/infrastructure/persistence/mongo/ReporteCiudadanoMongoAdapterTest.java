package com.aguavigia.ctg.infrastructure.persistence.mongo;

import com.aguavigia.ctg.domain.Coordenada;
import com.aguavigia.ctg.domain.EstadoModeracion;
import com.aguavigia.ctg.domain.EvidenciaVencida;
import com.aguavigia.ctg.domain.HuellaDispositivo;
import com.aguavigia.ctg.domain.ReporteCiudadano;
import com.aguavigia.ctg.domain.ReporteId;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.TipoReporte;
import com.aguavigia.ctg.domain.port.out.RelojPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataMongoTest
@Import({ReporteCiudadanoMongoAdapter.class, ReporteCiudadanoMongoAdapterTest.RelojFijo.class})
class ReporteCiudadanoMongoAdapterTest {

    private static final Instant AHORA = Instant.parse("2026-08-08T15:30:00Z");

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7.0");

    static class RelojFijo {
        @Bean
        RelojPort reloj() {
            return () -> AHORA;
        }
    }

    @Autowired
    private ReporteCiudadanoMongoAdapter adaptador;

    @Autowired
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void limpiar() {
        mongoTemplate.getDb().getCollection("reportes").drop();
    }

    @Test
    void debeGuardarUnReporteConCoordenada() {
        ReporteCiudadano reporte = new ReporteCiudadano(
                new ReporteId("r1"), new SectorId("bocagrande"), TipoReporte.SIN_AGUA,
                new Coordenada(10.39, -75.48), new HuellaDispositivo("hash-1"), AHORA);

        adaptador.guardar(reporte);

        org.bson.Document guardado = mongoTemplate.getDb().getCollection("reportes")
                .find(new org.bson.Document("_id", "r1")).first();
        assertThat(guardado.getString("sectorId")).isEqualTo("bocagrande");
        assertThat(guardado.getString("tipo")).isEqualTo("SIN_AGUA");
        assertThat(guardado.getDouble("latitud")).isEqualTo(10.39);
    }

    /** Sin esto el quórum no podría saber, tras leer los reportes de la ventana, cuáles venían verificados ni de qué red. */
    @Test
    void debeConservarLaVerificacionYLaRedDelReporte() {
        adaptador.guardar(new ReporteCiudadano(new ReporteId("r-v"), new SectorId("manga"), TipoReporte.SIN_AGUA, null,
                new HuellaDispositivo("hash-v"), AHORA)
                .conIdentidad(com.aguavigia.ctg.domain.NivelDeVerificacion.UBICACION_VERIFICADA, "red-a"));

        ReporteCiudadano leido = adaptador.buscarPorId(new ReporteId("r-v")).orElseThrow();

        assertThat(leido.verificacion()).isEqualTo(com.aguavigia.ctg.domain.NivelDeVerificacion.UBICACION_VERIFICADA);
        assertThat(leido.redHash()).isEqualTo("red-a");
    }

    /** Los reportes guardados antes de D16 no traen estos campos: se leen sin verificación y sin red conocida. */
    @Test
    void unReporteAnteriorALaVerificacionDebeLeerseSinVerificacionNiRed() {
        mongoTemplate.getDb().getCollection("reportes").insertOne(new org.bson.Document()
                .append("_id", "r-viejo").append("sectorId", "manga").append("tipo", "SIN_AGUA")
                .append("huella", "hash-viejo").append("timestamp", java.util.Date.from(AHORA)));

        ReporteCiudadano leido = adaptador.buscarPorId(new ReporteId("r-viejo")).orElseThrow();

        assertThat(leido.verificacion()).isEqualTo(com.aguavigia.ctg.domain.NivelDeVerificacion.NINGUNA);
        assertThat(leido.redHash()).isNull();
    }

    /** El origen sensor se persiste tal cual: sin él, un recálculo posterior no sabría de quién es el voto. */
    @Test
    void debeConservarSiElReporteEsDeSensorYLosAnterioresAlDatoSonCiudadanos() {
        adaptador.guardar(new ReporteCiudadano(new ReporteId("s1"), new SectorId("bocagrande"), TipoReporte.PRESION_BAJA,
                null, HuellaDispositivo.deSensor("sensor-1"), AHORA).comoDeSensor());
        mongoTemplate.getDb().getCollection("reportes").insertOne(new org.bson.Document()
                .append("_id", "viejo").append("sectorId", "bocagrande").append("tipo", "SIN_AGUA")
                .append("huella", "h").append("timestamp", java.util.Date.from(AHORA)));

        assertThat(adaptador.buscarPorId(new ReporteId("s1")).orElseThrow().esSensor()).isTrue();
        assertThat(adaptador.buscarPorId(new ReporteId("viejo")).orElseThrow().esSensor()).isFalse();
    }

    @Test
    void asignarFotoSoloSiNoTieneYSinPisarLoDemas() {
        ReporteId id = new ReporteId("r-foto");
        adaptador.guardar(new ReporteCiudadano(id, new SectorId("manga"), TipoReporte.SIN_AGUA, null,
                new HuellaDispositivo("h"), AHORA));
        // Una aprobación que llega «en medio»: asignar la foto no puede devolver el reporte a PENDIENTE.
        adaptador.guardar(adaptador.buscarPorId(id).orElseThrow().aprobar());

        assertThat(adaptador.asignarFotoSiNoTiene(id, "/api/fotos/a.jpg", "s".repeat(64))).isTrue();
        assertThat(adaptador.asignarFotoSiNoTiene(id, "/api/fotos/b.jpg", "t".repeat(64))).isFalse();

        ReporteCiudadano leido = adaptador.buscarPorId(id).orElseThrow();
        assertThat(leido.fotoUrl()).isEqualTo("/api/fotos/a.jpg");
        assertThat(leido.fotoSha256()).isEqualTo("s".repeat(64));
        assertThat(leido.estadoModeracion()).isEqualTo(com.aguavigia.ctg.domain.EstadoModeracion.APROBADO);
        assertThat(adaptador.asignarFotoSiNoTiene(new ReporteId("no-existe"), "/x", "y")).isFalse();
    }

    /** Antes se leía el reporte entero y se guardaba entero: una confirmación podía revivir un reporte ya descartado. */
    @Test
    void agregarConfirmacionNoReviveUnReporteDescartadoNiPisaOtrasConfirmaciones() {
        ReporteId id = new ReporteId("r-confirmar");
        adaptador.guardar(new ReporteCiudadano(id, new SectorId("manga"), TipoReporte.SIN_AGUA, null,
                new HuellaDispositivo("autor"), AHORA));

        assertThat(adaptador.agregarConfirmacionSiVigente(id, new HuellaDispositivo("v1"))).isTrue();
        assertThat(adaptador.agregarConfirmacionSiVigente(id, new HuellaDispositivo("v1"))).as("repetida").isTrue();
        assertThat(adaptador.agregarConfirmacionSiVigente(id, new HuellaDispositivo("v2"))).isTrue();
        assertThat(adaptador.buscarPorId(id).orElseThrow().numeroConfirmaciones()).isEqualTo(2);

        assertThat(adaptador.cambiarEstadoDeModeracion(id, EstadoModeracion.DESCARTADO)).isTrue();

        assertThat(adaptador.agregarConfirmacionSiVigente(id, new HuellaDispositivo("v3"))).isFalse();
        ReporteCiudadano leido = adaptador.buscarPorId(id).orElseThrow();
        assertThat(leido.estadoModeracion()).isEqualTo(EstadoModeracion.DESCARTADO);
        assertThat(leido.numeroConfirmaciones()).isEqualTo(2);
        assertThat(adaptador.agregarConfirmacionSiVigente(new ReporteId("no-existe"), new HuellaDispositivo("v"))).isFalse();
    }

    /** La moderación cambia solo la decisión: las confirmaciones que llegaron «en medio» no se pierden. */
    @Test
    void cambiarEstadoDeModeracionNoPisaLasConfirmacionesNiLaFoto() {
        ReporteId id = new ReporteId("r-moderar");
        adaptador.guardar(new ReporteCiudadano(id, new SectorId("manga"), TipoReporte.SIN_AGUA, null,
                new HuellaDispositivo("autor"), AHORA));
        adaptador.agregarConfirmacionSiVigente(id, new HuellaDispositivo("v1"));
        adaptador.asignarFotoSiNoTiene(id, "/api/fotos/a.jpg", "s".repeat(64));

        assertThat(adaptador.cambiarEstadoDeModeracion(id, EstadoModeracion.APROBADO)).isTrue();

        ReporteCiudadano leido = adaptador.buscarPorId(id).orElseThrow();
        assertThat(leido.estadoModeracion()).isEqualTo(EstadoModeracion.APROBADO);
        assertThat(leido.numeroConfirmaciones()).isEqualTo(1);
        assertThat(leido.fotoUrl()).isEqualTo("/api/fotos/a.jpg");
        assertThat(adaptador.cambiarEstadoDeModeracion(new ReporteId("no-existe"), EstadoModeracion.APROBADO)).isFalse();
    }

    @Test
    void marcarFotoDescartadaSoloSiHayFotoYSinTocarElReporte() {
        ReporteId id = new ReporteId("r-descarte");
        adaptador.guardar(new ReporteCiudadano(id, new SectorId("manga"), TipoReporte.SIN_AGUA, null,
                new HuellaDispositivo("h"), AHORA).aprobar());

        assertThat(adaptador.marcarFotoDescartada(id)).as("sin foto").isFalse();

        adaptador.asignarFotoSiNoTiene(id, "/api/fotos/a.jpg", "s".repeat(64));
        assertThat(adaptador.marcarFotoDescartada(id)).isTrue();

        ReporteCiudadano leido = adaptador.buscarPorId(id).orElseThrow();
        assertThat(leido.fotoDescartada()).isTrue();
        assertThat(leido.fotoEsPublica()).isFalse();
        assertThat(leido.estadoModeracion()).isEqualTo(com.aguavigia.ctg.domain.EstadoModeracion.APROBADO);
        assertThat(adaptador.marcarFotoDescartada(new ReporteId("no-existe"))).isFalse();
    }

    @Test
    void debeGuardarUnReporteSinCoordenada() {
        ReporteCiudadano reporte = new ReporteCiudadano(
                new ReporteId("r2"), new SectorId("bocagrande"), TipoReporte.PRESION_BAJA,
                null, new HuellaDispositivo("hash-2"), AHORA);

        adaptador.guardar(reporte);

        org.bson.Document guardado = mongoTemplate.getDb().getCollection("reportes")
                .find(new org.bson.Document("_id", "r2")).first();
        assertThat(guardado.get("latitud")).isNull();
    }

    @Test
    void debeListarSoloLosReportesDentroDeLaVentana() {
        guardarConTimestamp("r3", AHORA.minus(Duration.ofMinutes(10)));
        guardarConTimestamp("r4", AHORA.minus(Duration.ofHours(2)));

        List<ReporteCiudadano> recientes = adaptador.listarRecientesPorSector(
                new SectorId("bocagrande"), Duration.ofMinutes(30));

        assertThat(recientes).extracting(r -> r.id().valor()).containsExactly("r3");
    }

    private void guardarConTimestamp(String id, Instant timestamp) {
        ReporteCiudadanoDocumento documento = new ReporteCiudadanoDocumento();
        documento.setId(id);
        documento.setSectorId("bocagrande");
        documento.setTipo("SIN_AGUA");
        documento.setHuella("hash");
        documento.setTimestamp(timestamp);
        mongoTemplate.save(documento);
    }

    @Test
    void debeNacerPendienteDeModeracion() {
        ReporteCiudadano reporte = new ReporteCiudadano(
                new ReporteId("r5"), new SectorId("bocagrande"), TipoReporte.SIN_AGUA,
                null, new HuellaDispositivo("hash-5"), AHORA);

        adaptador.guardar(reporte);

        ReporteCiudadano recuperado = adaptador.buscarPorId(new ReporteId("r5")).orElseThrow();
        assertThat(recuperado.estadoModeracion()).isEqualTo(EstadoModeracion.PENDIENTE);
    }

    @Test
    void debeTratarUnDocumentoSinEstadoModeracionComoPendiente() {
        // Documento sembrado antes de RF018 (ADR-023): sin el campo, no ya moderado.
        guardarConTimestamp("r6", AHORA);

        ReporteCiudadano recuperado = adaptador.buscarPorId(new ReporteId("r6")).orElseThrow();

        assertThat(recuperado.estadoModeracion()).isEqualTo(EstadoModeracion.PENDIENTE);
    }

    @Test
    void debeListarPendientesIncluyendoLosSinCampoDeModeracion() {
        adaptador.guardar(new ReporteCiudadano(new ReporteId("r7"), new SectorId("bocagrande"),
                TipoReporte.SIN_AGUA, null, new HuellaDispositivo("hash-7"), AHORA));
        guardarConTimestamp("r8", AHORA);
        adaptador.guardar(new ReporteCiudadano(new ReporteId("r9"), new SectorId("bocagrande"),
                TipoReporte.SIN_AGUA, null, new HuellaDispositivo("hash-9"), AHORA).aprobar());

        List<ReporteCiudadano> pendientes = adaptador.listarPendientes(0, 50).contenido();

        assertThat(pendientes).extracting(r -> r.id().valor()).containsExactlyInAnyOrder("r7", "r8");
    }

    @Test
    void noDebeSustentarElConsensoConUnReporteDescartado() {
        adaptador.guardar(new ReporteCiudadano(new ReporteId("r11"), new SectorId("bocagrande"),
                TipoReporte.SIN_AGUA, null, new HuellaDispositivo("hash-11"), AHORA));
        ReporteCiudadano descartado = adaptador.buscarPorId(new ReporteId("r11")).orElseThrow().descartar();
        adaptador.guardar(descartado);
        adaptador.guardar(new ReporteCiudadano(new ReporteId("r12"), new SectorId("bocagrande"),
                TipoReporte.SIN_AGUA, null, new HuellaDispositivo("hash-12"), AHORA));

        List<ReporteCiudadano> recientes = adaptador.listarRecientesPorSector(
                new SectorId("bocagrande"), Duration.ofMinutes(30));

        assertThat(recientes).extracting(r -> r.id().valor()).containsExactly("r12");
    }

    @Test
    void debeContarReportesDescartadosParaElCupoDelDispositivo() {
        HuellaDispositivo huella = new HuellaDispositivo("hash-abusador");
        adaptador.guardar(new ReporteCiudadano(new ReporteId("r13"), new SectorId("bocagrande"),
                TipoReporte.SIN_AGUA, null, huella, AHORA));
        ReporteCiudadano descartado = adaptador.buscarPorId(new ReporteId("r13")).orElseThrow().descartar();
        adaptador.guardar(descartado);

        long conteo = adaptador.contarRecientesPorSectorYDispositivo(
                new SectorId("bocagrande"), Duration.ofMinutes(30), huella);

        assertThat(conteo).isEqualTo(1);
    }

    private void guardarReporte(String id, String sector, String huella, TipoReporte tipo, Instant instante) {
        adaptador.guardar(new ReporteCiudadano(new ReporteId(id), new SectorId(sector), tipo, null,
                new HuellaDispositivo(huella), instante));
    }

    @Test
    void contarVotosRecientes_debeContarCadaDispositivoUnaVezConSuReporteMasReciente() {
        guardarReporte("v1", "bocagrande", "dispositivo-1", TipoReporte.SIN_AGUA, AHORA.minus(Duration.ofMinutes(20)));
        guardarReporte("v2", "bocagrande", "dispositivo-1", TipoReporte.SERVICIO_RESTABLECIDO, AHORA.minus(Duration.ofMinutes(5)));
        guardarReporte("v3", "bocagrande", "dispositivo-2", TipoReporte.SIN_AGUA, AHORA.minus(Duration.ofMinutes(4)));
        guardarReporte("v4", "bocagrande", "dispositivo-3", TipoReporte.SIN_AGUA, AHORA.minus(Duration.ofMinutes(3)));

        Map<TipoReporte, Long> votos = adaptador.contarVotosRecientes(new SectorId("bocagrande"), Duration.ofMinutes(30));

        assertThat(votos).containsExactlyInAnyOrderEntriesOf(
                Map.of(TipoReporte.SIN_AGUA, 2L, TipoReporte.SERVICIO_RESTABLECIDO, 1L));
    }

    @Test
    void contarVotosRecientes_debeExcluirDescartadosLoQueEstaFueraDeLaVentanaYOtrosSectores() {
        guardarReporte("w1", "bocagrande", "dispositivo-1", TipoReporte.SIN_AGUA, AHORA.minus(Duration.ofMinutes(5)));
        guardarReporte("w2", "bocagrande", "dispositivo-2", TipoReporte.SIN_AGUA, AHORA.minus(Duration.ofMinutes(5)));
        adaptador.guardar(adaptador.buscarPorId(new ReporteId("w2")).orElseThrow().descartar());
        guardarReporte("w3", "bocagrande", "dispositivo-3", TipoReporte.SIN_AGUA, AHORA.minus(Duration.ofHours(2)));
        guardarReporte("w4", "manga", "dispositivo-4", TipoReporte.SIN_AGUA, AHORA.minus(Duration.ofMinutes(5)));

        Map<TipoReporte, Long> votos = adaptador.contarVotosRecientes(new SectorId("bocagrande"), Duration.ofMinutes(30));

        assertThat(votos).containsExactly(Map.entry(TipoReporte.SIN_AGUA, 1L));
    }

    @Test
    void contarVotosRecientes_debeDevolverUnMapaVacioSinReportes() {
        assertThat(adaptador.contarVotosRecientes(new SectorId("bocagrande"), Duration.ofMinutes(30))).isEmpty();
    }

    @Test
    void debeSacarUnReporteDeLaColaAlModerarlo() {
        adaptador.guardar(new ReporteCiudadano(new ReporteId("r10"), new SectorId("bocagrande"),
                TipoReporte.SIN_AGUA, null, new HuellaDispositivo("hash-10"), AHORA));

        ReporteCiudadano recuperado = adaptador.buscarPorId(new ReporteId("r10")).orElseThrow();
        adaptador.guardar(recuperado.descartar());

        assertThat(adaptador.listarPendientes(0, 50).contenido()).isEmpty();
        assertThat(adaptador.buscarPorId(new ReporteId("r10")).orElseThrow().estadoModeracion())
                .isEqualTo(EstadoModeracion.DESCARTADO);
    }

    @Test
    void listarNombresDeFotoReferenciados_debeExtraerElNombreDeArchivoDeCadaFotoUrl() {
        adaptador.guardar(new ReporteCiudadano(new ReporteId("r14"), new SectorId("bocagrande"),
                TipoReporte.SIN_AGUA, null, new HuellaDispositivo("hash-14"), AHORA,
                EstadoModeracion.PENDIENTE, "/fotos/abc123.jpg"));
        // Un reporte DESCARTADO sigue siendo dueño legitimo de su foto: no es huerfana.
        adaptador.guardar(new ReporteCiudadano(new ReporteId("r15"), new SectorId("bocagrande"),
                TipoReporte.SIN_AGUA, null, new HuellaDispositivo("hash-15"), AHORA,
                EstadoModeracion.DESCARTADO, "/fotos/def456.png"));
        adaptador.guardar(new ReporteCiudadano(new ReporteId("r16"), new SectorId("bocagrande"),
                TipoReporte.SIN_AGUA, null, new HuellaDispositivo("hash-16"), AHORA));

        assertThat(adaptador.listarNombresDeFotoReferenciados())
                .containsExactlyInAnyOrder("abc123.jpg", "def456.png");
    }

    @Test
    void listarEvidenciaAnteriorA_debeExcluirReportesSinFotoOMasRecientesQueElLimite() {
        adaptador.guardar(new ReporteCiudadano(new ReporteId("r17"), new SectorId("bocagrande"),
                TipoReporte.SIN_AGUA, null, new HuellaDispositivo("hash-17"),
                AHORA.minus(Duration.ofDays(400)), EstadoModeracion.APROBADO, "/fotos/vieja.jpg"));
        adaptador.guardar(new ReporteCiudadano(new ReporteId("r18"), new SectorId("bocagrande"),
                TipoReporte.SIN_AGUA, null, new HuellaDispositivo("hash-18"),
                AHORA.minus(Duration.ofDays(1)), EstadoModeracion.APROBADO, "/fotos/reciente.jpg"));
        adaptador.guardar(new ReporteCiudadano(new ReporteId("r19"), new SectorId("bocagrande"),
                TipoReporte.SIN_AGUA, null, new HuellaDispositivo("hash-19"),
                AHORA.minus(Duration.ofDays(400))));

        List<EvidenciaVencida> vencidos = adaptador.listarEvidenciaAnteriorA(AHORA.minus(Duration.ofDays(365)));

        assertThat(vencidos).extracting(e -> e.id().valor()).containsExactly("r17");
        assertThat(vencidos).extracting(EvidenciaVencida::fotoUrl).containsExactly("/fotos/vieja.jpg");
    }

    @Test
    void quitarFotosDe_debeLimpiarLaFotoUrlSoloDeLosIdsIndicados() {
        adaptador.guardar(new ReporteCiudadano(new ReporteId("r20"), new SectorId("bocagrande"),
                TipoReporte.SIN_AGUA, null, new HuellaDispositivo("hash-20"), AHORA,
                EstadoModeracion.APROBADO, "/fotos/uno.jpg"));
        adaptador.guardar(new ReporteCiudadano(new ReporteId("r21"), new SectorId("bocagrande"),
                TipoReporte.SIN_AGUA, null, new HuellaDispositivo("hash-21"), AHORA,
                EstadoModeracion.APROBADO, "/fotos/dos.jpg"));

        adaptador.quitarFotosDe(List.of(new ReporteId("r20")));

        assertThat(adaptador.buscarPorId(new ReporteId("r20")).orElseThrow().fotoUrl()).isNull();
        assertThat(adaptador.buscarPorId(new ReporteId("r21")).orElseThrow().fotoUrl())
                .isEqualTo("/fotos/dos.jpg");
    }

    private static ReporteCiudadano conFoto(String id, String url) {
        return new ReporteCiudadano(new ReporteId(id), new SectorId("bocagrande"), TipoReporte.SIN_AGUA, null,
                new HuellaDispositivo("hash-" + id), AHORA).aprobar().conFoto(url, "b".repeat(64));
    }

    /** El hash de la foto y su descarte deben volver tal como se guardaron: de ellos depende qué se sirve al público. */
    @Test
    void debeGuardarYLeerElHashYElDescarteDeLaFoto() {
        adaptador.guardar(conFoto("r30", "/api/fotos/uno.jpg").descartarFoto());

        ReporteCiudadano leido = adaptador.buscarPorId(new ReporteId("r30")).orElseThrow();

        assertThat(leido.fotoSha256()).isEqualTo("b".repeat(64));
        assertThat(leido.fotoDescartada()).isTrue();
        assertThat(leido.fotoEsPublica()).isFalse();
    }

    @Test
    void buscarPorNombreDeFotoDebeEncontrarElReporteSeaLaUrlLaViejaOLaNueva() {
        adaptador.guardar(conFoto("r31", "/api/fotos/nueva.jpg"));
        adaptador.guardar(conFoto("r32", "/fotos/vieja.png"));

        assertThat(adaptador.buscarPorNombreDeFoto("nueva.jpg")).map(r -> r.id().valor()).contains("r31");
        assertThat(adaptador.buscarPorNombreDeFoto("vieja.png")).map(r -> r.id().valor()).contains("r32");
    }

    /** Un nombre que solo termina igual no es el mismo archivo: servirlo mostraría la foto de otro reporte. */
    @Test
    void buscarPorNombreDeFotoNoDebeCoincidirConUnNombreQueSoloTerminaIgual() {
        adaptador.guardar(conFoto("r33", "/api/fotos/abc.jpg"));

        assertThat(adaptador.buscarPorNombreDeFoto("c.jpg")).isEmpty();
        assertThat(adaptador.buscarPorNombreDeFoto("..abc.jpg")).isEmpty();
        assertThat(adaptador.buscarPorNombreDeFoto("inexistente.jpg")).isEmpty();
    }

    private static ReporteCiudadano deRed(String id, String sector, String red, Instant cuando) {
        return new ReporteCiudadano(new ReporteId(id), new SectorId(sector), TipoReporte.SIN_AGUA, null,
                new HuellaDispositivo("h-" + id), cuando, EstadoModeracion.PENDIENTE, null, java.util.Set.of(), false,
                com.aguavigia.ctg.domain.NivelDeVerificacion.NINGUNA, red);
    }

    /** D9: la ráfaga es de una red en un barrio dentro de la ventana; el mínimo se alcanza, no se supera. */
    @Test
    void redesEnRafagaDebeDevolverLasRedesQueLlegaronAlMinimoEnElBarrio() {
        Instant reciente = AHORA.minus(Duration.ofMinutes(5));
        for (int i = 0; i < 3; i++) {
            adaptador.guardar(deRed("crespo-a" + i, "crespo", "red-a", reciente));
        }
        adaptador.guardar(deRed("crespo-b", "crespo", "red-b", reciente));
        // La misma red repartida en otro barrio no suma al de Crespo, y por sí sola no llega a 3.
        adaptador.guardar(deRed("manga-a", "manga", "red-a", reciente));

        var rafagas = adaptador.redesEnRafaga(
                List.of(new SectorId("crespo"), new SectorId("manga")), AHORA.minus(Duration.ofMinutes(30)), 3);

        assertThat(rafagas).containsExactly(new com.aguavigia.ctg.domain.RedEnRafaga(new SectorId("crespo"), "red-a"));
    }

    @Test
    void redesEnRafagaIgnoraLoAnteriorALaVentanaLosBarriosNoPedidosYLosReportesSinRed() {
        Instant viejo = AHORA.minus(Duration.ofHours(2));
        for (int i = 0; i < 3; i++) {
            adaptador.guardar(deRed("viejo" + i, "crespo", "red-a", viejo));
            adaptador.guardar(deRed("sin-red" + i, "crespo", null, AHORA.minus(Duration.ofMinutes(1))));
            adaptador.guardar(deRed("otro" + i, "manga", "red-c", AHORA.minus(Duration.ofMinutes(1))));
        }

        assertThat(adaptador.redesEnRafaga(
                List.of(new SectorId("crespo")), AHORA.minus(Duration.ofMinutes(30)), 3)).isEmpty();
    }

    /** Descartar un reporte de la ráfaga no la borra: es la señal de que esa red mandó spam. */
    @Test
    void redesEnRafagaCuentaTambienLoYaModerado() {
        Instant reciente = AHORA.minus(Duration.ofMinutes(5));
        adaptador.guardar(deRed("m1", "crespo", "red-a", reciente));
        adaptador.guardar(deRed("m2", "crespo", "red-a", reciente).descartar());
        adaptador.guardar(deRed("m3", "crespo", "red-a", reciente).aprobar());

        assertThat(adaptador.redesEnRafaga(List.of(new SectorId("crespo")), AHORA.minus(Duration.ofMinutes(30)), 3))
                .hasSize(1);
    }

    /** D29: un voto por identidad y barrio (el más reciente), dentro de la ventana y sin lo descartado. */
    @Test
    void votosRecientesDevuelveElUltimoReporteDeCadaIdentidadEnCadaBarrio() {
        Instant desde = AHORA.minus(Duration.ofMinutes(30));
        adaptador.guardar(unaHuella("v1", "crespo", "h-1", AHORA.minus(Duration.ofMinutes(20))));
        adaptador.guardar(unaHuella("v2", "crespo", "h-1", AHORA.minus(Duration.ofMinutes(5))));
        adaptador.guardar(unaHuella("v3", "manga", "h-1", AHORA.minus(Duration.ofMinutes(10))));
        adaptador.guardar(unaHuella("v4", "crespo", "h-2", AHORA.minus(Duration.ofMinutes(2))).descartar());
        adaptador.guardar(unaHuella("v5", "crespo", "h-3", AHORA.minus(Duration.ofHours(1))));

        var votos = adaptador.votosRecientes(desde);

        assertThat(votos).extracting(v -> v.sector().valor() + "/" + v.huella().hash() + "/" + v.instante())
                .containsExactlyInAnyOrder(
                        "crespo/h-1/" + AHORA.minus(Duration.ofMinutes(5)),
                        "manga/h-1/" + AHORA.minus(Duration.ofMinutes(10)));
    }

    private static ReporteCiudadano unaHuella(String id, String sector, String huella, Instant cuando) {
        return new ReporteCiudadano(new ReporteId(id), new SectorId(sector), TipoReporte.SIN_AGUA, null,
                new HuellaDispositivo(huella), cuando);
    }

    @Test
    void redesEnRafagaSinBarriosNoConsultaNada() {
        assertThat(adaptador.redesEnRafaga(List.of(), AHORA.minus(Duration.ofMinutes(30)), 3)).isEmpty();
    }

    @Test
    void quitarFotosDeDebeLimpiarTambienElHashYElDescarte() {
        adaptador.guardar(conFoto("r34", "/api/fotos/dos.jpg").descartarFoto());

        adaptador.quitarFotosDe(List.of(new ReporteId("r34")));

        ReporteCiudadano leido = adaptador.buscarPorId(new ReporteId("r34")).orElseThrow();
        assertThat(leido.fotoUrl()).isNull();
        assertThat(leido.fotoSha256()).isNull();
        assertThat(leido.fotoDescartada()).isFalse();
    }
}
