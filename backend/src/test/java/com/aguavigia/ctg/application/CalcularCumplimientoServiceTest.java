package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.AgregadoDuraciones;
import com.aguavigia.ctg.domain.CalidadDelCumplimiento;
import com.aguavigia.ctg.domain.CalidadDelDato;
import com.aguavigia.ctg.domain.CierreDeCorte;
import com.aguavigia.ctg.domain.CorteAgua;
import com.aguavigia.ctg.domain.OrigenEstado;
import com.aguavigia.ctg.domain.CorteId;
import com.aguavigia.ctg.domain.EstadoCorte;
import com.aguavigia.ctg.domain.IndiceCumplimiento;
import com.aguavigia.ctg.domain.OrigenCorte;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.port.out.CorteAguaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class CalcularCumplimientoServiceTest {

    private static final Instant INICIO = Instant.parse("2026-08-09T10:00:00Z");

    private static final Instant AHORA = Instant.parse("2026-10-02T08:00:00Z");

    private CorteAguaRepository cortes;
    private CalcularCumplimientoService servicio;

    @BeforeEach
    void montar() {
        cortes = mock(CorteAguaRepository.class);
        given(cortes.calidadDelDato(any(), any())).willReturn(CalidadDelDato.vacia());
        servicio = new CalcularCumplimientoService(cortes, () -> AHORA);
    }

    private CorteAgua corteCerrado(String id, List<String> sectores, Duration prometida, Duration real) {
        return CorteAgua.builder()
                .id(new CorteId(id))
                .sectoresAfectados(sectores.stream().map(SectorId::new).toList())
                .inicio(INICIO)
                .finPrometido(INICIO.plus(prometida))
                .finReal(INICIO.plus(real))
                .causa("Mantenimiento")
                .origen(OrigenCorte.OFICIAL_ACUACAR)
                .estado(EstadoCorte.RESTABLECIDO)
                .build();
    }

    private CorteAgua corteAbierto(String id, List<String> sectores) {
        return CorteAgua.builder()
                .id(new CorteId(id))
                .sectoresAfectados(sectores.stream().map(SectorId::new).toList())
                .inicio(INICIO)
                .finPrometido(INICIO.plus(2, ChronoUnit.HOURS))
                .causa("Mantenimiento")
                .origen(OrigenCorte.OFICIAL_ACUACAR)
                .estado(EstadoCorte.CONFIRMADO)
                .build();
    }

    @Test
    void debeCalcularElIndiceDeUnCorteQueTomoMasDeLoPrometido() {
        given(cortes.buscarPorId(new CorteId("corte-1"))).willReturn(Optional.of(
                corteCerrado("corte-1", List.of("manga"), Duration.ofHours(2), Duration.ofHours(8))));

        IndiceCumplimiento indice = servicio.porCorte(new CorteId("corte-1"));

        assertThat(indice.sectorId()).isNull();
        assertThat(indice.duracionPrometida()).isEqualTo(Duration.ofHours(2));
        assertThat(indice.duracionReal()).isEqualTo(Duration.ofHours(8));
        assertThat(indice.desviacion()).isEqualTo(Duration.ofHours(6));
        assertThat(indice.porcentajeCumplimiento()).isEqualTo(25.0);
    }

    @Test
    void debeCaparEnCienPorCientoUnCorteQueTerminoAntesDeLoPrometido() {
        given(cortes.buscarPorId(new CorteId("corte-1"))).willReturn(Optional.of(
                corteCerrado("corte-1", List.of("manga"), Duration.ofHours(4), Duration.ofHours(1))));

        IndiceCumplimiento indice = servicio.porCorte(new CorteId("corte-1"));

        assertThat(indice.desviacion()).isEqualTo(Duration.ofHours(-3));
        assertThat(indice.porcentajeCumplimiento()).isEqualTo(100.0);
    }

    @Test
    void debeRechazarElIndiceDeUnCorteQueNoExiste() {
        given(cortes.buscarPorId(new CorteId("no-existe"))).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.porCorte(new CorteId("no-existe")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void debeRechazarElIndiceDeUnCorteAbierto() {
        given(cortes.buscarPorId(new CorteId("corte-1")))
                .willReturn(Optional.of(corteAbierto("corte-1", List.of("manga"))));

        assertThatThrownBy(() -> servicio.porCorte(new CorteId("corte-1")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void debeAgregarPorSectorSumandoDuracionesNoPromediandoPorcentajes() {
        // Dos horas prometidas por 8 reales (25%), y una hora prometida por 1 real (100%).
        // Promediar los porcentajes daria 62.5%; sumar duraciones da 3/9 = 33.3%.
        given(cortes.agregarCerrados(new SectorId("manga")))
                .willReturn(new AgregadoDuraciones(Duration.ofHours(3), Duration.ofHours(9), 2));

        IndiceCumplimiento indice = servicio.porSector(new SectorId("manga"));

        assertThat(indice.sectorId()).isEqualTo(new SectorId("manga"));
        assertThat(indice.duracionPrometida()).isEqualTo(Duration.ofHours(3));
        assertThat(indice.duracionReal()).isEqualTo(Duration.ofHours(9));
        assertThat(indice.porcentajeCumplimiento()).isCloseTo(33.33, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void debeRechazarElIndiceDeUnSectorSinCortesCerrados() {
        given(cortes.agregarCerrados(new SectorId("manga"))).willReturn(AgregadoDuraciones.vacio());

        assertThatThrownBy(() -> servicio.porSector(new SectorId("manga")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- calidad del dato (D14) ---

    @Test
    void elIndiceDeclaraCuantoDeLoMedidoEsProvisional() {
        given(cortes.agregarCerrados(null))
                .willReturn(new AgregadoDuraciones(Duration.ofHours(8), Duration.ofHours(12), 4, 1));

        IndiceCumplimiento indice = servicio.global();

        assertThat(indice.porcentajeProvisional()).isEqualTo(25.0);
    }

    @Test
    void elIndiceDeclaraLosCortesSinCierreConfirmadoYLosAnulados() {
        given(cortes.agregarCerrados(null)).willReturn(new AgregadoDuraciones(Duration.ofHours(4), Duration.ofHours(8), 2));
        given(cortes.calidadDelDato(null, AHORA)).willReturn(new CalidadDelDato(7, 2));

        IndiceCumplimiento indice = servicio.global();

        assertThat(indice.cortesSinCierreConfirmado()).isEqualTo(7);
        assertThat(indice.cortesAnulados()).isEqualTo(2);
    }

    @Test
    void elIndiceDeUnSectorMiraLaCalidadDeEseSector() {
        given(cortes.agregarCerrados(new SectorId("manga")))
                .willReturn(new AgregadoDuraciones(Duration.ofHours(2), Duration.ofHours(2), 1));
        given(cortes.calidadDelDato(new SectorId("manga"), AHORA)).willReturn(new CalidadDelDato(1, 0));

        assertThat(servicio.porSector(new SectorId("manga")).cortesSinCierreConfirmado()).isEqualTo(1);
    }

    /** Sin un solo cierre el Índice no existe (400), pero lo que no se pudo medir sí se puede contar: «sin datos suficientes». */
    @Test
    void sinCierresLaCalidadSeSigueDeclarando() {
        given(cortes.agregarCerrados(null)).willReturn(AgregadoDuraciones.vacio());
        given(cortes.calidadDelDato(null, AHORA)).willReturn(new CalidadDelDato(12, 1));

        CalidadDelCumplimiento calidad = servicio.calidad(null);

        assertThat(calidad.cierresMedidos()).isZero();
        assertThat(calidad.porcentajeProvisional()).isZero();
        assertThat(calidad.cortesSinCierreConfirmado()).isEqualTo(12);
        assertThat(calidad.cortesAnulados()).isEqualTo(1);
    }

    @Test
    void elIndiceDeUnCorteConCierreProvisionalDeclaraSuPorcentaje() {
        CorteAgua corte = CorteAgua.builder().id(new CorteId("c"))
                .sectoresAfectados(List.of(new SectorId("manga"), new SectorId("bocagrande")))
                .inicio(INICIO).finPrometido(INICIO.plus(2, ChronoUnit.HOURS)).causa("x")
                .origen(OrigenCorte.OFICIAL_ACUACAR).estado(EstadoCorte.CONFIRMADO).build()
                .cerrarSector(new SectorId("manga"), new CierreDeCorte(INICIO.plus(1, ChronoUnit.HOURS), OrigenEstado.VEEDOR, false))
                .cerrarSector(new SectorId("bocagrande"), new CierreDeCorte(INICIO.plus(3, ChronoUnit.HOURS), OrigenEstado.VECINOS, true));
        given(cortes.buscarPorId(new CorteId("c"))).willReturn(Optional.of(corte));

        IndiceCumplimiento indice = servicio.porCorte(new CorteId("c"));

        // Por par corte-barrio: 2 + 2 horas prometidas, 1 + 3 reales; uno de dos cierres es provisional.
        assertThat(indice.duracionPrometida()).isEqualTo(Duration.ofHours(4));
        assertThat(indice.duracionReal()).isEqualTo(Duration.ofHours(4));
        assertThat(indice.porcentajeProvisional()).isEqualTo(50.0);
    }

    /**
     * La suma ahora la hace el pipeline Mongo (CorteAguaRepository.agregarCerrados) — este test
     * verifica que el servicio calcule el porcentaje sobre el agregado, no que sepa sumar
     * ventanas; el pipeline en sí se prueba contra Mongo real en CorteAguaMongoAdapterTest.
     */
    @Test
    void debeCalcularElIndiceGlobalSobreElAgregadoDeCortesCerrados() {
        given(cortes.agregarCerrados(null))
                .willReturn(new AgregadoDuraciones(Duration.ofHours(4), Duration.ofHours(8), 2));

        IndiceCumplimiento indice = servicio.global();

        assertThat(indice.sectorId()).isNull();
        assertThat(indice.duracionPrometida()).isEqualTo(Duration.ofHours(4));
        assertThat(indice.duracionReal()).isEqualTo(Duration.ofHours(8));
    }

    @Test
    void debeRechazarElIndiceGlobalSinCortesCerrados() {
        given(cortes.agregarCerrados(null)).willReturn(AgregadoDuraciones.vacio());

        assertThatThrownBy(() -> servicio.global()).isInstanceOf(IllegalArgumentException.class);
    }
}
