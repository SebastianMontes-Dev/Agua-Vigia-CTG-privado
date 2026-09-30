package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.CorteAgua;
import com.aguavigia.ctg.domain.CorteId;
import com.aguavigia.ctg.domain.EstadoCorte;
import com.aguavigia.ctg.domain.EstadoServicio;
import com.aguavigia.ctg.domain.OrigenCorte;
import com.aguavigia.ctg.domain.PropuestaId;
import com.aguavigia.ctg.domain.PropuestaIngesta;
import com.aguavigia.ctg.domain.Sector;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.port.in.RegistrarEventoBitacoraUseCase;
import com.aguavigia.ctg.domain.port.out.CorteAguaRepository;
import com.aguavigia.ctg.domain.port.out.PropuestaIngestaRepository;
import com.aguavigia.ctg.domain.port.out.RelojPort;
import com.aguavigia.ctg.domain.port.out.SectorRepository;
import com.aguavigia.ctg.domain.port.out.TransaccionPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Caracteriza el hallazgo 2 del plan: aprobar un boletín con ventana deja un corte de ingesta
 * <b>sin {@code finReal}</b> (ver {@code RevisarPropuestaIngestaService.registrarCorteDelBoletin}),
 * y el barrido lo trataba como «corte oficial abierto» para siempre. El test existente de
 * {@code ActualizarEstadosPorVentanaServiceTest} lo ocultaba al mockear {@code listarPorSectores}
 * vacío; aquí el repositorio devuelve el corte tal como lo deja la aprobación.
 */
class ActualizarEstadosPorVentanaServiceCortesAbiertosTest {

    private static final SectorId MANGA = new SectorId("manga");
    private static final Instant INICIO = Instant.parse("2026-08-21T14:00:00Z");
    private static final Instant FIN = Instant.parse("2026-08-21T23:00:00Z");

    private PropuestaIngestaRepository propuestas;
    private SectorRepository sectores;
    private CorteAguaRepository cortes;
    private RelojPort reloj;
    private ActualizarEstadosPorVentanaService servicio;

    @BeforeEach
    void montar() {
        propuestas = mock(PropuestaIngestaRepository.class);
        sectores = mock(SectorRepository.class);
        cortes = mock(CorteAguaRepository.class);
        reloj = mock(RelojPort.class);
        TransaccionPort pasoDirecto = new TransaccionPort() {
            @Override
            public <T> T ejecutar(java.util.function.Supplier<T> accion) {
                return accion.get();
            }
        };
        servicio = new ActualizarEstadosPorVentanaService(
                propuestas, sectores, mock(RegistrarEventoBitacoraUseCase.class), cortes, reloj, pasoDirecto);
    }

    /** El corte exacto que crea la aprobación del boletín: origen INGESTA_IA, ANUNCIADO, sin finReal. */
    private static CorteAgua corteQueDejaLaAprobacionDelBoletin() {
        return CorteAgua.builder()
                .id(new CorteId("corte-del-boletin"))
                .sectoresAfectados(List.of(MANGA))
                .inicio(INICIO)
                .finPrometido(FIN)
                .causa("Habrá suspensión del servicio de acueducto")
                .origen(OrigenCorte.INGESTA_IA)
                .estado(EstadoCorte.ANUNCIADO)
                .build();
    }

    private void dadoUnBoletinAprobadoConSuCorteAbierto(EstadoServicio estadoDelSector) {
        PropuestaIngesta boletin = new PropuestaIngesta(
                new PropuestaId("p1"), MANGA, EstadoServicio.SIN_SERVICIO, "acuacar",
                "https://acuacar.com/2854", "cita", 0.85, INICIO.minusSeconds(3600),
                INICIO, FIN).aprobar();
        given(propuestas.listarAprobadasConVentanaVigente(any())).willReturn(List.of(boletin));
        given(sectores.listarTodos()).willReturn(List.of(new Sector(MANGA, "MANGA", 1000, estadoDelSector)));
        given(cortes.listarPorSectores(List.of(MANGA))).willReturn(List.of(corteQueDejaLaAprobacionDelBoletin()));
    }

    @Test
    void debeVolverAConServicioAlTerminarLaVentanaAunqueElCorteDelBoletinSigaSinFinReal() {
        given(reloj.ahora()).willReturn(FIN.plusSeconds(60));
        dadoUnBoletinAprobadoConSuCorteAbierto(EstadoServicio.SIN_SERVICIO);

        assertThat(servicio.aplicarVentanasVencidas()).isEqualTo(1);

        ArgumentCaptor<Sector> guardado = ArgumentCaptor.forClass(Sector.class);
        verify(sectores).guardar(guardado.capture());
        assertThat(guardado.getValue().estadoActual()).isEqualTo(EstadoServicio.CON_SERVICIO);
    }

    /** Contrapeso: el arreglo no puede soltar el barrio mientras la ventana del boletín sigue corriendo. */
    @Test
    void debeSeguirSinServicioMientrasLaVentanaDelBoletinEstaEnCurso() {
        given(reloj.ahora()).willReturn(INICIO.plusSeconds(3600));
        dadoUnBoletinAprobadoConSuCorteAbierto(EstadoServicio.SIN_SERVICIO);

        assertThat(servicio.aplicarVentanasVencidas()).isZero();

        verify(sectores, never()).guardar(any());
    }
}
