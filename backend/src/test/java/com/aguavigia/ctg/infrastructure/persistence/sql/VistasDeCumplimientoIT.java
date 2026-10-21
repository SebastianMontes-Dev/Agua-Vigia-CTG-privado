package com.aguavigia.ctg.infrastructure.persistence.sql;

import com.aguavigia.ctg.application.CalcularCumplimientoService;
import com.aguavigia.ctg.domain.AgregadoDuraciones;
import com.aguavigia.ctg.domain.FilaCorteAnalitica;
import com.aguavigia.ctg.domain.FilaParAnalitica;
import com.aguavigia.ctg.domain.FilaSectorAnalitica;
import com.aguavigia.ctg.domain.IndiceCumplimiento;
import com.aguavigia.ctg.domain.CumplimientoAnalitico;
import com.aguavigia.ctg.domain.PuntoMensualAnalitico;
import com.aguavigia.ctg.domain.port.out.CorteAguaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Las vistas tienen que dar el mismo porcentaje que {@link CalcularCumplimientoService} (ADR-022) en los casos de borde. La
 * fórmula del servicio se ejercita de verdad: se le da la suma de duraciones que sale de los mismos datos.
 */
@Testcontainers
class VistasDeCumplimientoIT {

    private static final Instant INICIO = Instant.parse("2026-10-14T10:00:00Z");
    private static final Instant COPIA = Instant.parse("2026-10-15T15:00:00Z");

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.11-alpine")
            .withDatabaseName("aguavigia_analitica").withUsername("aguavigia");

    private AlmacenAnaliticoPostgresAdapter almacen;
    private ConsultaAnaliticaPostgresAdapter consulta;

    @BeforeEach
    void esquemaLimpio() {
        var dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        new JdbcTemplate(dataSource).execute("DROP SCHEMA IF EXISTS analitica CASCADE");
        almacen = new AlmacenAnaliticoPostgresAdapter(dataSource);
        consulta = new ConsultaAnaliticaPostgresAdapter(dataSource);
        almacen.registrarInicioDeCopia(COPIA);
    }

    private static FilaCorteAnalitica corte(String id, String estado, long horasPrometidas, FilaParAnalitica... pares) {
        return new FilaCorteAnalitica(id, "OFICIAL_ACUACAR", estado, "Mantenimiento", INICIO,
                INICIO.plus(Duration.ofHours(horasPrometidas)), List.of(pares));
    }

    private static FilaParAnalitica cierreA(String sectorId, Duration despuesDelInicio) {
        return new FilaParAnalitica(sectorId, INICIO.plus(despuesDelInicio), "ACUACAR", false);
    }

    private static final List<FilaSectorAnalitica> BARRIOS = List.of(new FilaSectorAnalitica("manga", "Manga", 100),
            new FilaSectorAnalitica("bocagrande", "Bocagrande", 100), new FilaSectorAnalitica("crespo", "Crespo", 100));

    /** El Índice según el servicio de Java, alimentado con la suma de las mismas duraciones. */
    private static IndiceCumplimiento segunJava(Duration prometida, Duration real, long pares) {
        CorteAguaRepository cortes = mock(CorteAguaRepository.class);
        when(cortes.agregarCerrados(any())).thenReturn(new AgregadoDuraciones(prometida, real, pares));
        when(cortes.calidadDelDato(any(), any())).thenReturn(com.aguavigia.ctg.domain.CalidadDelDato.vacia());
        return new CalcularCumplimientoService(cortes, () -> COPIA).global();
    }

    @Test
    void sinNingunCierreElGlobalNoDebeTenerParesCerrados() {
        almacen.reemplazar(BARRIOS, List.of(corte("c1", "ANUNCIADO", 2, new FilaParAnalitica("manga", null, null, false))), COPIA);

        CumplimientoAnalitico global = consulta.cumplimientoGlobal();

        assertThat(global.paresCerrados()).isZero();
        assertThat(global.prometidaSegundos()).isZero();
        assertThat(global.realSegundos()).isZero();
    }

