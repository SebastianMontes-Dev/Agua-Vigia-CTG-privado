package com.aguavigia.ctg.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static com.aguavigia.ctg.domain.NivelDeVerificacion.CUENTA_VERIFICADA;
import static com.aguavigia.ctg.domain.NivelDeVerificacion.NINGUNA;
import static com.aguavigia.ctg.domain.NivelDeVerificacion.UBICACION_VERIFICADA;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * El quórum de vecinos no basta con que llegue al umbral: hace falta que no sea una sola persona con
 * varios aparatos (≥ 2 redes) ni solo anónimos sin ninguna prueba de dónde están (≥ ⅓ con verificación).
 */
class ComposicionDelSustentoTest {

    private static final Instant AHORA = Instant.parse("2026-10-01T15:00:00Z");

    private static ReporteCiudadano reporte(int n, NivelDeVerificacion nivel, String red) {
        return new ReporteCiudadano(new ReporteId("r-" + n), new SectorId("manga"), TipoReporte.SIN_AGUA, null,
                new HuellaDispositivo("h-" + n), AHORA).conIdentidad(nivel, red);
    }

    private static List<ReporteCiudadano> sustento(NivelDeVerificacion[] niveles, String[] redes) {
        List<ReporteCiudadano> lista = new ArrayList<>();
        for (int i = 0; i < niveles.length; i++) {
            lista.add(reporte(i, niveles[i], redes[i]));
        }
        return lista;
    }

    @Test
    void unQuorumConUnTercioVerificadoYDosRedesDebeSerValido() {
        // Albornoz (umbral 3): 1 verificado de 3 (⅓), dos redes distintas.
        assertThat(ComposicionDelSustento.cumple(
                sustento(new NivelDeVerificacion[]{CUENTA_VERIFICADA, NINGUNA, NINGUNA},
                        new String[]{"red-a", "red-b", "red-b"}), 2)).isTrue();
    }

    /** Escenario 9: tres huellas desde una misma red en un barrio de umbral 3 → no hay cambio. */
    @Test
    void tresReportesDeUnaSolaRedNoDebenSerValidosAunqueEstenVerificados() {
        assertThat(ComposicionDelSustento.cumple(
                sustento(new NivelDeVerificacion[]{CUENTA_VERIFICADA, CUENTA_VERIFICADA, CUENTA_VERIFICADA},
                        new String[]{"red-a", "red-a", "red-a"}), 2)).isFalse();
    }

    @Test
    void unaSolaRedSiLaPermiteElMinimoDeRedesEnUno() {
        assertThat(ComposicionDelSustento.cumple(
                sustento(new NivelDeVerificacion[]{CUENTA_VERIFICADA, NINGUNA, NINGUNA},
                        new String[]{"red-a", "red-a", "red-a"}), 1)).isTrue();
    }

    /** Sin ninguna prueba de ubicación el reporte anónimo por sí solo no mueve el mapa. */
    @Test
    void unQuorumSinNingunReporteVerificadoNoDebeSerValido() {
        assertThat(ComposicionDelSustento.cumple(
                sustento(new NivelDeVerificacion[]{NINGUNA, NINGUNA, NINGUNA},
                        new String[]{"red-a", "red-b", "red-c"}), 2)).isFalse();
    }

    @Test
    void laUbicacionVerificadaCuentaComoVerificacion() {
        assertThat(ComposicionDelSustento.cumple(
                sustento(new NivelDeVerificacion[]{UBICACION_VERIFICADA, NINGUNA, NINGUNA},
                        new String[]{"red-a", "red-b", "red-b"}), 2)).isTrue();
    }

    /** Bordes del tercio (redondeado hacia arriba): de 4 hacen falta 2, de 6 hacen falta 2, de 7 hacen falta 3. */
    @Test
    void elTercioDebeRedondearseHaciaArriba() {
        NivelDeVerificacion[] unVerificadoDeCuatro = {CUENTA_VERIFICADA, NINGUNA, NINGUNA, NINGUNA};
        NivelDeVerificacion[] dosVerificadosDeCuatro = {CUENTA_VERIFICADA, CUENTA_VERIFICADA, NINGUNA, NINGUNA};
        String[] redes4 = {"a", "b", "a", "b"};
        assertThat(ComposicionDelSustento.cumple(sustento(unVerificadoDeCuatro, redes4), 2)).isFalse();
        assertThat(ComposicionDelSustento.cumple(sustento(dosVerificadosDeCuatro, redes4), 2)).isTrue();

        NivelDeVerificacion[] dosDeSeis = {CUENTA_VERIFICADA, CUENTA_VERIFICADA, NINGUNA, NINGUNA, NINGUNA, NINGUNA};
        assertThat(ComposicionDelSustento.cumple(sustento(dosDeSeis, new String[]{"a", "b", "a", "b", "a", "b"}), 2))
                .isTrue();

        NivelDeVerificacion[] dosDeSiete = {CUENTA_VERIFICADA, CUENTA_VERIFICADA, NINGUNA, NINGUNA, NINGUNA, NINGUNA, NINGUNA};
        assertThat(ComposicionDelSustento.cumple(
                sustento(dosDeSiete, new String[]{"a", "b", "a", "b", "a", "b", "a"}), 2)).isFalse();
    }

    @Test
    void unReporteSinRedConocidaNoDebeContarComoUnaRedMas() {
        assertThat(ComposicionDelSustento.cumple(
                sustento(new NivelDeVerificacion[]{CUENTA_VERIFICADA, CUENTA_VERIFICADA, CUENTA_VERIFICADA},
                        new String[]{"red-a", null, null}), 2)).isFalse();
    }

    @Test
    void unSustentoVacioNoDebeSerValido() {
        assertThat(ComposicionDelSustento.cumple(List.of(), 2)).isFalse();
        assertThat(ComposicionDelSustento.cumple(List.of(), 0)).isFalse();
    }

    /** Un sensor se autentica con su clave y no tiene red de ciudadano: cuenta como verificado y como su propia red. */
    @Test
    void losSensoresDebenContarComoVerificadosYComoSuPropiaRed() {
        List<ReporteCiudadano> sensores = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            sensores.add(new ReporteCiudadano(new ReporteId("s-" + i), new SectorId("manga"), TipoReporte.PRESION_BAJA,
                    null, HuellaDispositivo.deSensor("sensor-" + i), AHORA).comoDeSensor());
        }

        assertThat(ComposicionDelSustento.cumple(sensores, 2)).isTrue();
    }
}
