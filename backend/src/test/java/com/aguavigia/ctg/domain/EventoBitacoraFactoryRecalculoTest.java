package com.aguavigia.ctg.domain;

import com.aguavigia.ctg.domain.Afirmacion.QuorumVecinos;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** La cita que deja en la bitácora un recálculo del estado de un barrio: quién lo sostiene decide qué se anexa. */
class EventoBitacoraFactoryRecalculoTest {

    private static final SectorId MANGA = new SectorId("manga");
    private static final Instant AHORA = Instant.parse("2026-10-02T12:00:00Z");

    private final Sector sector = new Sector(MANGA, "MANGA", 1000, null);

    private static ReporteCiudadano reporte(String id, String huella, TipoReporte tipo) {
        return new ReporteCiudadano(new ReporteId(id), MANGA, tipo, null, new HuellaDispositivo(huella), AHORA.minusSeconds(60),
                EstadoModeracion.PENDIENTE, null, Set.of(), false, NivelDeVerificacion.UBICACION_VERIFICADA, "red-" + huella);
    }

    private static VotosDeVecinos votosDe(TipoReporte tipo, int cuantos) {
        List<ReporteCiudadano> reportes = new java.util.ArrayList<>();
        for (int i = 0; i < cuantos; i++) {
            reportes.add(reporte("r" + i, "h" + i, tipo));
        }
        return VotosDeVecinos.formar(reportes, cuantos, 1);
    }

    // --- el estado cambió ---

    @Test
    void siLosVecinosFijaronUnCorteSeCitanSusReportes() {
        EstadoPublicado publicado = EstadoPublicado.porVecinos(EstadoServicio.SIN_SERVICIO, null, new RespaldoVecinal(3, 3), false);

        Optional<EventoBitacora> evento = EventoBitacoraFactory.delCambioDeEstado(
                sector, publicado, List.of(), votosDe(TipoReporte.SIN_AGUA, 3), AHORA);

        assertThat(evento).hasValueSatisfying(e -> {
            assertThat(e.tipo()).isEqualTo(TipoEvento.CORTE_CONFIRMADO_POR_CIUDADANOS);
            assertThat(e.reportesSustento()).hasSize(3);
            assertThat(e.fuente()).isEqualTo(OrigenEstado.VECINOS);
        });
    }

    @Test
    void siLosVecinosConfirmaronQueVolvioElAguaEsUnRestablecimientoPorVecinos() {
        EstadoPublicado publicado = EstadoPublicado.porVecinos(EstadoServicio.CON_SERVICIO, null, new RespaldoVecinal(2, 2), false);

        Optional<EventoBitacora> evento = EventoBitacoraFactory.delCambioDeEstado(
                sector, publicado, List.of(), votosDe(TipoReporte.SERVICIO_RESTABLECIDO, 2), AHORA);

        assertThat(evento).hasValueSatisfying(e -> assertThat(e.tipo()).isEqualTo(TipoEvento.RESTABLECIMIENTO_POR_VECINOS));
    }

    /** Un sensor no es un vecino: el evento declara que lo sostienen sensores de la red. */
    @Test
    void siTodosLosVotosSonDeSensoresLaFuenteDelEventoEsSensor() {
        ReporteCiudadano deSensor = new ReporteCiudadano(new ReporteId("s1"), MANGA, TipoReporte.PRESION_BAJA, null,
                HuellaDispositivo.deSensor("sensor-1"), AHORA.minusSeconds(30), EstadoModeracion.PENDIENTE, null, Set.of(), true);
        EstadoPublicado publicado = new EstadoPublicado(EstadoServicio.PRESION_BAJA, OrigenEstado.SENSOR, null, false, false, 0,
                new RespaldoVecinal(1, 1), false);

        Optional<EventoBitacora> evento = EventoBitacoraFactory.delCambioDeEstado(
                sector, publicado, List.of(), VotosDeVecinos.formar(List.of(deSensor), 1, 1), AHORA);

        assertThat(evento).hasValueSatisfying(e -> assertThat(e.fuente()).isEqualTo(OrigenEstado.SENSOR));
    }

