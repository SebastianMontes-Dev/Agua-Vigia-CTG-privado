package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.EstadoServicio;
import com.aguavigia.ctg.domain.EstrategiaConsenso;
import com.aguavigia.ctg.domain.EventoBitacora;
import com.aguavigia.ctg.domain.HuellaDispositivo;
import com.aguavigia.ctg.domain.ReporteCiudadano;
import com.aguavigia.ctg.domain.ReporteId;
import com.aguavigia.ctg.domain.ResultadoConsenso;
import com.aguavigia.ctg.domain.Sector;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.TipoEvento;
import com.aguavigia.ctg.domain.TipoReporte;
import com.aguavigia.ctg.domain.port.in.RegistrarEventoBitacoraUseCase;
import com.aguavigia.ctg.domain.port.out.ContadorReportesPort;
import com.aguavigia.ctg.domain.port.out.RelojPort;
import com.aguavigia.ctg.domain.port.out.ReservaDeEvaluacionPort;
import com.aguavigia.ctg.domain.port.out.ReporteCiudadanoRepository;
import com.aguavigia.ctg.domain.port.out.SectorRepository;
import com.aguavigia.ctg.domain.port.out.TransaccionPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class EvaluarConsensoServiceTest {

    private static final Instant AHORA = Instant.parse("2026-08-08T15:30:00Z");
    private static final SectorId SECTOR_ID = new SectorId("bocagrande");

    private SectorRepository sectores;
    private ReporteCiudadanoRepository reportes;
    private ContadorReportesPort contadorReportes;
    private ReservaDeEvaluacionPort reserva;
    private EstrategiaConsenso estrategia;
    private RegistrarEventoBitacoraUseCase registrarEvento;
    private RelojPort reloj;
    private TransaccionPort transaccion;

    private EvaluarConsensoService servicio;

    @BeforeEach
    void montar() {
        sectores = mock(SectorRepository.class);
        reportes = mock(ReporteCiudadanoRepository.class);
        contadorReportes = mock(ContadorReportesPort.class);
        reserva = mock(ReservaDeEvaluacionPort.class);
        estrategia = mock(EstrategiaConsenso.class);
        registrarEvento = mock(RegistrarEventoBitacoraUseCase.class);
        reloj = mock(RelojPort.class);
        transaccion = spy(new TransaccionPasoDirecto());

        given(reloj.ahora()).willReturn(AHORA);
        given(reserva.reservar(any())).willReturn(true);
        given(sectores.cambiarEstadoSiEs(any(), any(), any())).willReturn(true);
        servicio = new EvaluarConsensoService(
                sectores, reportes, contadorReportes, reserva, estrategia, registrarEvento, reloj, transaccion, 30);
    }

    /** Ejecuta la acción directamente, sin Mongo real — la atomicidad real se prueba en TransaccionMongoAdapterIntegrationTest. */
    private static class TransaccionPasoDirecto implements TransaccionPort {
        @Override
        public <T> T ejecutar(java.util.function.Supplier<T> accion) {
            return accion.get();
        }
    }

    private ReporteCiudadano reporte(String id, TipoReporte tipo) {
        return new ReporteCiudadano(new ReporteId(id), SECTOR_ID, tipo, null, new HuellaDispositivo("h-" + id), AHORA);
    }

    private ReporteCiudadano reporte(String id, String huella, TipoReporte tipo, Instant instante) {
        return new ReporteCiudadano(new ReporteId(id), SECTOR_ID, tipo, null,
                new HuellaDispositivo(huella), instante);
    }

    private void conVotos(Map<TipoReporte, Long> votos) {
        given(reportes.contarVotosRecientes(any(), any())).willReturn(votos);
    }

    @Test
    void debeCambiarElEstadoYAnexarUnEventoCuandoSeAlcanzaElConsenso() {
        Sector sector = new Sector(SECTOR_ID, "BOCAGRANDE", 12000, EstadoServicio.CON_SERVICIO);
        given(sectores.buscarPorId(SECTOR_ID)).willReturn(Optional.of(sector));
        given(contadorReportes.contarRecientes(any(), any())).willReturn(3L);
        conVotos(Map.of(TipoReporte.SIN_AGUA, 3L));
        given(estrategia.seAlcanzaConsenso(3L, sector)).willReturn(true);
        given(reportes.listarRecientesPorSector(any(), any())).willReturn(List.of(
                reporte("r1", TipoReporte.SIN_AGUA), reporte("r2", TipoReporte.SIN_AGUA), reporte("r3", TipoReporte.SIN_AGUA)));

        ResultadoConsenso resultado = servicio.evaluar(SECTOR_ID);

        assertThat(resultado.alcanzado()).isTrue();
        assertThat(resultado.nuevoEstado()).isEqualTo(EstadoServicio.SIN_SERVICIO);
        assertThat(resultado.reportesQueSustentan()).containsExactly(
                new ReporteId("r1"), new ReporteId("r2"), new ReporteId("r3"));
        verify(sectores).cambiarEstadoSiEs(SECTOR_ID, EstadoServicio.CON_SERVICIO, EstadoServicio.SIN_SERVICIO);
        verify(registrarEvento).registrar(any(EventoBitacora.class));
    }

    @Test
    void debeAnexarElEventoConElTipoDeConsensoCiudadano() {
        Sector sector = new Sector(SECTOR_ID, "BOCAGRANDE", 12000, EstadoServicio.CON_SERVICIO);
        given(sectores.buscarPorId(SECTOR_ID)).willReturn(Optional.of(sector));
        given(contadorReportes.contarRecientes(any(), any())).willReturn(3L);
        conVotos(Map.of(TipoReporte.SIN_AGUA, 3L));
        given(estrategia.seAlcanzaConsenso(3L, sector)).willReturn(true);
        given(reportes.listarRecientesPorSector(any(), any())).willReturn(List.of(
                reporte("r1", TipoReporte.SIN_AGUA), reporte("r2", TipoReporte.SIN_AGUA),
                reporte("r3", TipoReporte.SIN_AGUA)));

        servicio.evaluar(SECTOR_ID);

        var captor = org.mockito.ArgumentCaptor.forClass(EventoBitacora.class);
        verify(registrarEvento).registrar(captor.capture());
        assertThat(captor.getValue().tipo()).isEqualTo(TipoEvento.CORTE_CONFIRMADO_POR_CIUDADANOS);
        assertThat(captor.getValue().sectorId()).isEqualTo(SECTOR_ID);
    }

    @Test
    void debeDevolverNoAlcanzadoSiLaEstrategiaLoRechaza() {
        Sector sector = new Sector(SECTOR_ID, "BOCAGRANDE", 12000, EstadoServicio.CON_SERVICIO);
        given(sectores.buscarPorId(SECTOR_ID)).willReturn(Optional.of(sector));
        given(contadorReportes.contarRecientes(any(), any())).willReturn(1L);
        conVotos(Map.of(TipoReporte.SIN_AGUA, 1L));
        given(estrategia.seAlcanzaConsenso(1L, sector)).willReturn(false);

        ResultadoConsenso resultado = servicio.evaluar(SECTOR_ID);

        assertThat(resultado.alcanzado()).isFalse();
        assertThat(resultado.nuevoEstado()).isNull();
        verify(sectores, never()).cambiarEstadoSiEs(any(), any(), any());
        verify(registrarEvento, never()).registrar(any());
    }

    @Test
    void noDebeDuplicarElEventoSiElEstadoNoCambia() {
        Sector sector = new Sector(SECTOR_ID, "BOCAGRANDE", 12000, EstadoServicio.SIN_SERVICIO);
        given(sectores.buscarPorId(SECTOR_ID)).willReturn(Optional.of(sector));
        given(contadorReportes.contarRecientes(any(), any())).willReturn(3L);
        conVotos(Map.of(TipoReporte.SIN_AGUA, 3L));
        given(estrategia.seAlcanzaConsenso(3L, sector)).willReturn(true);
        given(reportes.listarRecientesPorSector(any(), any())).willReturn(List.of(
                reporte("r1", TipoReporte.SIN_AGUA), reporte("r2", TipoReporte.SIN_AGUA),
                reporte("r3", TipoReporte.SIN_AGUA)));

        ResultadoConsenso resultado = servicio.evaluar(SECTOR_ID);

        assertThat(resultado.alcanzado()).isFalse();
        verify(sectores, never()).cambiarEstadoSiEs(any(), any(), any());
        verify(registrarEvento, never()).registrar(any());
    }

    @Test
    void debeElegirElEstadoPorMayoriaDeTiposDeReporte() {
        Sector sector = new Sector(SECTOR_ID, "BOCAGRANDE", 12000, EstadoServicio.CON_SERVICIO);
        given(sectores.buscarPorId(SECTOR_ID)).willReturn(Optional.of(sector));
        given(contadorReportes.contarRecientes(any(), any())).willReturn(3L);
        conVotos(Map.of(TipoReporte.PRESION_BAJA, 1L, TipoReporte.SIN_AGUA, 2L));
        given(estrategia.seAlcanzaConsenso(3L, sector)).willReturn(true);
        given(reportes.listarRecientesPorSector(any(), any())).willReturn(List.of(
                reporte("r1", TipoReporte.PRESION_BAJA),
                reporte("r2", TipoReporte.SIN_AGUA),
                reporte("r3", TipoReporte.SIN_AGUA)));

        ResultadoConsenso resultado = servicio.evaluar(SECTOR_ID);

        assertThat(resultado.nuevoEstado()).isEqualTo(EstadoServicio.SIN_SERVICIO);
    }

    @Test
    void debeMantenerElEstadoActualSiHayEmpateEntreTiposDeReporte() {
        Sector sector = new Sector(SECTOR_ID, "BOCAGRANDE", 12000, EstadoServicio.CON_SERVICIO);
        given(sectores.buscarPorId(SECTOR_ID)).willReturn(Optional.of(sector));
        given(contadorReportes.contarRecientes(any(), any())).willReturn(2L);
        conVotos(Map.of(TipoReporte.SIN_AGUA, 1L, TipoReporte.SERVICIO_RESTABLECIDO, 1L));
        given(estrategia.seAlcanzaConsenso(2L, sector)).willReturn(true);
        given(reportes.listarRecientesPorSector(any(), any())).willReturn(List.of(
                reporte("r1", TipoReporte.SIN_AGUA), reporte("r2", TipoReporte.SERVICIO_RESTABLECIDO)));

        ResultadoConsenso resultado = servicio.evaluar(SECTOR_ID);

        assertThat(resultado.alcanzado()).isFalse();
        verify(sectores, never()).cambiarEstadoSiEs(any(), any(), any());
        verify(registrarEvento, never()).registrar(any());
    }

    @Test
    void unDispositivoConVariosReportesNoDebeContarComoVariosVecinos() {
        Sector sector = new Sector(SECTOR_ID, "BOCAGRANDE", null, EstadoServicio.CON_SERVICIO);
        given(sectores.buscarPorId(SECTOR_ID)).willReturn(Optional.of(sector));
        given(contadorReportes.contarRecientes(any(), any())).willReturn(3L);
        conVotos(Map.of(TipoReporte.SIN_AGUA, 3L));
        given(estrategia.seAlcanzaConsenso(3L, sector)).willReturn(true);
        given(estrategia.seAlcanzaConsenso(1L, sector)).willReturn(false);
        given(reportes.listarRecientesPorSector(any(), any())).willReturn(List.of(
                reporte("r1", "misma-huella", TipoReporte.SIN_AGUA, AHORA.minusSeconds(20)),
                reporte("r2", "misma-huella", TipoReporte.SIN_AGUA, AHORA.minusSeconds(10)),
                reporte("r3", "misma-huella", TipoReporte.SIN_AGUA, AHORA)));

        ResultadoConsenso resultado = servicio.evaluar(SECTOR_ID);

        assertThat(resultado.alcanzado()).isFalse();
        verify(sectores, never()).cambiarEstadoSiEs(any(), any(), any());
        verify(registrarEvento, never()).registrar(any());
    }

    /** Avería masiva: cada POST llega con el sector al umbral. Verificado hace un minuto, no hay nada que hacer. */
    @Test
    void noDebeCargarLosReportesDeLaVentanaSiElEstadoNoCambiaria() {
        Sector sector = new Sector(SECTOR_ID, "BOCAGRANDE", 12000, EstadoServicio.SIN_SERVICIO,
                AHORA.minusSeconds(3_600), AHORA.minusSeconds(60));
        given(sectores.buscarPorId(SECTOR_ID)).willReturn(Optional.of(sector));
        given(contadorReportes.contarRecientes(any(), any())).willReturn(5000L);
        given(estrategia.seAlcanzaConsenso(5000L, sector)).willReturn(true);
        conVotos(Map.of(TipoReporte.SIN_AGUA, 5000L));

        ResultadoConsenso resultado = servicio.evaluar(SECTOR_ID);

        assertThat(resultado.alcanzado()).isFalse();
        verify(reportes, never()).listarRecientesPorSector(any(), any());
    }

    private Sector verificadoHaceUnDia(EstadoServicio estado) {
        return new Sector(SECTOR_ID, "BOCAGRANDE", 12000, estado, AHORA.minusSeconds(86_400));
    }

    @Test
    void vecinosQueSostienenElEstadoActualDebenRenovarSuVerificacionSinCambiarlo() {
        Sector sector = verificadoHaceUnDia(EstadoServicio.CON_SERVICIO);
        given(sectores.buscarPorId(SECTOR_ID)).willReturn(Optional.of(sector));
        given(contadorReportes.contarRecientes(any(), any())).willReturn(3L);
        given(estrategia.seAlcanzaConsenso(3L, sector)).willReturn(true);
        conVotos(Map.of(TipoReporte.SERVICIO_RESTABLECIDO, 3L));
        given(reportes.listarRecientesPorSector(any(), any())).willReturn(List.of(
                reporte("r1", TipoReporte.SERVICIO_RESTABLECIDO), reporte("r2", TipoReporte.SERVICIO_RESTABLECIDO),
                reporte("r3", TipoReporte.SERVICIO_RESTABLECIDO)));

        ResultadoConsenso resultado = servicio.evaluar(SECTOR_ID);

        assertThat(resultado.alcanzado()).isFalse();
        verify(sectores).confirmarEstado(SECTOR_ID, EstadoServicio.CON_SERVICIO);
        verify(sectores, never()).cambiarEstadoSiEs(any(), any(), any());
        verify(registrarEvento, never()).registrar(any());
    }

    @Test
    void noDebeVerificarDeNuevoSiSeVerificoHacePocosMinutos() {
        Sector sector = new Sector(SECTOR_ID, "BOCAGRANDE", 12000, EstadoServicio.CON_SERVICIO,
                AHORA.minusSeconds(86_400), AHORA.minusSeconds(120));
        given(sectores.buscarPorId(SECTOR_ID)).willReturn(Optional.of(sector));
        given(contadorReportes.contarRecientes(any(), any())).willReturn(3L);
        given(estrategia.seAlcanzaConsenso(3L, sector)).willReturn(true);
        conVotos(Map.of(TipoReporte.SERVICIO_RESTABLECIDO, 3L));

        servicio.evaluar(SECTOR_ID);

        verify(sectores, never()).confirmarEstado(any(), any());
        verify(reportes, never()).listarRecientesPorSector(any(), any());
    }

    @Test
    void unEmpateNoDebeContarComoVerificacionDelEstadoActual() {
        Sector sector = verificadoHaceUnDia(EstadoServicio.CON_SERVICIO);
        given(sectores.buscarPorId(SECTOR_ID)).willReturn(Optional.of(sector));
        given(contadorReportes.contarRecientes(any(), any())).willReturn(2L);
        given(estrategia.seAlcanzaConsenso(2L, sector)).willReturn(true);
        conVotos(Map.of(TipoReporte.SIN_AGUA, 1L, TipoReporte.SERVICIO_RESTABLECIDO, 1L));

        servicio.evaluar(SECTOR_ID);

        verify(sectores, never()).confirmarEstado(any(), any());
    }

    @Test
    void unSoloDispositivoRepetidoNoDebeVerificarElEstado() {
        Sector sector = verificadoHaceUnDia(EstadoServicio.SIN_SERVICIO);
        given(sectores.buscarPorId(SECTOR_ID)).willReturn(Optional.of(sector));
        given(contadorReportes.contarRecientes(any(), any())).willReturn(3L);
        given(estrategia.seAlcanzaConsenso(3L, sector)).willReturn(true);
        given(estrategia.seAlcanzaConsenso(1L, sector)).willReturn(false);
        conVotos(Map.of(TipoReporte.SIN_AGUA, 3L));
        given(reportes.listarRecientesPorSector(any(), any())).willReturn(List.of(
                reporte("r1", "misma-huella", TipoReporte.SIN_AGUA, AHORA.minusSeconds(20)),
                reporte("r2", "misma-huella", TipoReporte.SIN_AGUA, AHORA.minusSeconds(10)),
                reporte("r3", "misma-huella", TipoReporte.SIN_AGUA, AHORA)));

        servicio.evaluar(SECTOR_ID);

        verify(sectores, never()).confirmarEstado(any(), any());
    }

    @Test
    void unSectorSinEstadoNoDebeVerificarseSoloPorQueLosVotosEmpatan() {
        Sector sector = new Sector(SECTOR_ID, "BOCAGRANDE", 12000, null);
        given(sectores.buscarPorId(SECTOR_ID)).willReturn(Optional.of(sector));
        given(contadorReportes.contarRecientes(any(), any())).willReturn(2L);
        given(estrategia.seAlcanzaConsenso(2L, sector)).willReturn(true);
        conVotos(Map.of(TipoReporte.SIN_AGUA, 1L, TipoReporte.SERVICIO_RESTABLECIDO, 1L));

        servicio.evaluar(SECTOR_ID);

        verify(sectores, never()).confirmarEstado(any(), any());
    }

    @Test
    void noDebeCargarLosReportesDeLaVentanaSiLosVotosNoAlcanzanConsenso() {
        Sector sector = new Sector(SECTOR_ID, "BOCAGRANDE", 12000, EstadoServicio.CON_SERVICIO);
        given(sectores.buscarPorId(SECTOR_ID)).willReturn(Optional.of(sector));
        given(contadorReportes.contarRecientes(any(), any())).willReturn(3L);
        given(estrategia.seAlcanzaConsenso(3L, sector)).willReturn(true);
        given(estrategia.seAlcanzaConsenso(1L, sector)).willReturn(false);
        conVotos(Map.of(TipoReporte.SIN_AGUA, 1L));

        ResultadoConsenso resultado = servicio.evaluar(SECTOR_ID);

        assertThat(resultado.alcanzado()).isFalse();
        verify(reportes, never()).listarRecientesPorSector(any(), any());
    }

    @Test
    void noDebeCargarLosReportesDeLaVentanaSiHayEmpateEntreTipos() {
        Sector sector = new Sector(SECTOR_ID, "BOCAGRANDE", 12000, EstadoServicio.CON_SERVICIO);
        given(sectores.buscarPorId(SECTOR_ID)).willReturn(Optional.of(sector));
        given(contadorReportes.contarRecientes(any(), any())).willReturn(2L);
        given(estrategia.seAlcanzaConsenso(2L, sector)).willReturn(true);
        conVotos(Map.of(TipoReporte.SIN_AGUA, 1L, TipoReporte.SERVICIO_RESTABLECIDO, 1L));

        servicio.evaluar(SECTOR_ID);

        verify(reportes, never()).listarRecientesPorSector(any(), any());
    }

    @Test
    void debeCargarLosReportesDeSustentoUnaSolaVezCuandoElEstadoCambia() {
        Sector sector = new Sector(SECTOR_ID, "BOCAGRANDE", 12000, EstadoServicio.CON_SERVICIO);
        given(sectores.buscarPorId(SECTOR_ID)).willReturn(Optional.of(sector));
        given(contadorReportes.contarRecientes(any(), any())).willReturn(3L);
        given(estrategia.seAlcanzaConsenso(3L, sector)).willReturn(true);
        conVotos(Map.of(TipoReporte.SIN_AGUA, 3L));
        given(reportes.listarRecientesPorSector(any(), any())).willReturn(List.of(
                reporte("r1", TipoReporte.SIN_AGUA), reporte("r2", TipoReporte.SIN_AGUA),
                reporte("r3", TipoReporte.SIN_AGUA)));

        servicio.evaluar(SECTOR_ID);

        verify(reportes, times(1)).listarRecientesPorSector(any(), any());
    }

    @Test
    void siOtraPeticionYaTieneLaReservaDebeDejarElSectorPendienteSinConsultarNada() {
        given(reserva.reservar(SECTOR_ID)).willReturn(false);

        ResultadoConsenso resultado = servicio.evaluar(SECTOR_ID);

        assertThat(resultado.alcanzado()).isFalse();
        verify(reserva).dejarPendiente(SECTOR_ID);
        verify(sectores, never()).buscarPorId(any());
        verify(reportes, never()).contarVotosRecientes(any(), any());
    }

    @Test
    void evaluarPendientesDebeEvaluarCadaSectorSinPedirLaReserva() {
        SectorId manga = new SectorId("manga");
        given(reserva.tomarPendientes()).willReturn(List.of(SECTOR_ID, manga));
        Sector bocagrande = new Sector(SECTOR_ID, "BOCAGRANDE", 12000, EstadoServicio.CON_SERVICIO);
        Sector sectorManga = new Sector(manga, "MANGA", 12000, EstadoServicio.CON_SERVICIO);
        given(sectores.buscarPorId(SECTOR_ID)).willReturn(Optional.of(bocagrande));
        given(sectores.buscarPorId(manga)).willReturn(Optional.of(sectorManga));
        given(contadorReportes.contarRecientes(any(), any())).willReturn(1L);

        servicio.evaluarPendientes();

        verify(sectores).buscarPorId(SECTOR_ID);
        verify(sectores).buscarPorId(manga);
        verify(reserva, never()).reservar(any());
    }

    @Test
    void evaluarPendientesDebeSeguirConLosDemasSectoresSiUnoFalla() {
        SectorId manga = new SectorId("manga");
        given(reserva.tomarPendientes()).willReturn(List.of(SECTOR_ID, manga));
        given(sectores.buscarPorId(SECTOR_ID)).willThrow(new IllegalStateException("Mongo caído"));
        given(sectores.buscarPorId(manga)).willReturn(Optional.of(
                new Sector(manga, "MANGA", 12000, EstadoServicio.CON_SERVICIO)));
        given(contadorReportes.contarRecientes(any(), any())).willReturn(1L);

        servicio.evaluarPendientes();

        verify(sectores).buscarPorId(manga);
        verify(reserva).dejarPendiente(SECTOR_ID);
    }

    @Test
    void debeCambiarElEstadoYRegistrarElEventoDentroDeUnaSolaTransaccion() {
        Sector sector = new Sector(SECTOR_ID, "BOCAGRANDE", 12000, EstadoServicio.CON_SERVICIO);
        given(sectores.buscarPorId(SECTOR_ID)).willReturn(Optional.of(sector));
        given(contadorReportes.contarRecientes(any(), any())).willReturn(3L);
        conVotos(Map.of(TipoReporte.SIN_AGUA, 3L));
        given(estrategia.seAlcanzaConsenso(3L, sector)).willReturn(true);
        given(reportes.listarRecientesPorSector(any(), any())).willReturn(List.of(
                reporte("r1", TipoReporte.SIN_AGUA), reporte("r2", TipoReporte.SIN_AGUA), reporte("r3", TipoReporte.SIN_AGUA)));

        servicio.evaluar(SECTOR_ID);

        verify(transaccion).ejecutar(any());
    }

    /**
     * Si el registro del evento falla a mitad de la transacción, `evaluar` debe dejar que la
     * excepción suba en vez de atraparla: es lo que permite a `TransaccionMongoAdapter` revertir
     * también el cambio de estado ya hecho por `cambiarEstadoSiEs` (RF028 — nunca un estado sin su
     * evento que lo sustente en la bitácora).
     */
    @Test
    void debePropagarLaFallaSiElRegistroDeEventoFallaParaQueLaTransaccionRevierta() {
        Sector sector = new Sector(SECTOR_ID, "BOCAGRANDE", 12000, EstadoServicio.CON_SERVICIO);
        given(sectores.buscarPorId(SECTOR_ID)).willReturn(Optional.of(sector));
        given(contadorReportes.contarRecientes(any(), any())).willReturn(3L);
        conVotos(Map.of(TipoReporte.SIN_AGUA, 3L));
        given(estrategia.seAlcanzaConsenso(3L, sector)).willReturn(true);
        given(reportes.listarRecientesPorSector(any(), any())).willReturn(List.of(
                reporte("r1", TipoReporte.SIN_AGUA), reporte("r2", TipoReporte.SIN_AGUA), reporte("r3", TipoReporte.SIN_AGUA)));
        org.mockito.Mockito.doThrow(new IllegalStateException("Mongo caído al anexar el evento"))
                .when(registrarEvento).registrar(any());

        assertThatThrownBy(() -> servicio.evaluar(SECTOR_ID)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void debeRechazarSiElSectorNoExiste() {
        given(sectores.buscarPorId(new SectorId("no-existe"))).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.evaluar(new SectorId("no-existe")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
