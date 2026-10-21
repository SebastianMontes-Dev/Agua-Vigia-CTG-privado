package com.aguavigia.ctg.infrastructure.persistence.sql;

import com.aguavigia.ctg.domain.AnaliticaNoDisponibleException;
import com.aguavigia.ctg.domain.ConteosAnaliticos;
import com.aguavigia.ctg.domain.FilaCorteAnalitica;
import com.aguavigia.ctg.domain.FilaParAnalitica;
import com.aguavigia.ctg.domain.FilaSectorAnalitica;
import com.aguavigia.ctg.domain.RegistroDeCopia;
import com.aguavigia.ctg.domain.ResultadoDeCopia;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contra un PostgreSQL real (la misma etiqueta que el compose): Flyway aplica las migraciones, la copia es idempotente, las claves
 * foráneas se hacen cumplir y un fallo a mitad deja la copia anterior intacta.
 */
@Testcontainers
class AlmacenAnaliticoPostgresAdapterIT {

    static final Instant INICIO = Instant.parse("2026-10-14T10:00:00Z");
    static final Instant COPIA = Instant.parse("2026-10-15T15:00:00Z");

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.11-alpine")
            .withDatabaseName("aguavigia_analitica").withUsername("aguavigia");

    private JdbcTemplate jdbc;
    private AlmacenAnaliticoPostgresAdapter almacen;
    private ConsultaAnaliticaPostgresAdapter consulta;

    @BeforeEach
    void esquemaLimpio() {
        var dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP SCHEMA IF EXISTS analitica CASCADE");
        almacen = new AlmacenAnaliticoPostgresAdapter(dataSource);
        consulta = new ConsultaAnaliticaPostgresAdapter(dataSource);
    }

    static List<FilaSectorAnalitica> barrios() {
        return List.of(new FilaSectorAnalitica("manga", "Manga", 12000), new FilaSectorAnalitica("bocagrande", "Bocagrande", null));
    }

    static FilaCorteAnalitica corte(String id, String estado, FilaParAnalitica... pares) {
        return new FilaCorteAnalitica(id, "OFICIAL_ACUACAR", estado, "Mantenimiento", INICIO, INICIO.plus(2, ChronoUnit.HOURS),
                List.of(pares));
    }

    static FilaParAnalitica cerrado(String sectorId, long horasDespues) {
        return new FilaParAnalitica(sectorId, INICIO.plus(horasDespues, ChronoUnit.HOURS), "ACUACAR", false);
    }

