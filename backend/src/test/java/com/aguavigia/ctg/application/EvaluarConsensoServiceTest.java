package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.EstadoPublicado;
import com.aguavigia.ctg.domain.EstadoServicio;
import com.aguavigia.ctg.domain.EstrategiaConsenso;
import com.aguavigia.ctg.domain.OrigenEstado;
import com.aguavigia.ctg.domain.ReglasDeEstado;
import com.aguavigia.ctg.domain.ReporteId;
import com.aguavigia.ctg.domain.RespaldoVecinal;
import com.aguavigia.ctg.domain.ResultadoConsenso;
import com.aguavigia.ctg.domain.ResultadoDeRecalculo;
import com.aguavigia.ctg.domain.Sector;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.port.in.RecalcularSectorUseCase;
import com.aguavigia.ctg.domain.port.out.ContadorReportesPort;
import com.aguavigia.ctg.domain.port.out.ReservaDeEvaluacionPort;
import com.aguavigia.ctg.domain.port.out.SectorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * El consenso ya no decide nada: conserva la reserva de Redis (una evaluación por intervalo) y un prefiltro
 * barato que evita recalcular el barrio por cada reporte, y delega en {@link RecalcularSectorUseCase}, que es
 * el único escritor del estado. Cómo se cuentan los votos, se elige el estado y se escribe con compare-and-set
 * se prueba en {@code RecalcularSectorServiceTest}.
 */
class EvaluarConsensoServiceTest {

    private static final SectorId SECTOR_ID = new SectorId("bocagrande");
    private static final Duration VENTANA = Duration.ofMinutes(30);

    private SectorRepository sectores;
    private ContadorReportesPort contadorReportes;
    private ReservaDeEvaluacionPort reserva;
    private RecalcularSectorUseCase recalcular;
    private EvaluarConsensoService servicio;

    @BeforeEach
    void montar() {
        sectores = mock(SectorRepository.class);
        contadorReportes = mock(ContadorReportesPort.class);
        reserva = mock(ReservaDeEvaluacionPort.class);
        recalcular = mock(RecalcularSectorUseCase.class);
        EstrategiaConsenso umbralDeTres = sector -> 3;
        given(reserva.reservar(any())).willReturn(true);
        given(sectores.buscarPorId(SECTOR_ID)).willReturn(Optional.of(sectorConServicio()));
        given(recalcular.recalcular(SECTOR_ID)).willReturn(ResultadoDeRecalculo.sinCambio(EstadoPublicado.sinDatos()));
        servicio = new EvaluarConsensoService(sectores, contadorReportes, reserva, umbralDeTres,
                ReglasDeEstado.porDefecto(), recalcular, 30);
    }

    private static Sector sectorConServicio() {
        return new Sector(SECTOR_ID, "BOCAGRANDE", 12000, EstadoServicio.CON_SERVICIO);
    }

    private void conReportesRecientes(long cuantos) {
        given(contadorReportes.contarRecientes(SECTOR_ID, VENTANA)).willReturn(cuantos);
    }

    @Test
    void recalculaElBarrioCuandoLosReportesRecientesLlegaranAlUmbral() {
        conReportesRecientes(3);

        servicio.evaluar(SECTOR_ID);

        verify(recalcular).recalcular(SECTOR_ID);
    }

    /** Confirmar un restablecimiento pide menos vecinos que reportar una avería: el prefiltro no puede taparlo. */
    @Test
    void recalculaConLosReportesQueBastanParaElQuorumReducido() {
        conReportesRecientes(2);

        servicio.evaluar(SECTOR_ID);

        verify(recalcular).recalcular(SECTOR_ID);
    }

    @Test
    void noRecalculaSiLosReportesNoLlegaranNiAlQuorumReducido() {
        conReportesRecientes(1);

        ResultadoConsenso resultado = servicio.evaluar(SECTOR_ID);

        assertThat(resultado.alcanzado()).isFalse();
        verify(recalcular, never()).recalcular(any());
    }