    @Test
    void sinReportesQueSustentenOSiLoSostieneElVeedorNoSeAnexaNada() {
        EstadoPublicado delVeedor = EstadoPublicado.de(EstadoServicio.SIN_SERVICIO, OrigenEstado.VEEDOR, null, false);
        assertThat(EventoBitacoraFactory.delCambioDeEstado(sector, delVeedor, List.of(), VotosDeVecinos.formar(List.of(), 3, 1), AHORA))
                .as("el corte del veedor ya dejó su evento al registrarse").isEmpty();

        EstadoPublicado deMemoria = EstadoPublicado.porVecinos(EstadoServicio.SIN_SERVICIO, null, new RespaldoVecinal(3, 3), false);
        assertThat(EventoBitacoraFactory.delCambioDeEstado(sector, deMemoria, List.of(), VotosDeVecinos.formar(List.of(), 3, 1), AHORA))
                .as("un estado que viene de la memoria no tiene reportes que citar").isEmpty();

        assertThat(EventoBitacoraFactory.delCambioDeEstado(sector, EstadoPublicado.sinDatos(), List.of(),
                VotosDeVecinos.formar(List.of(), 3, 1), AHORA)).as("volver a «sin datos» no es una noticia").isEmpty();
    }

    @Test
    void unEstadoOficialSinBoletinQueLoSustenteNoAnexaNada() {
        EstadoPublicado delBoletin = EstadoPublicado.de(EstadoServicio.SIN_SERVICIO, OrigenEstado.ACUACAR, null, false);

        assertThat(EventoBitacoraFactory.delCambioDeEstado(sector, delBoletin, List.of(), VotosDeVecinos.formar(List.of(), 3, 1), AHORA))
                .isEmpty();
    }

    // --- varias cosas pasaron a la vez ---

    @Test
    void alDescartarReportesQueSostenianElEstadoPrimeroSeAnexaQueElConsensoSeRevirtio() {
        List<EventoBitacora> eventos = EventoBitacoraFactory.delRecalculo(sector, EstadoPublicado.sinDatos(), List.of(), null,
                VotosDeVecinos.formar(List.of(), 3, 1), true, true, AHORA);

        assertThat(eventos).extracting(EventoBitacora::tipo).containsExactly(TipoEvento.CONSENSO_REVERTIDO);
    }

    @Test
    void unaDisputaSeAnotaAlAbrirseYElCambioDeEstadoNoLaRepite() {
        Sector sinDisputa = new Sector(MANGA, "MANGA", 1000, EstadoServicio.SIN_SERVICIO, AHORA, AHORA,
                MarcasDeEstado.ninguna());
        EstadoPublicado enDisputa = EstadoPublicado.de(EstadoServicio.SIN_SERVICIO, OrigenEstado.ACUACAR, null, false).enDisputaPor(11);

        List<EventoBitacora> eventos = EventoBitacoraFactory.delRecalculo(sinDisputa, enDisputa, List.of(), null,
                VotosDeVecinos.formar(List.of(), 3, 1), false, false, AHORA);

        assertThat(eventos).extracting(EventoBitacora::tipo).containsExactly(TipoEvento.ESTADO_EN_DISPUTA);

        Sector yaEnDisputa = new Sector(MANGA, "MANGA", 1000, EstadoServicio.SIN_SERVICIO, AHORA, AHORA,
                MarcasDeEstado.de(enDisputa));
        assertThat(EventoBitacoraFactory.delRecalculo(yaEnDisputa, enDisputa, List.of(), null,
                VotosDeVecinos.formar(List.of(), 3, 1), false, false, AHORA)).as("se anota al abrirse, no cada minuto").isEmpty();
    }

    /** Un corte que se reabre lo provocaron los vecinos aunque el estado lo afirme de nuevo la fuente oficial: se cita su quórum. */
    @Test
    void siSeReabreUnCorteSeCitaElQuorumQueLoContradijoYNoElBoletinDeAntes() {
        VotosDeVecinos votos = votosDe(TipoReporte.SIN_AGUA, 3);
        QuorumVecinos contradice = votos.porTipo().get(TipoReporte.SIN_AGUA);
        EstadoPublicado oficial = EstadoPublicado.de(EstadoServicio.SIN_SERVICIO, OrigenEstado.ACUACAR, null, false);

        List<EventoBitacora> eventos = EventoBitacoraFactory.delRecalculo(sector, oficial, List.of(), contradice, votos, false, false, AHORA);

        assertThat(eventos).singleElement().satisfies(e -> {
            assertThat(e.tipo()).isEqualTo(TipoEvento.CORTE_CONFIRMADO_POR_CIUDADANOS);
            assertThat(e.reportesSustento()).hasSize(3);
        });
    }

    @Test
    void unaReaperturaSinReportesQueCitarNoAnexaNada() {
        QuorumVecinos recordado = new QuorumVecinos(TipoReporte.SIN_AGUA, 3, 3, true, AHORA, AHORA, true);

        assertThat(EventoBitacoraFactory.delRecalculo(sector, EstadoPublicado.sinDatos(), List.of(), recordado,
                VotosDeVecinos.formar(List.of(), 3, 1), false, false, AHORA)).isEmpty();
    }
}
