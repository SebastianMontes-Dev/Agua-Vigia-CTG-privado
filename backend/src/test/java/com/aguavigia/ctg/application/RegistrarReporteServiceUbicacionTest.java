package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.Coordenada;
import com.aguavigia.ctg.domain.EstadoServicio;
import com.aguavigia.ctg.domain.HuellaDispositivo;
import com.aguavigia.ctg.domain.ReporteCiudadano;
import com.aguavigia.ctg.domain.ResultadoConsenso;
import com.aguavigia.ctg.domain.Sector;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.TipoReporte;
import com.aguavigia.ctg.domain.port.in.EvaluarConsensoUseCase;
import com.aguavigia.ctg.domain.port.out.ContadorReportesPort;
import com.aguavigia.ctg.domain.port.out.RelojPort;
import com.aguavigia.ctg.domain.port.out.ReporteCiudadanoRepository;
import com.aguavigia.ctg.domain.port.out.SectorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** RF007 — el sector se infiere de la coordenada cuando el cliente no lo declara. */
class RegistrarReporteServiceUbicacionTest {

    private static final Instant AHORA = Instant.parse("2026-08-08T15:30:00Z");
    private static final HuellaDispositivo HUELLA = new HuellaDispositivo("hash-1");
    private static final Coordenada EN_BOCAGRANDE = new Coordenada(10.4012, -75.5560);
    private static final SectorId BOCAGRANDE = new SectorId("bocagrande");

    private SectorRepository sectores;
    private ReporteCiudadanoRepository reportes;
    private ContadorReportesPort contadorReportes;
    private EvaluarConsensoUseCase evaluarConsenso;
    private RegistrarReporteService servicio;

    @BeforeEach
    void montar() {
        sectores = mock(SectorRepository.class);
        reportes = mock(ReporteCiudadanoRepository.class);
        contadorReportes = mock(ContadorReportesPort.class);
        evaluarConsenso = mock(EvaluarConsensoUseCase.class);
        RelojPort reloj = () -> AHORA;
        servicio = new RegistrarReporteService(sectores, reportes, contadorReportes, evaluarConsenso, reloj,
                3, 30, 30);

        given(reportes.guardar(any(ReporteCiudadano.class))).willAnswer(i -> i.getArgument(0));
        given(reportes.contarRecientesPorSectorYDispositivo(any(), any(), any())).willReturn(0L);
        given(contadorReportes.intentarReservarCupo(any(), any(), anyInt(), any())).willReturn(true);
        given(evaluarConsenso.evaluar(any())).willReturn(new ResultadoConsenso(BOCAGRANDE, false, null, List.of()));
    }

    @Test
    void debeInferirElSectorDeLaCoordenadaCuandoElClienteNoLoDeclara() {
        Sector bocagrande = new Sector(BOCAGRANDE, "BOCAGRANDE", 12000, EstadoServicio.SIN_SERVICIO);
        given(sectores.buscarPorCoordenada(EN_BOCAGRANDE)).willReturn(Optional.of(bocagrande));

        ReporteCiudadano reporte = servicio.registrar(null, TipoReporte.SIN_AGUA, EN_BOCAGRANDE, HUELLA, false);

        assertThat(reporte.sectorId()).isEqualTo(BOCAGRANDE);
        verify(contadorReportes).registrar(BOCAGRANDE, HUELLA);
        verify(evaluarConsenso).evaluar(BOCAGRANDE);
    }

    @Test
    void debeRechazarUnaCoordenadaQueNoCaeEnNingunSectorDeCartagena() {
        Coordenada enElMar = new Coordenada(10.20, -75.90);
        given(sectores.buscarPorCoordenada(enElMar)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.registrar(null, TipoReporte.SIN_AGUA, enElMar, HUELLA, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ningún sector");

        verify(reportes, never()).guardar(any());
        verify(evaluarConsenso, never()).evaluar(any());
    }

    @Test
    void debeRechazarUnReporteSinSectorNiCoordenada() {
        assertThatThrownBy(() -> servicio.registrar(null, TipoReporte.SIN_AGUA, null, HUELLA, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sector");

        verify(reportes, never()).guardar(any());
    }

    @Test
    void noDebeConsultarLaGeometriaSiElClienteYaDeclaraElSector() {
        Sector bocagrande = new Sector(BOCAGRANDE, "BOCAGRANDE", 12000, EstadoServicio.SIN_SERVICIO);
        given(sectores.buscarPorId(BOCAGRANDE)).willReturn(Optional.of(bocagrande));

        servicio.registrar(BOCAGRANDE, TipoReporte.SIN_AGUA, EN_BOCAGRANDE, HUELLA, false);

        verify(sectores, never()).buscarPorCoordenada(any());
    }
}
