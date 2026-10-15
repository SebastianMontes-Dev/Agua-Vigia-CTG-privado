package com.aguavigia.ctg.infrastructure.persistence.sql;

import com.aguavigia.ctg.application.CalcularCumplimientoService;
import com.aguavigia.ctg.application.CopiarAnaliticaService;
import com.aguavigia.ctg.domain.CierreDeCorte;
import com.aguavigia.ctg.domain.CorteAgua;
import com.aguavigia.ctg.domain.CorteId;
import com.aguavigia.ctg.domain.CumplimientoAnalitico;
import com.aguavigia.ctg.domain.EstadoCorte;
import com.aguavigia.ctg.domain.IndiceCumplimiento;
import com.aguavigia.ctg.domain.OrigenCorte;
import com.aguavigia.ctg.domain.OrigenEstado;
import com.aguavigia.ctg.domain.PuntoSerieCumplimiento;
import com.aguavigia.ctg.domain.ResultadoDeCopia;
import com.aguavigia.ctg.domain.Sector;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.port.out.RelojPort;
import com.aguavigia.ctg.domain.port.out.SectorRepository;
import com.aguavigia.ctg.infrastructure.persistence.mongo.CorteAguaDocumento;
import com.aguavigia.ctg.infrastructure.persistence.mongo.CorteAguaMongoAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Con los mismos cortes en MongoDB, la copia a PostgreSQL y su vista tienen que dar lo mismo que la agregación de Mongo
 * (`GET /api/cumplimiento`): mismas duraciones y mismo porcentaje, global, por barrio y por mes. La fórmula del Índice existe dos veces
 * (Java/Mongo y SQL) y esta prueba es lo que impide que se separen (ADR-100).
 */
@Testcontainers
@DataMongoTest
@Import(CorteAguaMongoAdapter.class)
class ParidadMongoPostgresIntegrationTest {

    private static final Instant AHORA = Instant.parse("2026-10-15T15:00:00Z");

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7.0");

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.11-alpine")
            .withDatabaseName("aguavigia_analitica").withUsername("aguavigia");

    @Autowired
    private CorteAguaMongoAdapter cortesMongo;

    @Autowired
    private MongoTemplate mongoTemplate;

    private ConsultaAnaliticaPostgresAdapter consulta;
    private CopiarAnaliticaService copiar;
    private CalcularCumplimientoService indiceJava;
    private List<Sector> barrios;

    @BeforeEach
    void prepararLasDosBases() {
        mongoTemplate.getDb().getCollection("cortes").drop();
        var dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        new JdbcTemplate(dataSource).execute("DROP SCHEMA IF EXISTS analitica CASCADE");

        barrios = List.of(barrio("manga"), barrio("bocagrande"), barrio("crespo"), barrio("getsemani"));
        SectorRepository sectores = mock(SectorRepository.class);
        when(sectores.listarTodos()).thenReturn(barrios);
        RelojPort reloj = () -> AHORA;
        consulta = new ConsultaAnaliticaPostgresAdapter(dataSource);
        copiar = new CopiarAnaliticaService(sectores, cortesMongo, new AlmacenAnaliticoPostgresAdapter(dataSource), reloj);
        indiceJava = new CalcularCumplimientoService(cortesMongo, reloj);
    }

    private static Sector barrio(String id) {
        return new Sector(new SectorId(id), "Barrio " + id, 1000, null);
    }

    private static CorteAgua.Builder corte(String id, EstadoCorte estado, Instant inicio, Duration prometida, String... sectores) {
        return CorteAgua.builder()
                .id(new CorteId(id))
                .sectoresAfectados(Arrays.stream(sectores).map(SectorId::new).toList())
                .inicio(inicio)
                .finPrometido(inicio.plus(prometida))
                .causa("Mantenimiento")
                .origen(OrigenCorte.OFICIAL_ACUACAR)
                .estado(estado);
    }

    private static CierreDeCorte cierre(Instant hora, boolean provisional) {
        return new CierreDeCorte(hora, provisional ? OrigenEstado.VECINOS : OrigenEstado.ACUACAR, provisional);
    }

