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
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

class ActualizarEstadosPorVentanaServiceTest {

    private static final SectorId MANGA = new SectorId("manga");
    private static final Instant INICIO = Instant.parse("2026-08-21T14:00:00Z");
    private static final Instant FIN = Instant.parse("2026-08-21T23:00:00Z");

    private PropuestaIngestaRepository propuestas;
    private SectorRepository sectores;
    private RegistrarEventoBitacoraUseCase registrarEvento;
    private CorteAguaRepository cortes;
    private RelojPort reloj;
    private TransaccionPort transaccion;
    private ActualizarEstadosPorVentanaService servicio;

    @BeforeEach
    void montar() {
        propuestas = mock(PropuestaIngestaRepository.class);
        sectores = mock(SectorRepository.class);
        registrarEvento = mock(RegistrarEventoBitacoraUseCase.class);
        cortes = mock(CorteAguaRepository.class);
        reloj = mock(RelojPort.class);
        transaccion = spy(new TransaccionPasoDirecto());
        given(cortes.listarPorSectores(any())).willReturn(List.of());
        servicio = new ActualizarEstadosPorVentanaService(propuestas, sectores, registrarEvento, cortes, reloj, transaccion);
    }

    /** Ejecuta la acción directamente, sin Mongo real — la atomicidad real se prueba en TransaccionMongoAdapterIntegrationTest. */
    private static class TransaccionPasoDirecto implements TransaccionPort {
        @Override
        public <T> T ejecutar(java.util.function.Supplier<T> accion) {
            return accion.get();
        }
    }

    private void dadoQueHay(PropuestaIngesta propuesta, EstadoServicio estadoActualDelSector) {
        given(propuestas.listarAprobadasConVentanaVigente(any())).willReturn(List.of(propuesta));
        // listarTodos(), no buscarPorId(): el servicio resuelve todos los sectores de una sola vez
        // (una lectura cacheada) en vez de uno por propuesta.
        given(sectores.listarTodos())
                .willReturn(List.of(new Sector(MANGA, "MANGA", 1000, estadoActualDelSector)));
    }

    private static PropuestaIngesta propuestaAprobadaDeCorte() {
        return new PropuestaIngesta(
                new PropuestaId("p1"), MANGA, EstadoServicio.SIN_SERVICIO, "acuacar",
                "https://acuacar.com/2854", "cita", 0.85, INICIO.minusSeconds(3600),
                INICIO, FIN).aprobar();
    }

    @Test
    void antesDeQueEmpieceLaVentanaElBarrioDebeQuedarComoCorteProgramado() {
        given(reloj.ahora()).willReturn(INICIO.minusSeconds(1800));
        dadoQueHay(propuestaAprobadaDeCorte(), EstadoServicio.CON_SERVICIO);

        assertThat(servicio.aplicarVentanasVencidas()).isEqualTo(1);

        ArgumentCaptor<Sector> guardado = ArgumentCaptor.forClass(Sector.class);
        verify(sectores).guardar(guardado.capture());
        assertThat(guardado.getValue().estadoActual()).isEqualTo(EstadoServicio.CORTE_PROGRAMADO);
    }

    @Test
    void duranteLaVentanaElBarrioDebeQuedarSinServicio() {
        given(reloj.ahora()).willReturn(INICIO.plusSeconds(3600));
        dadoQueHay(propuestaAprobadaDeCorte(), EstadoServicio.CORTE_PROGRAMADO);

        assertThat(servicio.aplicarVentanasVencidas()).isEqualTo(1);

        ArgumentCaptor<Sector> guardado = ArgumentCaptor.forClass(Sector.class);
        verify(sectores).guardar(guardado.capture());
        assertThat(guardado.getValue().estadoActual()).isEqualTo(EstadoServicio.SIN_SERVICIO);
    }

