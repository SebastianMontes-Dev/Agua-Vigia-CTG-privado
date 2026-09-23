package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.EstadoServicio;
import com.aguavigia.ctg.domain.EstrategiaConsenso;
import com.aguavigia.ctg.domain.HuellaDispositivo;
import com.aguavigia.ctg.domain.ReporteCiudadano;
import com.aguavigia.ctg.domain.ReporteId;
import com.aguavigia.ctg.domain.ResultadoConsenso;
import com.aguavigia.ctg.domain.Sector;
import com.aguavigia.ctg.domain.SectorId;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Dos reportes simultáneos del mismo sector leen el mismo estado, deciden el mismo cambio y los dos
 * lo escriben: sin control, la bitácora (solo anexado, RF028) recibía el mismo evento dos veces.
 */
class EvaluarConsensoServiceConcurrenciaTest {

    private static final Instant AHORA = Instant.parse("2026-08-08T15:30:00Z");
    private static final SectorId SECTOR_ID = new SectorId("bocagrande");

    private SectorRepository sectores;
    private ReporteCiudadanoRepository reportes;
    private ContadorReportesPort contadorReportes;
    private EstrategiaConsenso estrategia;
    private RegistrarEventoBitacoraUseCase registrarEvento;
    private EvaluarConsensoService servicio;
    private Sector sector;

    @BeforeEach
    void montar() {
        sectores = mock(SectorRepository.class);
        reportes = mock(ReporteCiudadanoRepository.class);
        contadorReportes = mock(ContadorReportesPort.class);
        estrategia = mock(EstrategiaConsenso.class);
        registrarEvento = mock(RegistrarEventoBitacoraUseCase.class);
        RelojPort reloj = () -> AHORA;
        ReservaDeEvaluacionPort reserva = mock(ReservaDeEvaluacionPort.class);
        given(reserva.reservar(any())).willReturn(true);
        TransaccionPort transaccion = new TransaccionPort() {
            @Override
            public <T> T ejecutar(java.util.function.Supplier<T> accion) {
                return accion.get();
            }
        };
        servicio = new EvaluarConsensoService(
                sectores, reportes, contadorReportes, reserva, estrategia, registrarEvento, reloj, transaccion, 30);

        sector = new Sector(SECTOR_ID, "BOCAGRANDE", 12000, EstadoServicio.CON_SERVICIO);
        given(sectores.buscarPorId(SECTOR_ID)).willReturn(Optional.of(sector));
        given(contadorReportes.contarRecientes(any(), any())).willReturn(3L);
        given(estrategia.seAlcanzaConsenso(3L, sector)).willReturn(true);
        given(reportes.contarVotosRecientes(any(), any())).willReturn(Map.of(TipoReporte.SIN_AGUA, 3L));
        given(reportes.listarRecientesPorSector(any(), any())).willReturn(List.of(
                reporte("r1"), reporte("r2"), reporte("r3")));
    }

    private static ReporteCiudadano reporte(String id) {
        return new ReporteCiudadano(new ReporteId(id), SECTOR_ID, TipoReporte.SIN_AGUA, null,
                new HuellaDispositivo("h-" + id), AHORA);
    }

    @Test
    void siOtraPeticionYaCambioElEstadoNoDebeAnexarUnEventoDuplicado() {
        given(sectores.cambiarEstadoSiEs(SECTOR_ID, EstadoServicio.CON_SERVICIO, EstadoServicio.SIN_SERVICIO))
                .willReturn(false);

        ResultadoConsenso resultado = servicio.evaluar(SECTOR_ID);

        assertThat(resultado.alcanzado()).isFalse();
        verify(registrarEvento, never()).registrar(any());
    }

    @Test
    void elGanadorDeLaCarreraSiDebeAnexarElEvento() {
        given(sectores.cambiarEstadoSiEs(SECTOR_ID, EstadoServicio.CON_SERVICIO, EstadoServicio.SIN_SERVICIO))
                .willReturn(true);

        ResultadoConsenso resultado = servicio.evaluar(SECTOR_ID);

        assertThat(resultado.alcanzado()).isTrue();
        verify(registrarEvento).registrar(any());
    }
}