    @Test
    void unCorteQueDuroMasDeLoPrometidoDebeDarElMismoPorcentajeQueJava() {
        almacen.reemplazar(BARRIOS, List.of(corte("c1", "RESTABLECIDO", 2, cierreA("manga", Duration.ofHours(8)))), COPIA);

        CumplimientoAnalitico global = consulta.cumplimientoGlobal();

        IndiceCumplimiento java = segunJava(Duration.ofHours(2), Duration.ofHours(8), 1);
        assertThat(global.prometidaSegundos()).isEqualTo(java.duracionPrometida().toSeconds()).isEqualTo(7200);
        assertThat(global.realSegundos()).isEqualTo(java.duracionReal().toSeconds()).isEqualTo(28800);
        assertThat(global.desviacionSegundos()).isEqualTo(java.desviacion().toSeconds());
        assertThat(global.porcentajeCumplimiento()).isCloseTo(java.porcentajeCumplimiento(), within(1e-9)).isCloseTo(25.0, within(1e-9));
    }

    @Test
    void unCorteQueTerminoAntesDeLoPrometidoDebeCapar100() {
        almacen.reemplazar(BARRIOS, List.of(corte("c1", "RESTABLECIDO", 6, cierreA("manga", Duration.ofHours(2)))), COPIA);

        CumplimientoAnalitico global = consulta.cumplimientoGlobal();

        assertThat(global.porcentajeCumplimiento()).isEqualTo(100.0);
        assertThat(global.desviacionSegundos()).isNegative();
        assertThat(global.porcentajeCumplimiento())
                .isEqualTo(segunJava(Duration.ofHours(6), Duration.ofHours(2), 1).porcentajeCumplimiento());
    }

    @Test
    void unCierreEnElInstanteDelInicioDebeDar100PorqueLaDuracionRealEsCero() {
        almacen.reemplazar(BARRIOS, List.of(corte("c1", "RESTABLECIDO", 2, cierreA("manga", Duration.ZERO))), COPIA);

        CumplimientoAnalitico global = consulta.cumplimientoGlobal();

        assertThat(global.paresCerrados()).isEqualTo(1);
        assertThat(global.realSegundos()).isZero();
        assertThat(global.porcentajeCumplimiento()).isEqualTo(100.0);
    }

    @Test
    void debeSumarDuracionesYNoPromediarPorcentajes() {
        // Un corte largo incumplido (2 h prometidas, 8 reales) y uno corto cumplido (1 h prometida, 1 real): 3 h / 9 h = 33,3 %.
        // El promedio de los porcentajes (25 % y 100 %) daría 62,5 %, que es justo lo que ADR-022 descarta.
        almacen.reemplazar(BARRIOS, List.of(
                corte("c1", "RESTABLECIDO", 2, cierreA("manga", Duration.ofHours(8))),
                corte("c2", "RESTABLECIDO", 1, cierreA("bocagrande", Duration.ofHours(1)))), COPIA);

        CumplimientoAnalitico global = consulta.cumplimientoGlobal();

        assertThat(global.paresCerrados()).isEqualTo(2);
        assertThat(global.porcentajeCumplimiento()).isCloseTo(300.0 / 9.0, within(1e-9));
        assertThat(global.porcentajeCumplimiento())
                .isCloseTo(segunJava(Duration.ofHours(3), Duration.ofHours(9), 2).porcentajeCumplimiento(), within(1e-9));
    }

    @Test
    void losCortesAnuladosNoDebenEntrarAlIndice() {
        almacen.reemplazar(BARRIOS, List.of(
                corte("c1", "RESTABLECIDO", 2, cierreA("manga", Duration.ofHours(2))),
                corte("c2", "ANULADO", 1, cierreA("manga", Duration.ofHours(20)))), COPIA);

        CumplimientoAnalitico global = consulta.cumplimientoGlobal();

        assertThat(global.paresCerrados()).isEqualTo(1);
        assertThat(global.realSegundos()).isEqualTo(7200);
        assertThat(global.porcentajeCumplimiento()).isEqualTo(100.0);
    }

    @Test
    void cadaBarrioDeUnMismoCorteDebeContarConSuPropioCierre() {
        // D14: manga volvió a las 3 h, bocagrande sigue sin servicio. El corte sigue abierto y aun así manga ya cuenta.
        almacen.reemplazar(BARRIOS, List.of(corte("c1", "ANUNCIADO", 2, cierreA("manga", Duration.ofHours(3)),
                new FilaParAnalitica("bocagrande", null, null, false))), COPIA);

        assertThat(consulta.cumplimientoGlobal().paresCerrados()).isEqualTo(1);
        CumplimientoAnalitico manga = consulta.cumplimientoDe("manga").orElseThrow();
        CumplimientoAnalitico bocagrande = consulta.cumplimientoDe("bocagrande").orElseThrow();
        assertThat(manga.paresCerrados()).isEqualTo(1);
        assertThat(manga.nombre()).isEqualTo("Manga");
        assertThat(manga.porcentajeCumplimiento()).isCloseTo(200.0 / 3.0, within(1e-9));
        assertThat(bocagrande.paresCerrados()).isZero();
    }

