package com.aguavigia.ctg.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EventoBitacoraFactoryTest {

    private final Instant inicio = Instant.parse("2026-08-09T10:00:00Z");
    private final Instant ahora = inicio.plus(1, ChronoUnit.HOURS);

    private CorteAgua corte() {
        return CorteAgua.builder()
                .id(new CorteId("corte-1"))
                .sectoresAfectados(List.of(new SectorId("manga")))
                .inicio(inicio)
                .finPrometido(inicio.plus(6, ChronoUnit.HOURS))
                .causa("Mantenimiento planta El Bosque")
                .origen(OrigenCorte.OFICIAL_ACUACAR)
                .build();
    }

    @Test
    void debeCrearElEventoDeCorteAnunciado() {
        CorteAgua corte = corte();
        SectorId sectorId = new SectorId("manga");

        EventoBitacora evento = EventoBitacoraFactory.corteAnunciado(corte, sectorId, ahora);

        assertThat(evento.tipo()).isEqualTo(TipoEvento.CORTE_ANUNCIADO);
        assertThat(evento.sectorId()).isEqualTo(sectorId);
        assertThat(evento.corteId()).isEqualTo(corte.id());
        assertThat(evento.timestamp()).isEqualTo(ahora);
        assertThat(evento.descripcion()).contains("manga").contains(corte.causa());
    }

    @Test
    void debeCrearElEventoDeCorteRestablecido() {
        CorteAgua corte = corte();
        SectorId sectorId = new SectorId("manga");

        EventoBitacora evento = EventoBitacoraFactory.corteRestablecido(corte, sectorId, ahora);

        assertThat(evento.tipo()).isEqualTo(TipoEvento.CORTE_RESTABLECIDO);
        assertThat(evento.sectorId()).isEqualTo(sectorId);
        assertThat(evento.corteId()).isEqualTo(corte.id());
        assertThat(evento.timestamp()).isEqualTo(ahora);
        assertThat(evento.descripcion()).contains("manga");
    }

    @Test
    void debeCrearElEventoDeConsensoConfirmadoSinCorteAsociado() {
        SectorId sectorId = new SectorId("bocagrande");

        EventoBitacora evento = EventoBitacoraFactory.consensoConfirmado(
                sectorId, EstadoServicio.SIN_SERVICIO, ids("r1", "r2", "r3", "r4", "r5"), ahora);

        assertThat(evento.tipo()).isEqualTo(TipoEvento.CORTE_CONFIRMADO_POR_CIUDADANOS);
        assertThat(evento.sectorId()).isEqualTo(sectorId);
        assertThat(evento.corteId()).isNull();
        assertThat(evento.timestamp()).isEqualTo(ahora);
        assertThat(evento.descripcion()).contains("5").contains("bocagrande").contains("SIN_SERVICIO");
    }

    /**
     * RF011 — el evento debe poder responder «¿qué reportes sostuvieron este cambio?». Sin los ids
     * quedaba solo un conteo, y un veedor no podía contrastar el cambio con la evidencia.
     */
    @Test
    void elEventoDeConsensoDebeConservarLosReportesQueSustentanElCambio() {
        EventoBitacora evento = EventoBitacoraFactory.consensoConfirmado(
                new SectorId("bocagrande"), EstadoServicio.SIN_SERVICIO, ids("r1", "r2", "r3"), ahora);

        assertThat(evento.reportesSustento()).containsExactly(
                new ReporteId("r1"), new ReporteId("r2"), new ReporteId("r3"));
    }

    @Test
    void elEventoDeConsensoDebeAfirmarElEstadoAlQueCambio() {
        EventoBitacora evento = EventoBitacoraFactory.consensoConfirmado(
                new SectorId("bocagrande"), EstadoServicio.SIN_SERVICIO, ids("r1", "r2", "r3"), ahora);

        assertThat(evento.estado()).isEqualTo(EstadoServicio.SIN_SERVICIO);
    }

    @Test
    void unEventoSinSustentoNoDebeTenerListaNula() {
        EventoBitacora evento = new EventoBitacora(new EventoId("e1"), TipoEvento.CORTE_ANUNCIADO,
                new SectorId("manga"), null, ahora, "anuncio");

        assertThat(evento.reportesSustento()).isEmpty();
    }

    private static java.util.List<ReporteId> ids(String... valores) {
        return java.util.Arrays.stream(valores).map(ReporteId::new).toList();
    }
}
