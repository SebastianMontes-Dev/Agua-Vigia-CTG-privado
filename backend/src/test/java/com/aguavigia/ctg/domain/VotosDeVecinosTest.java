package com.aguavigia.ctg.domain;

import com.aguavigia.ctg.domain.Afirmacion.QuorumVecinos;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Lo que los vecinos afirman de un barrio en una ventana: un voto por dispositivo, con su composición y la memoria del barrio. */
class VotosDeVecinosTest {

    private static final SectorId MANGA = new SectorId("manga");
    private static final Instant T0 = Instant.parse("2026-10-02T08:00:00Z");

    private static ReporteCiudadano reporte(String id, String huella, TipoReporte tipo, Instant cuando, NivelDeVerificacion nivel,
                                            String red) {
        return new ReporteCiudadano(new ReporteId(id), MANGA, tipo, null, new HuellaDispositivo(huella), cuando,
                EstadoModeracion.PENDIENTE, null, Set.of(), false, nivel, red);
    }

    private static ReporteCiudadano verificado(String id, String huella, TipoReporte tipo, Instant cuando, String red) {
        return reporte(id, huella, tipo, cuando, NivelDeVerificacion.UBICACION_VERIFICADA, red);
    }

    // --- un voto por dispositivo ---

    @Test
    void unVecinoQueReportaVariasVecesVotaUnaSolaVezConSuReporteMasReciente() {
        List<ReporteCiudadano> reportes = List.of(
                verificado("r1", "a", TipoReporte.SIN_AGUA, T0, "red-1"),
                verificado("r2", "a", TipoReporte.PRESION_BAJA, T0.plusSeconds(60), "red-1"),
                verificado("r3", "b", TipoReporte.SIN_AGUA, T0.plusSeconds(30), "red-2"));

        var porTipo = VotosDeVecinos.porDispositivo(reportes);

        assertThat(porTipo.get(TipoReporte.SIN_AGUA)).extracting(r -> r.id().valor()).containsExactly("r3");
        assertThat(porTipo.get(TipoReporte.PRESION_BAJA)).extracting(r -> r.id().valor()).containsExactly("r2");
    }

    // --- formar el quórum de cada tipo ---

    @Test
    void formaUnQuorumPorTipoConSuPrimerYSuUltimoReporte() {
        List<ReporteCiudadano> reportes = List.of(
                verificado("r1", "a", TipoReporte.SIN_AGUA, T0, "red-1"),
                verificado("r2", "b", TipoReporte.SIN_AGUA, T0.plusSeconds(120), "red-2"),
                verificado("r3", "c", TipoReporte.SIN_AGUA, T0.plusSeconds(60), "red-3"));

        VotosDeVecinos votos = VotosDeVecinos.formar(reportes, 3, 2);

        QuorumVecinos quorum = votos.porTipo().get(TipoReporte.SIN_AGUA);
        assertThat(quorum.respaldo()).isEqualTo(3);
        assertThat(quorum.umbral()).isEqualTo(3);
        assertThat(quorum.alcanzado()).isTrue();
        assertThat(quorum.composicionValida()).isTrue();
        assertThat(quorum.primerReporte()).isEqualTo(T0);
        assertThat(quorum.ultimoReporte()).isEqualTo(T0.plusSeconds(120));
        assertThat(votos.sustentoDe(EstadoServicio.SIN_SERVICIO)).hasSize(3);
    }

    @Test
    void unaSolaRedNoDaUnaComposicionValidaSiSeExigenDos() {
        List<ReporteCiudadano> reportes = List.of(
                verificado("r1", "a", TipoReporte.SIN_AGUA, T0, "red-1"),
                verificado("r2", "b", TipoReporte.SIN_AGUA, T0, "red-1"),
                verificado("r3", "c", TipoReporte.SIN_AGUA, T0, "red-1"));

        VotosDeVecinos votos = VotosDeVecinos.formar(reportes, 3, 2);

        assertThat(votos.porTipo().get(TipoReporte.SIN_AGUA).composicionValida()).isFalse();
    }

    @Test
    void sinReportesNoHayQuorumNiSustento() {
        VotosDeVecinos votos = VotosDeVecinos.formar(List.of(), 3, 2);

        assertThat(votos.quorums()).isEmpty();
        assertThat(votos.sustentoDe(EstadoServicio.SIN_SERVICIO)).isEmpty();
    }

    // --- el sustento de un estado ---

