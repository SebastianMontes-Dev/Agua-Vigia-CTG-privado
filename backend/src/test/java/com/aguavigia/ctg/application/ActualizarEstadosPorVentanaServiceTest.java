package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.CorteAgua;
import com.aguavigia.ctg.domain.CorteId;
import com.aguavigia.ctg.domain.EstadoPublicado;
import com.aguavigia.ctg.domain.EstadoServicio;
import com.aguavigia.ctg.domain.OrigenCorte;
import com.aguavigia.ctg.domain.PropuestaId;
import com.aguavigia.ctg.domain.PropuestaIngesta;
import com.aguavigia.ctg.domain.ResultadoDeRecalculo;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.port.in.RecalcularSectorUseCase;
import com.aguavigia.ctg.domain.port.out.CorteAguaRepository;
import com.aguavigia.ctg.domain.port.out.PropuestaIngestaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * El barrido ya no decide nada: solo elige qué barrios tienen una ventana o un corte que el paso del
 * tiempo puede haber movido y le pide a {@link RecalcularSectorUseCase} que los recalcule. Qué estado
 * corresponde en cada caso está en la tabla de verdad del resolutor y en {@code RecalcularSectorServiceTest}.
 */
class ActualizarEstadosPorVentanaServiceTest {

    private static final SectorId MANGA = new SectorId("manga");
    private static final SectorId BOCAGRANDE = new SectorId("bocagrande");
    private static final SectorId CRESPO = new SectorId("crespo");
    private static final Instant AHORA = Instant.parse("2026-08-21T15:00:00Z");
    private static final Duration MEMORIA = Duration.ofHours(72);

    private PropuestaIngestaRepository propuestas;
    private CorteAguaRepository cortes;
    private RecalcularSectorUseCase recalcular;
    private ActualizarEstadosPorVentanaService servicio;

    @BeforeEach
    void montar() {
        propuestas = mock(PropuestaIngestaRepository.class);
        cortes = mock(CorteAguaRepository.class);
        recalcular = mock(RecalcularSectorUseCase.class);
        given(propuestas.listarAprobadasConVentanaVigente(any())).willReturn(List.of());
        given(cortes.listarAbiertos()).willReturn(List.of());
        given(recalcular.recalcular(any())).willReturn(sinCambio());
        servicio = new ActualizarEstadosPorVentanaService(propuestas, cortes, recalcular, () -> AHORA, MEMORIA);
    }

    private static ResultadoDeRecalculo sinCambio() {
        return ResultadoDeRecalculo.sinCambio(EstadoPublicado.sinDatos());
    }

    private static ResultadoDeRecalculo conCambio() {
        return new ResultadoDeRecalculo(EstadoPublicado.sinDatos(), true, List.of());
    }

    private static PropuestaIngesta boletinAprobado(SectorId sector) {
        return new PropuestaIngesta(new PropuestaId("p-" + sector.valor()), sector, EstadoServicio.SIN_SERVICIO,
                "acuacar", "https://acuacar.com/2854", "cita", 0.85, AHORA.minusSeconds(7200),
                AHORA.minusSeconds(3600), AHORA.plusSeconds(3600)).aprobar();
    }

    private static CorteAgua corteAbierto(SectorId... sectores) {
        return CorteAgua.builder()
                .id(new CorteId("corte-" + sectores[0].valor()))
                .sectoresAfectados(List.of(sectores))
                .inicio(AHORA.minusSeconds(3600))
                .finPrometido(AHORA.plusSeconds(3600))
                .causa("Mantenimiento")
                .origen(OrigenCorte.VEEDOR)
                .build();
    }

    @Test
    void recalculaLosBarriosDeLosBoletinesAprobadosConVentanaVigente() {
        given(propuestas.listarAprobadasConVentanaVigente(any()))
                .willReturn(List.of(boletinAprobado(MANGA), boletinAprobado(BOCAGRANDE)));

        servicio.aplicarVentanasVencidas();

        verify(recalcular).recalcular(MANGA);
        verify(recalcular).recalcular(BOCAGRANDE);
    }

    /** Una promesa vencida sigue pesando hasta que expira: el barrido mira hasta ese plazo, no un día. */
    @Test
    void miraLosBoletinesHastaElPlazoDeExpiracion() {
        servicio.aplicarVentanasVencidas();

        verify(propuestas).listarAprobadasConVentanaVigente(AHORA.minus(MEMORIA));
    }

    @Test
    void recalculaLosBarriosDeLosCortesAbiertos() {
        given(cortes.listarAbiertos()).willReturn(List.of(corteAbierto(CRESPO, MANGA)));

        servicio.aplicarVentanasVencidas();

        verify(recalcular).recalcular(CRESPO);
        verify(recalcular).recalcular(MANGA);
    }

    @Test
    void unBarrioEnAmbasFuentesSeRecalculaUnaSolaVez() {
        given(propuestas.listarAprobadasConVentanaVigente(any())).willReturn(List.of(boletinAprobado(MANGA)));
        given(cortes.listarAbiertos()).willReturn(List.of(corteAbierto(MANGA)));

        servicio.aplicarVentanasVencidas();

        verify(recalcular, times(1)).recalcular(MANGA);
    }

    @Test
    void devuelveCuantosBarriosCambiaronDeEstado() {
        given(propuestas.listarAprobadasConVentanaVigente(any()))
                .willReturn(List.of(boletinAprobado(MANGA), boletinAprobado(BOCAGRANDE)));
        given(recalcular.recalcular(MANGA)).willReturn(conCambio());

        assertThat(servicio.aplicarVentanasVencidas()).isEqualTo(1);
    }

    /** Un barrio que falla (por ejemplo, ya no existe) no puede dejar sin revisar a los demás. */
    @Test
    void unFalloEnUnBarrioNoDetieneLosDemas() {
        given(propuestas.listarAprobadasConVentanaVigente(any()))
                .willReturn(List.of(boletinAprobado(MANGA), boletinAprobado(BOCAGRANDE)));
        given(recalcular.recalcular(MANGA)).willThrow(new IllegalArgumentException("No existe el sector 'manga'"));
        given(recalcular.recalcular(BOCAGRANDE)).willReturn(conCambio());

        assertThat(servicio.aplicarVentanasVencidas()).isEqualTo(1);

        verify(recalcular).recalcular(BOCAGRANDE);
    }

    @Test
    void sinVentanasNiCortesAbiertosNoRecalculaNada() {
        assertThat(servicio.aplicarVentanasVencidas()).isZero();

        verify(recalcular, never()).recalcular(any());
    }
}