    /** Una colección con todos los casos que importan: se ejercita cada rama de la agregación de Mongo. */
    private void sembrarCortesVariados() {
        Instant octubre = Instant.parse("2026-10-14T10:00:00Z");
        // Restablecido y cumplido a medias, con un cierre provisional.
        cortesMongo.guardar(corte("c1", EstadoCorte.RESTABLECIDO, octubre, Duration.ofHours(2), "manga", "bocagrande")
                .cierres(Map.of(new SectorId("manga"), cierre(octubre.plus(Duration.ofHours(8)), false),
                        new SectorId("bocagrande"), cierre(octubre.plus(Duration.ofHours(1)), true)))
                .build());
        // Abierto, con un solo barrio ya restablecido (D14).
        cortesMongo.guardar(corte("c2", EstadoCorte.ANUNCIADO, octubre, Duration.ofHours(4), "manga", "crespo")
                .cierres(Map.of(new SectorId("crespo"), cierre(octubre.plus(Duration.ofHours(5)), false)))
                .build());
        // Abierto sin ningún cierre: no cuenta.
        cortesMongo.guardar(corte("c3", EstadoCorte.ANUNCIADO, octubre, Duration.ofHours(4), "getsemani").build());
        // Anulado con cierres: fuera del Índice.
        cortesMongo.guardar(corte("c4", EstadoCorte.ANULADO, octubre, Duration.ofHours(1), "manga")
                .cierres(Map.of(new SectorId("manga"), cierre(octubre.plus(Duration.ofHours(30)), false)))
                .motivoAnulacion("publicado por error")
                .build());
        // Expirado: entra por el barrio que sí se cerró.
        cortesMongo.guardar(corte("c5", EstadoCorte.EXPIRADO, octubre, Duration.ofHours(3), "getsemani", "crespo")
                .cierres(Map.of(new SectorId("getsemani"), cierre(octubre.plus(Duration.ofHours(3)), false)))
                .build());
        // Cierre en el mismo instante del inicio: duración real 0.
        cortesMongo.guardar(corte("c6", EstadoCorte.RESTABLECIDO, octubre, Duration.ofHours(1), "bocagrande")
                .cierres(Map.of(new SectorId("bocagrande"), cierre(octubre, false)))
                .build());
        // Cambio de mes en hora de Cartagena: 02:00Z del 1 de noviembre es octubre allá.
        Instant finDeMes = Instant.parse("2026-10-31T20:00:00Z");
        cortesMongo.guardar(corte("c7", EstadoCorte.RESTABLECIDO, finDeMes, Duration.ofHours(2), "crespo")
                .cierres(Map.of(new SectorId("crespo"), cierre(Instant.parse("2026-11-01T02:00:00Z"), false)))
                .build());
        // Fracciones de segundo.
        Instant fracciones = Instant.parse("2026-11-10T10:00:00.250Z");
        cortesMongo.guardar(corte("c8", EstadoCorte.RESTABLECIDO, fracciones, Duration.ofMillis(7_200_500), "manga")
                .cierres(Map.of(new SectorId("manga"), cierre(fracciones.plusMillis(10_800_700), false)))
                .build());
        // Un documento anterior a los cierres por barrio: solo trae finReal.
        var antiguo = new CorteAguaDocumento();
        antiguo.setId("antiguo");
        antiguo.setSectoresAfectados(List.of("manga", "crespo"));
        antiguo.setInicio(Instant.parse("2026-09-01T08:00:00Z"));
        antiguo.setFinPrometido(Instant.parse("2026-09-01T12:00:00Z"));
        antiguo.setFinReal(Instant.parse("2026-09-01T14:00:00Z"));
        antiguo.setCausa("Histórico");
        antiguo.setOrigen("VEEDOR");
        antiguo.setEstado("RESTABLECIDO");
        mongoTemplate.save(antiguo);
    }

