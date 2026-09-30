package com.aguavigia.ctg.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class MarcasDeEstadoTest {

    private static final Instant INICIO = Instant.parse("2026-08-21T14:00:00Z");
    private static final Instant FIN = Instant.parse("2026-08-21T23:00:00Z");

    @Test
    void unEstadoSinMarcasNoDeclaraNadaMasQueSuOrigen() {
        MarcasDeEstado marcas = MarcasDeEstado.ninguna();

        assertThat(marcas.origen()).isNull();
        assertThat(marcas.ventanaPrometida()).isNull();
        assertThat(marcas.porConfirmar()).isFalse();
        assertThat(marcas.enDisputa()).isFalse();
        assertThat(marcas.reportesEnContra()).isZero();
        assertThat(marcas.respaldo()).isNull();
    }

    /** Lo que se guarda en el barrio es lo que el resolutor decidió, sin perder ninguna marca. */
    @Test
    void debeConservarTodasLasMarcasDelEstadoPublicado() {
        EstadoPublicado publicado = new EstadoPublicado(EstadoServicio.SIN_SERVICIO, OrigenEstado.ACUACAR,
                new VentanaTiempo(INICIO, FIN), true, true, 11, new RespaldoVecinal(11, 11), false);

        MarcasDeEstado marcas = MarcasDeEstado.de(publicado);

        assertThat(marcas.origen()).isEqualTo(OrigenEstado.ACUACAR);
        assertThat(marcas.ventanaPrometida()).isEqualTo(new VentanaTiempo(INICIO, FIN));
        assertThat(marcas.porConfirmar()).isTrue();
        assertThat(marcas.enDisputa()).isTrue();
        assertThat(marcas.reportesEnContra()).isEqualTo(11);
        assertThat(marcas.respaldo()).isEqualTo(new RespaldoVecinal(11, 11));
    }

    @Test
    void unSectorSinMarcasExplicitasLasTieneVacias() {
        Sector sector = new Sector(new SectorId("manga"), "MANGA", 5000, EstadoServicio.CON_SERVICIO);

        assertThat(sector.marcas()).isEqualTo(MarcasDeEstado.ninguna());
    }
}