    @Test
    void unBarrioQueNoExisteNoDebeTenerIndice() {
        almacen.reemplazar(BARRIOS, List.of(), COPIA);

        assertThat(consulta.cumplimientoDe("fantasma")).isEmpty();
        assertThat(consulta.cumplimientoDe("crespo")).get().extracting(CumplimientoAnalitico::paresCerrados).isEqualTo(0L);
    }

    @Test
    void unCierreProvisionalDebeContarIgualQueUnoConfirmado() {
        almacen.reemplazar(BARRIOS, List.of(corte("c1", "RESTABLECIDO", 2,
                new FilaParAnalitica("manga", INICIO.plus(Duration.ofHours(4)), "VECINOS", true))), COPIA);

        assertThat(consulta.cumplimientoGlobal().porcentajeCumplimiento()).isCloseTo(50.0, within(1e-9));
    }

    @Test
    void laSerieMensualDebeAgruparPorMesEnHoraDeCartagenaYNoDeUtc() {
        // 02:00Z del 1 de noviembre es el 31 de octubre a las 21:00 en Cartagena: cuenta en octubre.
        Instant inicio = Instant.parse("2026-10-31T20:00:00Z");
        var finDeMes = new FilaCorteAnalitica("c1", "OFICIAL_ACUACAR", "RESTABLECIDO", null, inicio, inicio.plus(Duration.ofHours(2)),
                List.of(new FilaParAnalitica("manga", Instant.parse("2026-11-01T02:00:00Z"), "ACUACAR", false)));
        var deNoviembre = new FilaCorteAnalitica("c2", "OFICIAL_ACUACAR", "RESTABLECIDO", null,
                Instant.parse("2026-11-10T10:00:00Z"), Instant.parse("2026-11-10T12:00:00Z"),
                List.of(new FilaParAnalitica("manga", Instant.parse("2026-11-10T11:00:00Z"), "ACUACAR", false)));
        almacen.reemplazar(BARRIOS, List.of(deNoviembre, finDeMes), COPIA);

        List<PuntoMensualAnalitico> serie = consulta.cumplimientoMensual();

        assertThat(serie).extracting(PuntoMensualAnalitico::mes).containsExactly(YearMonth.of(2026, 10), YearMonth.of(2026, 11));
        assertThat(serie.get(0).paresCerrados()).isEqualTo(1);
        assertThat(serie.get(0).porcentajeCumplimiento()).isCloseTo(2.0 * 100 / 6.0, within(1e-9));
        assertThat(serie.get(1).porcentajeCumplimiento()).isEqualTo(100.0);
    }

    @Test
    void lasFraccionesDeSegundoDebenTruncarseComoJava() {
        // 2 h 0,5 s prometidas y 3 h 0,7 s reales: Java suma en ms y trunca a segundos al final.
        Instant inicio = Instant.parse("2026-10-14T10:00:00.000Z");
        var fila = new FilaCorteAnalitica("c1", "OFICIAL_ACUACAR", "RESTABLECIDO", null, inicio,
                inicio.plusSeconds(7200).plusMillis(500),
                List.of(new FilaParAnalitica("manga", inicio.plusSeconds(10800).plusMillis(700), "ACUACAR", false)));
        almacen.reemplazar(BARRIOS, List.of(fila), COPIA);

        CumplimientoAnalitico global = consulta.cumplimientoGlobal();

        Duration prometida = Duration.ofSeconds(7200, 500_000_000);
        Duration real = Duration.ofSeconds(10800, 700_000_000);
        IndiceCumplimiento java = segunJava(prometida, real, 1);
        assertThat(global.prometidaSegundos()).isEqualTo(java.duracionPrometida().toSeconds());
        assertThat(global.realSegundos()).isEqualTo(java.duracionReal().toSeconds());
        assertThat(global.desviacionSegundos()).isEqualTo(java.desviacion().toSeconds());
        assertThat(global.porcentajeCumplimiento()).isCloseTo(java.porcentajeCumplimiento(), within(1e-9));
    }
}
