package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.CorteAgua;
import com.aguavigia.ctg.domain.CorteId;
import com.aguavigia.ctg.domain.EstadoCorte;
import com.aguavigia.ctg.domain.EstadoServicio;
import com.aguavigia.ctg.domain.OrigenCorte;
import com.aguavigia.ctg.domain.Sector;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.port.in.RegistrarEventoBitacoraUseCase;
import com.aguavigia.ctg.domain.port.out.CorteAguaRepository;
import com.aguavigia.ctg.domain.port.out.RelojPort;
import com.aguavigia.ctg.domain.port.out.SectorRepository;
import com.aguavigia.ctg.domain.port.out.TransaccionPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Misma falla que {@code ActualizarEstadosPorVentanaServiceCortesAbiertosTest}, vista desde el
 * cierre del veedor: un corte de ingesta cuya ventana ya venció nunca se cierra (nadie fija su
 * {@code finReal}), así que bloqueaba para siempre el retorno a CON_SERVICIO cuando el veedor
 * cerraba otro corte del mismo barrio.
 */
class GestionarCorteOficialServiceCortesAbiertosTest {

    private static final SectorId MANGA = new SectorId("manga");
    private static final Instant INICIO = Instant.parse("2026-08-09T10:00:00Z");
    private static final Instant AHORA = Instant.parse("2026-08-10T20:00:00Z");

    private CorteAguaRepository cortes;
    private SectorRepository sectores;
    private GestionarCorteOficialService servicio;

    @BeforeEach
    void montar() {
        cortes = mock(CorteAguaRepository.class);
        sectores = mock(SectorRepository.class);
        RelojPort reloj = () -> AHORA;
        TransaccionPort pasoDirecto = new TransaccionPort() {
            @Override
            public <T> T ejecutar(java.util.function.Supplier<T> accion) {
                return accion.get();
            }
        };
        servicio = new GestionarCorteOficialService(
                cortes, sectores, mock(RegistrarEventoBitacoraUseCase.class), reloj, pasoDirecto);
        given(cortes.guardar(any(CorteAgua.class))).willAnswer(invocacion -> invocacion.getArgument(0));
    }

    private static CorteAgua corteDelVeedorAbierto() {
        return CorteAgua.builder()
                .id(new CorteId("corte-veedor"))
                .sectoresAfectados(List.of(MANGA))
                .inicio(AHORA.minus(5, ChronoUnit.HOURS))
                .finPrometido(AHORA.plus(1, ChronoUnit.HOURS))
                .causa("Mantenimiento planta El Bosque")
                .origen(OrigenCorte.VEEDOR)
                .estado(EstadoCorte.CONFIRMADO)
                .build();
    }

    private static CorteAgua corteDeIngestaConVentana(Instant inicio, Instant fin) {
        return CorteAgua.builder()
                .id(new CorteId("corte-del-boletin"))
                .sectoresAfectados(List.of(MANGA))
                .inicio(inicio)
                .finPrometido(fin)
                .causa("Habrá suspensión del servicio de acueducto")
                .origen(OrigenCorte.INGESTA_IA)
                .estado(EstadoCorte.ANUNCIADO)
                .build();
    }

    private void cerrandoElDelVeedorConOtroDeIngestaAbierto(CorteAgua deIngesta) {
        CorteAgua delVeedor = corteDelVeedorAbierto();
        given(cortes.buscarPorId(delVeedor.id())).willReturn(Optional.of(delVeedor));
        given(cortes.listarPorSectores(List.of(MANGA))).willReturn(List.of(delVeedor, deIngesta));
        given(sectores.listarTodos())
                .willReturn(List.of(new Sector(MANGA, "MANGA", 5000, EstadoServicio.SIN_SERVICIO)));

        servicio.cerrar(delVeedor.id(), AHORA.minus(10, ChronoUnit.MINUTES));
    }

    @Test
    void debeRestablecerElSectorSiElOtroCorteAbiertoEsDeIngestaYSuVentanaYaVencio() {
        // Boletín de ayer: terminó hace más de un día y su corte sigue «abierto» porque nadie lo cerró.
        cerrandoElDelVeedorConOtroDeIngestaAbierto(
                corteDeIngestaConVentana(INICIO, INICIO.plus(9, ChronoUnit.HOURS)));

        verify(sectores).guardar(new Sector(MANGA, "MANGA", 5000, EstadoServicio.CON_SERVICIO));
    }

    /** Contrapeso: un boletín cuya ventana sigue corriendo sí sostiene el barrio sin servicio. */
    @Test
    void noDebeRestablecerElSectorSiElOtroCorteDeIngestaTodaviaEstaEnSuVentana() {
        cerrandoElDelVeedorConOtroDeIngestaAbierto(
                corteDeIngestaConVentana(AHORA.minus(2, ChronoUnit.HOURS), AHORA.plus(3, ChronoUnit.HOURS)));

        verify(sectores, never()).guardar(any());
    }
}