    @Test
    void traduceElCambioDeEstadoConLosReportesQueLoSustentan() {
        conReportesRecientes(3);
        EstadoPublicado publicado = EstadoPublicado.porVecinos(EstadoServicio.SIN_SERVICIO, null,
                new RespaldoVecinal(3, 3), false);
        List<ReporteId> sustento = List.of(new ReporteId("r1"), new ReporteId("r2"), new ReporteId("r3"));
        given(recalcular.recalcular(SECTOR_ID)).willReturn(new ResultadoDeRecalculo(publicado, true, sustento));

        ResultadoConsenso resultado = servicio.evaluar(SECTOR_ID);

        assertThat(resultado.alcanzado()).isTrue();
        assertThat(resultado.nuevoEstado()).isEqualTo(EstadoServicio.SIN_SERVICIO);
        assertThat(resultado.reportesQueSustentan()).containsExactlyElementsOf(sustento);
    }

    @Test
    void siElRecalculoNoMovioElEstadoElConsensoNoSeAlcanzo() {
        conReportesRecientes(3);
        given(recalcular.recalcular(SECTOR_ID)).willReturn(ResultadoDeRecalculo.sinCambio(
                EstadoPublicado.de(EstadoServicio.SIN_SERVICIO, OrigenEstado.ACUACAR, null, false)));

        ResultadoConsenso resultado = servicio.evaluar(SECTOR_ID);

        assertThat(resultado.alcanzado()).isFalse();
        assertThat(resultado.nuevoEstado()).isNull();
    }

    @Test
    void siOtraPeticionYaTieneLaReservaDejaElSectorPendienteSinConsultarNada() {
        given(reserva.reservar(SECTOR_ID)).willReturn(false);

        ResultadoConsenso resultado = servicio.evaluar(SECTOR_ID);

        assertThat(resultado.alcanzado()).isFalse();
        verify(reserva).dejarPendiente(SECTOR_ID);
        verify(sectores, never()).buscarPorId(any());
        verify(recalcular, never()).recalcular(any());
    }

    @Test
    void evaluarPendientesEvaluaCadaSectorSinPedirLaReserva() {
        SectorId manga = new SectorId("manga");
        given(reserva.tomarPendientes()).willReturn(List.of(SECTOR_ID, manga));
        given(sectores.buscarPorId(manga)).willReturn(Optional.of(new Sector(manga, "MANGA", 12000, EstadoServicio.CON_SERVICIO)));
        given(contadorReportes.contarRecientes(any(), any())).willReturn(3L);
        given(recalcular.recalcular(manga)).willReturn(ResultadoDeRecalculo.sinCambio(EstadoPublicado.sinDatos()));

        servicio.evaluarPendientes();

        verify(recalcular).recalcular(SECTOR_ID);
        verify(recalcular).recalcular(manga);
        verify(reserva, never()).reservar(any());
    }

    @Test
    void evaluarPendientesSigueConLosDemasSectoresSiUnoFallaYDejaElFallidoPendiente() {
        SectorId manga = new SectorId("manga");
        given(reserva.tomarPendientes()).willReturn(List.of(SECTOR_ID, manga));
        given(sectores.buscarPorId(SECTOR_ID)).willThrow(new IllegalStateException("Mongo caído"));
        given(sectores.buscarPorId(manga)).willReturn(Optional.of(new Sector(manga, "MANGA", 12000, EstadoServicio.CON_SERVICIO)));
        given(contadorReportes.contarRecientes(any(), any())).willReturn(3L);
        given(recalcular.recalcular(manga)).willReturn(ResultadoDeRecalculo.sinCambio(EstadoPublicado.sinDatos()));

        servicio.evaluarPendientes();

        verify(recalcular).recalcular(manga);
        verify(reserva).dejarPendiente(SECTOR_ID);
    }

    /** Si escribir falla a mitad del recálculo la falla sube: la transacción de allí revierte estado y evento juntos. */
    @Test
    void propagaLaFallaDelRecalculo() {
        conReportesRecientes(3);
        given(recalcular.recalcular(SECTOR_ID)).willThrow(new IllegalStateException("Mongo caído al anexar el evento"));

        assertThatThrownBy(() -> servicio.evaluar(SECTOR_ID)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rechazaUnSectorQueNoExiste() {
        given(sectores.buscarPorId(new SectorId("no-existe"))).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.evaluar(new SectorId("no-existe")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