    private int contar(String tabla) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM analitica." + tabla, Integer.class);
    }

    @Test
    void flywayDebeCrearElEsquemaLasTablasYLasVistas() {
        almacen.registrarInicioDeCopia(COPIA);

        List<String> tablas = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'analitica' AND table_type = 'BASE TABLE'",
                String.class);
        List<String> vistas = jdbc.queryForList(
                "SELECT table_name FROM information_schema.views WHERE table_schema = 'analitica'", String.class);
        assertThat(tablas).contains("sector", "corte", "corte_sector", "copia", "flyway_schema_history");
        assertThat(vistas).containsExactlyInAnyOrder("v_par_cerrado", "v_cumplimiento_por_sector", "v_cumplimiento_global",
                "v_cumplimiento_mensual");
    }

    @Test
    void migrarDosVecesNoDebeFallarNiRepetirMigraciones() {
        almacen.registrarInicioDeCopia(COPIA);
        // Otra instancia del backend (o un reinicio) encuentra el esquema ya migrado.
        new AlmacenAnaliticoPostgresAdapter(new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword())).registrarInicioDeCopia(COPIA);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM analitica.flyway_schema_history WHERE success AND version IS NOT NULL", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void copiarDosVecesDebeDejarLosMismosConteosSinDuplicar() {
        almacen.registrarInicioDeCopia(COPIA);
        var cortes = List.of(corte("c1", "RESTABLECIDO", cerrado("manga", 3), cerrado("bocagrande", 4)),
                corte("c2", "ANUNCIADO", new FilaParAnalitica("manga", null, null, false)));

        ConteosAnaliticos primera = almacen.reemplazar(barrios(), cortes, COPIA);
        ConteosAnaliticos segunda = almacen.reemplazar(barrios(), cortes, COPIA.plusSeconds(300));

        assertThat(primera).isEqualTo(new ConteosAnaliticos(2, 2, 3));
        assertThat(segunda).isEqualTo(primera);
        assertThat(contar("sector")).isEqualTo(2);
        assertThat(contar("corte")).isEqualTo(2);
        assertThat(contar("corte_sector")).isEqualTo(3);
    }

    @Test
    void debeActualizarLoQueCambioYQuitarLoQueYaNoExisteEnMongo() {
        almacen.registrarInicioDeCopia(COPIA);
        almacen.reemplazar(barrios(), List.of(corte("c1", "ANUNCIADO", new FilaParAnalitica("manga", null, null, false)),
                corte("c2", "ANUNCIADO", new FilaParAnalitica("bocagrande", null, null, false))), COPIA);

        // c1 se restablece, c2 desaparece y bocagrande ya no existe.
        ConteosAnaliticos despues = almacen.reemplazar(List.of(new FilaSectorAnalitica("manga", "Manga renombrado", 5)),
                List.of(corte("c1", "RESTABLECIDO", cerrado("manga", 3))), COPIA.plusSeconds(300));

        assertThat(despues).isEqualTo(new ConteosAnaliticos(1, 1, 1));
        assertThat(jdbc.queryForObject("SELECT estado FROM analitica.corte WHERE id = 'c1'", String.class)).isEqualTo("RESTABLECIDO");
        assertThat(jdbc.queryForObject("SELECT nombre FROM analitica.sector WHERE id = 'manga'", String.class)).isEqualTo("Manga renombrado");
        assertThat(jdbc.queryForObject("SELECT cierre_hora IS NOT NULL FROM analitica.corte_sector", Boolean.class)).isTrue();
    }

    @Test
    void debeGuardarLaPoblacionNulaSinConvertirlaEnCero() {
        almacen.registrarInicioDeCopia(COPIA);
        almacen.reemplazar(barrios(), List.of(), COPIA);

        assertThat(jdbc.queryForObject("SELECT poblacion FROM analitica.sector WHERE id = 'bocagrande'", Integer.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT poblacion FROM analitica.sector WHERE id = 'manga'", Integer.class)).isEqualTo(12000);
    }

    @Test
    void debeRechazarUnParConUnBarrioQueNoExiste() {
        almacen.registrarInicioDeCopia(COPIA);
        almacen.reemplazar(barrios(), List.of(corte("c1", "ANUNCIADO", new FilaParAnalitica("manga", null, null, false))), COPIA);

        assertThatThrownBy(() -> jdbc.update("INSERT INTO analitica.corte_sector (corte_id, sector_id) VALUES ('c1', 'fantasma')"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO analitica.corte_sector (corte_id, sector_id) VALUES ('no-hay', 'manga')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void debeRechazarUnCorteConElFinAnteriorAlInicioYUnCierreSinHoraConFuente() {
        almacen.registrarInicioDeCopia(COPIA);

        assertThatThrownBy(() -> jdbc.update("INSERT INTO analitica.corte VALUES ('malo', 'VEEDOR', 'ANUNCIADO', NULL, "
                + "'2026-10-14T10:00:00Z', '2026-10-14T09:00:00Z', now())")).isInstanceOf(DataIntegrityViolationException.class);
        almacen.reemplazar(barrios(), List.of(corte("c1", "ANUNCIADO", new FilaParAnalitica("manga", null, null, false))), COPIA);
        assertThatThrownBy(() -> jdbc.update("UPDATE analitica.corte_sector SET cierre_fuente = 'ACUACAR' WHERE corte_id = 'c1'"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void borrarUnCorteDebeBorrarEnCascadaSusPares() {
        almacen.registrarInicioDeCopia(COPIA);
        almacen.reemplazar(barrios(), List.of(corte("c1", "RESTABLECIDO", cerrado("manga", 3), cerrado("bocagrande", 4))), COPIA);

        jdbc.update("DELETE FROM analitica.corte WHERE id = 'c1'");

        assertThat(contar("corte_sector")).isZero();
    }

    @Test
    void unFalloAMitadDeLaCopiaNoDebeDejarNadaAMedias() {
        almacen.registrarInicioDeCopia(COPIA);
        var buenos = List.of(corte("c1", "RESTABLECIDO", cerrado("manga", 3)));
        almacen.reemplazar(barrios(), buenos, COPIA);

        // El segundo corte apunta a un barrio que no está en la copia: la clave foránea lo rechaza al insertar los pares, después de
        // haber borrado y reescrito lo demás.
        var conParRoto = List.of(corte("c1", "ANUNCIADO", cerrado("manga", 1)),
                corte("c9", "ANUNCIADO", new FilaParAnalitica("fantasma", null, null, false)));

        assertThatThrownBy(() -> almacen.reemplazar(barrios(), conParRoto, COPIA.plusSeconds(300)))
                .isInstanceOf(AnaliticaNoDisponibleException.class);

        assertThat(contar("corte")).isEqualTo(1);
        assertThat(contar("corte_sector")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT estado FROM analitica.corte WHERE id = 'c1'", String.class)).isEqualTo("RESTABLECIDO");
        assertThat(jdbc.queryForObject("SELECT copiado_en FROM analitica.corte WHERE id = 'c1'", java.sql.Timestamp.class).toInstant())
                .isEqualTo(COPIA);
    }

    @Test
    void debeAnotarCadaCopiaConSusConteosYSuResultado() {
        long uno = almacen.registrarInicioDeCopia(COPIA);
        almacen.cerrarCopia(uno, COPIA.plusSeconds(2), ResultadoDeCopia.OK, new ConteosAnaliticos(2, 1, 3),
                new ConteosAnaliticos(2, 1, 3), null);
        long dos = almacen.registrarInicioDeCopia(COPIA.plusSeconds(300));
        almacen.cerrarCopia(dos, COPIA.plusSeconds(301), ResultadoDeCopia.FALLIDA, new ConteosAnaliticos(2, 1, 3), null, "se cayó");
        long tres = almacen.registrarInicioDeCopia(COPIA.plusSeconds(600));

        List<RegistroDeCopia> copias = consulta.ultimasCopias(10);

        assertThat(copias).extracting(RegistroDeCopia::id).containsExactly(tres, dos, uno);
        assertThat(copias.get(0).resultado()).isEqualTo(ResultadoDeCopia.EN_CURSO);
        assertThat(copias.get(0).terminadaEn()).isNull();
        assertThat(copias.get(1)).satisfies(copia -> {
            assertThat(copia.resultado()).isEqualTo(ResultadoDeCopia.FALLIDA);
            assertThat(copia.enMongo()).isEqualTo(new ConteosAnaliticos(2, 1, 3));
            assertThat(copia.enSql()).isNull();
            assertThat(copia.detalle()).isEqualTo("se cayó");
        });
        assertThat(copias.get(2).enSql()).isEqualTo(new ConteosAnaliticos(2, 1, 3));
        assertThat(consulta.ultimasCopias(1)).hasSize(1);
        assertThat(consulta.ultimaCopiaOk()).contains(COPIA.plusSeconds(2));
    }

    @Test
    void unaCopiaSinNingunaOkNoDebeTenerFechaDeUltimaCopiaOk() {
        long id = almacen.registrarInicioDeCopia(COPIA);
        almacen.cerrarCopia(id, COPIA.plusSeconds(1), ResultadoDeCopia.CON_DIFERENCIAS, null, null, "no cuadra");

        assertThat(consulta.ultimaCopiaOk()).isEmpty();
    }

    @Test
    void consultarSinHaberCopiadoNuncaDebeDecirQueLaCopiaNoEstaDisponible() {
        assertThatThrownBy(() -> consulta.cumplimientoGlobal()).isInstanceOf(AnaliticaNoDisponibleException.class);
        assertThatThrownBy(() -> consulta.ultimasCopias(5)).isInstanceOf(AnaliticaNoDisponibleException.class);
    }

    @Test
    void conPostgresInalcanzableDebeSalirComoAnaliticaNoDisponible() {
        var sinBase = new DriverManagerDataSource("jdbc:postgresql://127.0.0.1:1/nada", "aguavigia", "");
        sinBase.setConnectionProperties(propiedades("connectTimeout", "1"));

        assertThatThrownBy(() -> new AlmacenAnaliticoPostgresAdapter(sinBase).registrarInicioDeCopia(COPIA))
                .isInstanceOf(AnaliticaNoDisponibleException.class);
        assertThatThrownBy(() -> new ConsultaAnaliticaPostgresAdapter(sinBase).cumplimientoGlobal())
                .isInstanceOf(AnaliticaNoDisponibleException.class);
    }

    private static java.util.Properties propiedades(String clave, String valor) {
        var propiedades = new java.util.Properties();
        propiedades.setProperty(clave, valor);
        return propiedades;
    }
}