    @Test
    void alTerminarLaVentanaElBarrioDebeVolverAConServicio() {
        given(reloj.ahora()).willReturn(FIN.plusSeconds(60));
        dadoQueHay(propuestaAprobadaDeCorte(), EstadoServicio.SIN_SERVICIO);

        assertThat(servicio.aplicarVentanasVencidas()).isEqualTo(1);

        ArgumentCaptor<Sector> guardado = ArgumentCaptor.forClass(Sector.class);
        verify(sectores).guardar(guardado.capture());
        assertThat(guardado.getValue().estadoActual()).isEqualTo(EstadoServicio.CON_SERVICIO);
    }

    /** Escribir en cada barrido dispararía correo, push y SSE por un cambio que no ocurrió. */
    @Test
    void noDebeEscribirCuandoElEstadoYaEsElQueCorresponde() {
        given(reloj.ahora()).willReturn(INICIO.plusSeconds(3600));
        dadoQueHay(propuestaAprobadaDeCorte(), EstadoServicio.SIN_SERVICIO);

        assertThat(servicio.aplicarVentanasVencidas()).isZero();

        verify(sectores, never()).guardar(any());
        verify(registrarEvento, never()).registrar(any());
    }

    @Test
    void debeAnexarElCambioALaBitacora() {
        given(reloj.ahora()).willReturn(FIN.plusSeconds(60));
        dadoQueHay(propuestaAprobadaDeCorte(), EstadoServicio.SIN_SERVICIO);

        servicio.aplicarVentanasVencidas();

        verify(registrarEvento).registrar(any());
    }

    /** El par estado+evento va en la misma transacción (Fase 3): si el evento falla, revierte el sector. */
    @Test
    void debeGuardarElSectorYRegistrarElEventoEnUnaSolaTransaccion() {
        given(reloj.ahora()).willReturn(FIN.plusSeconds(60));
        dadoQueHay(propuestaAprobadaDeCorte(), EstadoServicio.SIN_SERVICIO);

        servicio.aplicarVentanasVencidas();

        verify(transaccion).ejecutar(any());
    }

