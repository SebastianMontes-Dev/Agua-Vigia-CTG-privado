package com.aguavigia.ctg.domain;

import com.aguavigia.ctg.domain.Afirmacion.QuorumVecinos;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El quórum a 30 minutos deja de verse pronto, pero el estado que produjo no: el barrio lo recuerda con su origen y su respaldo, y
 * solo caduca si ningún reporte nuevo lo renueva.
 */
class MemoriaDelBarrioTest {

    private static final SectorId MANGA = new SectorId("manga");
    private static final Instant FORMADO = Instant.parse("2026-10-02T08:00:00Z");
    private static final Instant VERIFICADO = Instant.parse("2026-10-02T09:00:00Z");

    private static Sector sector(EstadoServicio estado, OrigenEstado origen, RespaldoVecinal respaldo, Instant verificado) {
        MarcasDeEstado marcas = new MarcasDeEstado(origen, null, false, false, 0, respaldo);
        return new Sector(MANGA, "MANGA", 1000, estado, FORMADO, verificado, marcas);
    }

    @Test
    void unEstadoDeLosVecinosSeRecuerdaConSuTipoRespaldoYUltimaVerificacion() {
        Optional<QuorumVecinos> memoria = MemoriaDelBarrio.recordado(
                sector(EstadoServicio.SIN_SERVICIO, OrigenEstado.VECINOS, new RespaldoVecinal(7, 5), VERIFICADO));

        assertThat(memoria).hasValueSatisfying(q -> {
            assertThat(q.tipo()).isEqualTo(TipoReporte.SIN_AGUA);
            assertThat(q.respaldo()).isEqualTo(7);
            assertThat(q.umbral()).isEqualTo(5);
            assertThat(q.sostenido()).isTrue();
            assertThat(q.alcanzado()).isTrue();
            assertThat(q.primerReporte()).isEqualTo(FORMADO);
            assertThat(q.ultimoReporte()).isEqualTo(VERIFICADO);
        });
    }

    @Test
    void tambienSeRecuerdaLoQueSostienenLosSensores() {
        assertThat(MemoriaDelBarrio.recordado(
                sector(EstadoServicio.PRESION_BAJA, OrigenEstado.SENSOR, new RespaldoVecinal(4, 3), VERIFICADO))).isPresent();
    }

    @Test
    void loQueSostieneUnaFuenteOficialNoEsMemoriaDeVecinos() {
        assertThat(MemoriaDelBarrio.recordado(
                sector(EstadoServicio.SIN_SERVICIO, OrigenEstado.ACUACAR, new RespaldoVecinal(7, 5), VERIFICADO))).isEmpty();
        assertThat(MemoriaDelBarrio.recordado(
                sector(EstadoServicio.SIN_SERVICIO, OrigenEstado.VEEDOR, new RespaldoVecinal(7, 5), VERIFICADO))).isEmpty();
    }

    @Test
    void sinEstadoOSinRespaldoNoHayNadaQueRecordar() {
        assertThat(MemoriaDelBarrio.recordado(sector(null, OrigenEstado.VECINOS, new RespaldoVecinal(7, 5), VERIFICADO))).isEmpty();
        assertThat(MemoriaDelBarrio.recordado(sector(EstadoServicio.SIN_SERVICIO, OrigenEstado.VECINOS, null, VERIFICADO))).isEmpty();
    }

    @Test
    void sinFechaDeVerificacionSeUsaLaDeFormacionYSiTampocoHayLaMasAntigua() {
        Optional<QuorumVecinos> sinVerificar = MemoriaDelBarrio.recordado(
                sector(EstadoServicio.SIN_SERVICIO, OrigenEstado.VECINOS, new RespaldoVecinal(7, 5), null));
        assertThat(sinVerificar).hasValueSatisfying(q -> assertThat(q.ultimoReporte()).isEqualTo(FORMADO));

        Sector sinFechas = new Sector(MANGA, "MANGA", 1000, EstadoServicio.SIN_SERVICIO, null, null,
                new MarcasDeEstado(OrigenEstado.VECINOS, null, false, false, 0, new RespaldoVecinal(7, 5)));
        assertThat(MemoriaDelBarrio.recordado(sinFechas)).hasValueSatisfying(q -> {
            assertThat(q.ultimoReporte()).isEqualTo(Instant.EPOCH);
            assertThat(q.primerReporte()).isEqualTo(Instant.EPOCH);
        });
    }

    @Test
    void cadaEstadoTieneElTipoDeReporteQueLoProduce() {
        assertThat(MemoriaDelBarrio.tipoDe(EstadoServicio.SIN_SERVICIO)).isEqualTo(TipoReporte.SIN_AGUA);
        assertThat(MemoriaDelBarrio.tipoDe(EstadoServicio.CORTE_PROGRAMADO)).isEqualTo(TipoReporte.SIN_AGUA);
        assertThat(MemoriaDelBarrio.tipoDe(EstadoServicio.PRESION_BAJA)).isEqualTo(TipoReporte.PRESION_BAJA);
        assertThat(MemoriaDelBarrio.tipoDe(EstadoServicio.CON_SERVICIO)).isEqualTo(TipoReporte.SERVICIO_RESTABLECIDO);
    }

    @Test
    void sostenidaPorElListonQueSeFijoYLaComposicion() {
        QuorumVecinos recordada = new QuorumVecinos(TipoReporte.SIN_AGUA, 5, 5, true, FORMADO, VERIFICADO, true);
        ReglasDeEstado reglas = ReglasDeEstado.porDefecto();

        assertThat(MemoriaDelBarrio.liston(recordada, reglas)).as("un corte se fijó con el umbral").isEqualTo(5);

        QuorumVecinos restablecida = new QuorumVecinos(TipoReporte.SERVICIO_RESTABLECIDO, 8, 15, true, FORMADO, VERIFICADO, true);
        assertThat(MemoriaDelBarrio.liston(restablecida, reglas))
                .as("un restablecimiento se fijó con el quórum reducido").isEqualTo(reglas.quorumReducido(15));
    }
}