    @Test
    void elSustentoDeUnEstadoSonSoloLosReportesDeSuTipo() {
        VotosDeVecinos votos = VotosDeVecinos.formar(List.of(
                verificado("r1", "a", TipoReporte.SIN_AGUA, T0, "red-1"),
                verificado("r2", "b", TipoReporte.PRESION_BAJA, T0, "red-2"),
                verificado("r3", "c", TipoReporte.SERVICIO_RESTABLECIDO, T0, "red-3")), 1, 1);

        assertThat(votos.sustentoDe(EstadoServicio.PRESION_BAJA)).extracting(r -> r.id().valor()).containsExactly("r2");
        assertThat(votos.sustentoDe(EstadoServicio.CON_SERVICIO)).extracting(r -> r.id().valor()).containsExactly("r3");
    }

    // --- el restablecimiento contradicho ---

    @Test
    void sinElRestablecimientoQuitaSuQuorumPeroConservaLosDemas() {
        VotosDeVecinos votos = VotosDeVecinos.formar(List.of(
                verificado("r1", "a", TipoReporte.SIN_AGUA, T0, "red-1"),
                verificado("r2", "b", TipoReporte.SERVICIO_RESTABLECIDO, T0, "red-2")), 1, 1);

        VotosDeVecinos sin = votos.sinElRestablecimiento();

        assertThat(votos.respaldoDeRestablecimiento()).isEqualTo(1);
        assertThat(sin.respaldoDeRestablecimiento()).isZero();
        assertThat(sin.porTipo()).containsOnlyKeys(TipoReporte.SIN_AGUA);
    }

    // --- la memoria del barrio ---

    private static QuorumVecinos recordado(TipoReporte tipo, int respaldo) {
        return new QuorumVecinos(tipo, respaldo, 3, true, T0, T0, true);
    }

    @Test
    void sinReportesFrescosElBarrioSigueApoyandoseEnLoQueRecuerda() {
        VotosDeVecinos votos = VotosDeVecinos.formar(List.of(), 3, 1)
                .conMemoria(Optional.of(recordado(TipoReporte.SIN_AGUA, 5)));

        assertThat(votos.porTipo().get(TipoReporte.SIN_AGUA).sostenido()).isTrue();
        assertThat(votos.porTipo().get(TipoReporte.SIN_AGUA).respaldo()).isEqualTo(5);
    }

    /** Los reportes que fijaron el estado van saliendo de la ventana: un quórum fresco que no llega al umbral no puede borrar la memoria. */
    @Test
    void unQuorumFrescoQueNoLlegaAlUmbralNoBorraLaMemoria() {
        VotosDeVecinos fresco = VotosDeVecinos.formar(List.of(
                verificado("r1", "a", TipoReporte.SIN_AGUA, T0, "red-1")), 3, 1);

        VotosDeVecinos votos = fresco.conMemoria(Optional.of(recordado(TipoReporte.SIN_AGUA, 5)));

        assertThat(votos.porTipo().get(TipoReporte.SIN_AGUA).sostenido()).isTrue();
    }

    @Test
    void unQuorumFrescoAlcanzadoYValidoOcupaElLugarDeLaMemoria() {
        VotosDeVecinos fresco = VotosDeVecinos.formar(List.of(
                verificado("r1", "a", TipoReporte.SIN_AGUA, T0, "red-1"),
                verificado("r2", "b", TipoReporte.SIN_AGUA, T0, "red-2"),
                verificado("r3", "c", TipoReporte.SIN_AGUA, T0, "red-3")), 3, 2);

        VotosDeVecinos votos = fresco.conMemoria(Optional.of(recordado(TipoReporte.SIN_AGUA, 5)));

        assertThat(votos.porTipo().get(TipoReporte.SIN_AGUA).sostenido()).isFalse();
        assertThat(votos.porTipo().get(TipoReporte.SIN_AGUA).respaldo()).isEqualTo(3);
    }

    @Test
    void laMemoriaSinReportesFrescosNoCuentaComoReciente() {
        VotosDeVecinos votos = VotosDeVecinos.formar(List.of(), 3, 1)
                .conMemoria(Optional.of(recordado(TipoReporte.SERVICIO_RESTABLECIDO, 5)));

        // «Reciente» es lo que esta ventana acaba de alcanzar, no lo que el barrio recuerda: de ahí no se cierran cortes ni se reabren.
        assertThat(votos.recienteDe(TipoReporte.SERVICIO_RESTABLECIDO)).isEmpty();
    }
}
