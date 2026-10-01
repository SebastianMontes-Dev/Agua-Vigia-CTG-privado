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
                sectorId, EstadoServicio.SIN_SERVICIO, ids("r1", "r2", "r3", "r4", "r5"), new RespaldoVecinal(3, 3), ahora);

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
                new SectorId("bocagrande"), EstadoServicio.SIN_SERVICIO, ids("r1", "r2", "r3"), new RespaldoVecinal(3, 3), ahora);

        assertThat(evento.reportesSustento()).containsExactly(
                new ReporteId("r1"), new ReporteId("r2"), new ReporteId("r3"));
    }

    @Test
    void elEventoDeConsensoDebeAfirmarElEstadoAlQueCambio() {
        EventoBitacora evento = EventoBitacoraFactory.consensoConfirmado(
                new SectorId("bocagrande"), EstadoServicio.SIN_SERVICIO, ids("r1", "r2", "r3"), new RespaldoVecinal(3, 3), ahora);

        assertThat(evento.estado()).isEqualTo(EstadoServicio.SIN_SERVICIO);
    }

    @Test
    void unEventoSinSustentoNoDebeTenerListaNula() {
        EventoBitacora evento = new EventoBitacora(new EventoId("e1"), TipoEvento.CORTE_ANUNCIADO,
                new SectorId("manga"), null, ahora, "anuncio");

        assertThat(evento.reportesSustento()).isEmpty();
    }

    @Test
    void debeCrearElEventoDeCorteAnuladoConSuMotivo() {
        CorteAgua anulado = corte().anular("El boletín hablaba de otro barrio");

        EventoBitacora evento = EventoBitacoraFactory.corteAnulado(anulado, new SectorId("manga"), ahora);

        assertThat(evento.tipo()).isEqualTo(TipoEvento.CORTE_ANULADO);
        assertThat(evento.corteId()).isEqualTo(anulado.id());
        assertThat(evento.descripcion()).contains("manga").contains("El boletín hablaba de otro barrio");
    }

    @Test
    void debeCrearElEventoDeBoletinAnuladoConSuMotivoYSuFuente() {
        EventoBitacora evento = EventoBitacoraFactory.boletinAnulado(
                new SectorId("manga"), "Leyó mal el barrio", "https://acuacar.com/2854", ahora);

        assertThat(evento.tipo()).isEqualTo(TipoEvento.CORTE_ANULADO);
        assertThat(evento.sectorId()).isEqualTo(new SectorId("manga"));
        assertThat(evento.descripcion()).contains("manga").contains("Leyó mal el barrio");
        assertThat(evento.urlOriginal()).isEqualTo("https://acuacar.com/2854");
    }

    @Test
    void debeCrearElEventoDeCorteExpiradoSinAfirmarUnEstado() {
        EventoBitacora evento = EventoBitacoraFactory.corteExpirado(corte().expirar(), new SectorId("manga"), ahora);

        assertThat(evento.tipo()).isEqualTo(TipoEvento.CORTE_EXPIRADO);
        assertThat(evento.estado()).isNull();
        assertThat(evento.descripcion()).contains("manga");
    }

    @Test
    void debeCrearElEventoDeRestablecimientoPorVecinosConSusReportes() {
        EventoBitacora evento = EventoBitacoraFactory.restablecimientoPorVecinos(
                new SectorId("manga"), ids("r1", "r2"), new RespaldoVecinal(3, 3), ahora);

        assertThat(evento.tipo()).isEqualTo(TipoEvento.RESTABLECIMIENTO_POR_VECINOS);
        assertThat(evento.estado()).isEqualTo(EstadoServicio.CON_SERVICIO);
        assertThat(evento.reportesSustento()).containsExactlyElementsOf(ids("r1", "r2"));
    }

    @Test
    void debeCrearElEventoDeEstadoEnDisputaConElEstadoQueSeDiscute() {
        EventoBitacora evento = EventoBitacoraFactory.estadoEnDisputa(
                new SectorId("manga"), EstadoServicio.SIN_SERVICIO, 11, ahora);

        assertThat(evento.tipo()).isEqualTo(TipoEvento.ESTADO_EN_DISPUTA);
        assertThat(evento.estado()).isEqualTo(EstadoServicio.SIN_SERVICIO);
        assertThat(evento.descripcion()).contains("11");
    }

    @Test
    void debeCrearElEventoDeConsensoRevertido() {
        EventoBitacora evento = EventoBitacoraFactory.consensoRevertido(new SectorId("manga"), ahora);

        assertThat(evento.tipo()).isEqualTo(TipoEvento.CONSENSO_REVERTIDO);
        assertThat(evento.estado()).isNull();
    }

    private static java.util.List<ReporteId> ids(String... valores) {
        return java.util.Arrays.stream(valores).map(ReporteId::new).toList();
    }

    /** La bitácora enseña quién sostiene cada evento: la tarjeta distingue un boletín de un quórum de vecinos. */
    @Test
    void elConsensoDeVecinosDeclaraSuFuenteYSuRespaldo() {
        EventoBitacora evento = EventoBitacoraFactory.consensoConfirmado(
                new SectorId("manga"), EstadoServicio.SIN_SERVICIO, ids("r1", "r2", "r3"), new RespaldoVecinal(11, 12), ahora);

        assertThat(evento.fuente()).isEqualTo(OrigenEstado.VECINOS);
        assertThat(evento.respaldo()).isEqualTo(new RespaldoVecinal(11, 12));
    }

    @Test
    void elRestablecimientoPorVecinosDeclaraSuFuenteYSuRespaldo() {
        EventoBitacora evento = EventoBitacoraFactory.restablecimientoPorVecinos(
                new SectorId("manga"), ids("r1", "r2"), new RespaldoVecinal(2, 3), ahora);

        assertThat(evento.fuente()).isEqualTo(OrigenEstado.VECINOS);
        assertThat(evento.respaldo()).isEqualTo(new RespaldoVecinal(2, 3));
    }

    @Test
    void laDisputaYLaReversionLasSostienenLosVecinosPeroSinRespaldoPropio() {
        EventoBitacora disputa = EventoBitacoraFactory.estadoEnDisputa(
                new SectorId("manga"), EstadoServicio.SIN_SERVICIO, 11, ahora);
        EventoBitacora reversion = EventoBitacoraFactory.consensoRevertido(new SectorId("manga"), ahora);

        assertThat(disputa.fuente()).isEqualTo(OrigenEstado.VECINOS);
        assertThat(reversion.fuente()).isEqualTo(OrigenEstado.VECINOS);
        assertThat(disputa.respaldo()).isNull();
        assertThat(reversion.respaldo()).isNull();
    }

    @Test
    void unBoletinDeAcuacarDeclaraAcuacarYUnaNotaDePrensaDeclaraPrensa() {
        EventoBitacora deAcuacar = EventoBitacoraFactory.detectadoPorIngesta(new SectorId("manga"), "MANGA",
                EstadoServicio.SIN_SERVICIO, "acuacar", "https://acuacar.com/1", null, "Suspensión", ahora);
        EventoBitacora dePrensa = EventoBitacoraFactory.detectadoPorIngesta(new SectorId("manga"), "MANGA",
                EstadoServicio.SIN_SERVICIO, "zona-cero", "https://zonacero.com/x", null, "Suspensión", ahora);

        assertThat(deAcuacar.fuente()).isEqualTo(OrigenEstado.ACUACAR);
        assertThat(dePrensa.fuente()).isEqualTo(OrigenEstado.PRENSA);
        assertThat(deAcuacar.respaldo()).isNull();
    }

    @Test
    void elCorteAnunciadoDeclaraQuienLoRegistro() {
        CorteAgua delVeedor = CorteAgua.builder().id(new CorteId("c1")).sectoresAfectados(List.of(new SectorId("manga")))
                .inicio(inicio).finPrometido(inicio.plus(6, ChronoUnit.HOURS)).causa("x").origen(OrigenCorte.VEEDOR).build();

        assertThat(EventoBitacoraFactory.corteAnunciado(delVeedor, new SectorId("manga"), ahora).fuente())
                .isEqualTo(OrigenEstado.VEEDOR);
        assertThat(EventoBitacoraFactory.corteAnunciado(corte(), new SectorId("manga"), ahora).fuente())
                .isEqualTo(OrigenEstado.ACUACAR);
    }

    @Test
    void elCorteRestablecidoLoCierraElVeedor() {
        CorteAgua cerrado = corte().cerrar(inicio.plusSeconds(3600));

        assertThat(EventoBitacoraFactory.corteRestablecido(cerrado, new SectorId("manga"), ahora).fuente())
                .isEqualTo(OrigenEstado.VEEDOR);
    }
}