    @Test
    void debePropagarLaFallaSiElRegistroDeEventoFallaParaQueLaTransaccionRevierta() {
        given(reloj.ahora()).willReturn(FIN.plusSeconds(60));
        dadoQueHay(propuestaAprobadaDeCorte(), EstadoServicio.SIN_SERVICIO);
        org.mockito.Mockito.doThrow(new IllegalStateException("Mongo caído al anexar el evento"))
                .when(registrarEvento).registrar(any());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> servicio.aplicarVentanasVencidas())
                .isInstanceOf(IllegalStateException.class);
    }

    /**
     * Un sector saneado por un corte oficial abierto (sin propuesta de ingesta que lo sustente, ver
     * el javadoc de `aplicarVentanasVencidas`) no anexa evento: su escritura queda sola, ya es
     * atómica de por sí y no necesita la transacción.
     */
    @Test
    void noDebeAbrirUnaTransaccionCuandoElSectorSeSaneaSinSustento() {
        given(reloj.ahora()).willReturn(FIN.plusSeconds(60));
        given(sectores.listarTodos())
                .willReturn(List.of(new Sector(MANGA, "MANGA", 1000, EstadoServicio.CON_SERVICIO)));
        PropuestaIngesta boletinYaTerminado = propuestaAprobadaDeCorte();
        given(propuestas.listarAprobadasConVentanaVigente(any())).willReturn(List.of(boletinYaTerminado));
        CorteAgua corteOficialAbierto = CorteAgua.builder()
                .id(new CorteId("c-oficial"))
                .sectoresAfectados(List.of(MANGA))
                .inicio(INICIO.minusSeconds(3600))
                .finPrometido(FIN.plusSeconds(10 * 3600))
                .causa("corte oficial, veedor")
                .origen(OrigenCorte.VEEDOR)
                .estado(EstadoCorte.CONFIRMADO)
                .build();
        given(cortes.listarPorSectores(List.of(MANGA))).willReturn(List.of(corteOficialAbierto));

        servicio.aplicarVentanasVencidas();

        verify(sectores).guardar(any());
        verify(registrarEvento, never()).registrar(any());
        verify(transaccion, never()).ejecutar(any());
    }

    @Test
    void noDebeTocarUnSectorQueYaNoExiste() {
        given(reloj.ahora()).willReturn(FIN.plusSeconds(60));
        given(propuestas.listarAprobadasConVentanaVigente(any()))
                .willReturn(List.of(propuestaAprobadaDeCorte()));
        given(sectores.listarTodos()).willReturn(List.of());

        assertThat(servicio.aplicarVentanasVencidas()).isZero();
        verify(sectores, never()).guardar(any());
    }

    /**
     * Dos boletines aprobados pueden solaparse sobre el mismo barrio (uno extiende el corte que el
     * otro ya había anunciado). El resultado no puede depender de en qué orden los devuelva Mongo:
     * debe ganar siempre el más severo, sin importar el orden de la lista.
     */
    @Test
    void avisosSolapadosDelMismoSectorDebenDarElMismoResultadoEnCualquierOrden() {
        given(reloj.ahora()).willReturn(FIN.plusSeconds(60));
        // Boletín original: terminó hace un minuto según su propia ventana -> vigente hoy = CON_SERVICIO.
        PropuestaIngesta boletinYaTerminado = propuestaAprobadaDeCorte();
        // Segundo boletín, mismo barrio, extiende el corte más allá de "ahora" -> vigente hoy = SIN_SERVICIO.
        PropuestaIngesta boletinQueLoExtiende = new PropuestaIngesta(
                new PropuestaId("p2"), MANGA, EstadoServicio.SIN_SERVICIO, "acuacar",
                "https://acuacar.com/2900", "se extiende el corte", 0.9, INICIO,
                INICIO, FIN.plusSeconds(3 * 3600)).aprobar();

        given(sectores.listarTodos())
                .willReturn(List.of(new Sector(MANGA, "MANGA", 1000, EstadoServicio.CON_SERVICIO)));

        given(propuestas.listarAprobadasConVentanaVigente(any()))
                .willReturn(List.of(boletinYaTerminado, boletinQueLoExtiende));
        EstadoServicio primerOrden = aplicarYCapturarEstado();

        given(propuestas.listarAprobadasConVentanaVigente(any()))
                .willReturn(List.of(boletinQueLoExtiende, boletinYaTerminado));
        EstadoServicio segundoOrden = aplicarYCapturarEstado();

        assertThat(primerOrden).isEqualTo(EstadoServicio.SIN_SERVICIO);
        assertThat(segundoOrden).isEqualTo(EstadoServicio.SIN_SERVICIO);
    }

    private EstadoServicio aplicarYCapturarEstado() {
        servicio.aplicarVentanasVencidas();
        ArgumentCaptor<Sector> guardado = ArgumentCaptor.forClass(Sector.class);
        verify(sectores, org.mockito.Mockito.atLeastOnce()).guardar(guardado.capture());
        return guardado.getValue().estadoActual();
    }

    /**
     * Un corte oficial que el veedor registró y sigue abierto (RF016) es una señal más autorizada
     * que un boletín de ingesta cuya ventana ya venció: el barrido no debe rebajarlo a CON_SERVICIO.
     */
    @Test
    void unCorteOficialAbiertoDebePrevalecerSobreUnAvisoDeIngestaVencido() {
        given(reloj.ahora()).willReturn(FIN.plusSeconds(60));
        dadoQueHay(propuestaAprobadaDeCorte(), EstadoServicio.SIN_SERVICIO);

        CorteAgua corteOficialAbierto = CorteAgua.builder()
                .id(new CorteId("c-oficial"))
                .sectoresAfectados(List.of(MANGA))
                .inicio(INICIO.minusSeconds(3600))
                .finPrometido(FIN.plusSeconds(10 * 3600))
                .causa("corte oficial, veedor")
                .origen(OrigenCorte.VEEDOR)
                .estado(EstadoCorte.CONFIRMADO)
                .build();
        given(cortes.listarPorSectores(List.of(MANGA))).willReturn(List.of(corteOficialAbierto));

        assertThat(servicio.aplicarVentanasVencidas()).isZero();
        verify(sectores, never()).guardar(any());
    }
}