    @Test
    void elIndiceGlobalDeSqlDebeCoincidirConElDeMongo() {
        sembrarCortesVariados();

        assertThat(copiar.copiar()).isEqualTo(ResultadoDeCopia.OK);

        IndiceCumplimiento mongoGlobal = indiceJava.global();
        CumplimientoAnalitico sqlGlobal = consulta.cumplimientoGlobal();
        assertThat(sqlGlobal.paresCerrados()).isEqualTo(cortesMongo.agregarCerrados(null).cantidadCortes()).isPositive();
        assertThat(sqlGlobal.prometidaSegundos()).isEqualTo(mongoGlobal.duracionPrometida().toSeconds());
        assertThat(sqlGlobal.realSegundos()).isEqualTo(mongoGlobal.duracionReal().toSeconds());
        assertThat(sqlGlobal.desviacionSegundos()).isEqualTo(mongoGlobal.desviacion().toSeconds());
        assertThat(sqlGlobal.porcentajeCumplimiento()).isCloseTo(mongoGlobal.porcentajeCumplimiento(), within(1e-9));
    }

    @Test
    void elIndiceDeCadaBarrioDeSqlDebeCoincidirConElDeMongo() {
        sembrarCortesVariados();
        copiar.copiar();

        for (Sector barrio : barrios) {
            SectorId id = barrio.id();
            CumplimientoAnalitico sql = consulta.cumplimientoDe(id.valor()).orElseThrow();
            if (cortesMongo.agregarCerrados(id).cantidadCortes() == 0) {
                assertThat(sql.paresCerrados()).as(id.valor()).isZero();
                continue;
            }
            IndiceCumplimiento enMongo = indiceJava.porSector(id);
            assertThat(sql.paresCerrados()).as(id.valor()).isEqualTo(cortesMongo.agregarCerrados(id).cantidadCortes());
            assertThat(sql.prometidaSegundos()).as(id.valor()).isEqualTo(enMongo.duracionPrometida().toSeconds());
            assertThat(sql.realSegundos()).as(id.valor()).isEqualTo(enMongo.duracionReal().toSeconds());
            assertThat(sql.porcentajeCumplimiento()).as(id.valor()).isCloseTo(enMongo.porcentajeCumplimiento(), within(1e-9));
        }
    }

    @Test
    void laSerieMensualDeSqlDebeCoincidirConLaDeMongo() {
        sembrarCortesVariados();
        copiar.copiar();

        List<PuntoSerieCumplimiento> enMongo = indiceJava.serieMensual(null, null, null);
        var enSql = consulta.cumplimientoMensual();

        assertThat(enSql).hasSameSizeAs(enMongo).hasSizeGreaterThanOrEqualTo(3);
        for (int i = 0; i < enMongo.size(); i++) {
            assertThat(enSql.get(i).mes()).isEqualTo(enMongo.get(i).periodo());
            assertThat(enSql.get(i).paresCerrados()).isEqualTo(enMongo.get(i).cantidadCortes());
            assertThat(enSql.get(i).prometidaSegundos()).isEqualTo(enMongo.get(i).indice().duracionPrometida().toSeconds());
            assertThat(enSql.get(i).realSegundos()).isEqualTo(enMongo.get(i).indice().duracionReal().toSeconds());
            assertThat(enSql.get(i).porcentajeCumplimiento())
                    .isCloseTo(enMongo.get(i).indice().porcentajeCumplimiento(), within(1e-9));
        }
    }

    @Test
    void losConteosDeLasDosBasesDebenCuadrarYQuedarAnotados() {
        sembrarCortesVariados();

        copiar.copiar();

        var copia = consulta.ultimasCopias(1).get(0);
        assertThat(copia.resultado()).isEqualTo(ResultadoDeCopia.OK);
        assertThat(copia.enMongo()).isEqualTo(copia.enSql());
        assertThat(copia.enSql().sectores()).isEqualTo(4);
        assertThat(copia.enSql().cortes()).isEqualTo(9);
        assertThat(consulta.ultimaCopiaOk()).contains(AHORA);
    }

    @Test
    void sinCortesCerradosNiMongoNiSqlDebenTenerNadaQueMedir() {
        cortesMongo.guardar(corte("c1", EstadoCorte.ANUNCIADO, Instant.parse("2026-10-14T10:00:00Z"), Duration.ofHours(2), "manga").build());

        assertThat(copiar.copiar()).isEqualTo(ResultadoDeCopia.OK);

        assertThat(cortesMongo.agregarCerrados(null).cantidadCortes()).isZero();
        assertThat(consulta.cumplimientoGlobal().paresCerrados()).isZero();
    }
}
