package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.CorteAgua;
import com.aguavigia.ctg.domain.CorteId;
import com.aguavigia.ctg.domain.EstadoCorte;
import com.aguavigia.ctg.domain.EstadoServicio;
import com.aguavigia.ctg.domain.EventoBitacora;
import com.aguavigia.ctg.domain.OrigenCorte;
import com.aguavigia.ctg.domain.Sector;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.TipoEvento;
import com.aguavigia.ctg.domain.port.in.RegistrarEventoBitacoraUseCase;
import com.aguavigia.ctg.domain.port.out.CorteAguaRepository;
import com.aguavigia.ctg.domain.port.out.RelojPort;
import com.aguavigia.ctg.domain.port.out.SectorRepository;
import com.aguavigia.ctg.domain.port.out.TransaccionPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

class GestionarCorteOficialServiceTest {

    private static final Instant INICIO = Instant.parse("2026-08-09T10:00:00Z");
    private static final Instant AHORA = Instant.parse("2026-08-09T20:00:00Z");

    private CorteAguaRepository cortes;
    private SectorRepository sectores;
    private RegistrarEventoBitacoraUseCase registrarEvento;
    private RelojPort reloj;
    private TransaccionPort transaccion;
    private GestionarCorteOficialService servicio;

    @BeforeEach
    void montar() {
        cortes = mock(CorteAguaRepository.class);
        sectores = mock(SectorRepository.class);
        registrarEvento = mock(RegistrarEventoBitacoraUseCase.class);
        reloj = () -> AHORA;
        transaccion = spy(new TransaccionPasoDirecto());
        servicio = new GestionarCorteOficialService(cortes, sectores, registrarEvento, reloj, transaccion);

        given(cortes.guardar(any(CorteAgua.class))).willAnswer(invocacion -> invocacion.getArgument(0));
    }

    /** Ejecuta la acción directamente, sin Mongo real — la atomicidad real se prueba en TransaccionMongoAdapterIntegrationTest. */
    private static class TransaccionPasoDirecto implements TransaccionPort {
        @Override
        public <T> T ejecutar(java.util.function.Supplier<T> accion) {
            return accion.get();
        }
    }

    private CorteAgua corte(EstadoCorte estado, Instant finReal) {
        return CorteAgua.builder()
                .id(new CorteId("corte-1"))
                .sectoresAfectados(List.of(new SectorId("manga")))
                .inicio(INICIO)
                .finPrometido(INICIO.plus(6, ChronoUnit.HOURS))
                .finReal(finReal)
                .causa("Mantenimiento planta El Bosque")
                .origen(OrigenCorte.VEEDOR)
                .estado(estado)
                .build();
    }

    @Test
    void debeRegistrarUnCorteCuandoTodosLosSectoresExisten() {
        given(sectores.listarTodos())
                .willReturn(List.of(new Sector(new SectorId("manga"), "MANGA", 5000, EstadoServicio.SIN_SERVICIO)));

        CorteAgua guardado = servicio.registrar(corte(EstadoCorte.ANUNCIADO, null));

        assertThat(guardado.estado()).isEqualTo(EstadoCorte.ANUNCIADO);
        verify(cortes).guardar(any(CorteAgua.class));
    }

    @Test
    void debeAnexarUnEventoAnunciadoPorCadaSectorAfectado() {
        given(sectores.listarTodos()).willReturn(List.of(
                new Sector(new SectorId("manga"), "MANGA", 5000, EstadoServicio.SIN_SERVICIO),
                new Sector(new SectorId("bocagrande"), "BOCAGRANDE", 5000, EstadoServicio.SIN_SERVICIO)));
        CorteAgua corteConDosSectores = CorteAgua.builder()
                .id(new CorteId("corte-1"))
                .sectoresAfectados(List.of(new SectorId("manga"), new SectorId("bocagrande")))
                .inicio(INICIO)
                .finPrometido(INICIO.plus(6, ChronoUnit.HOURS))
                .causa("Mantenimiento")
                .origen(OrigenCorte.VEEDOR)
                .build();

        servicio.registrar(corteConDosSectores);

        ArgumentCaptor<EventoBitacora> captor = ArgumentCaptor.forClass(EventoBitacora.class);
        verify(registrarEvento, org.mockito.Mockito.times(2)).registrar(captor.capture());
        assertThat(captor.getAllValues()).extracting(EventoBitacora::tipo)
                .containsExactly(TipoEvento.CORTE_ANUNCIADO, TipoEvento.CORTE_ANUNCIADO);
        assertThat(captor.getAllValues()).extracting(EventoBitacora::sectorId)
                .containsExactly(new SectorId("manga"), new SectorId("bocagrande"));
        assertThat(captor.getAllValues()).allSatisfy(evento -> {
            assertThat(evento.corteId()).isEqualTo(new CorteId("corte-1"));
            assertThat(evento.timestamp()).isEqualTo(AHORA);
        });
    }

    @Test
    void debeDejarElSectorEnCorteProgramadoCuandoElCorteAunNoEmpieza() {
        Sector conServicio = new Sector(new SectorId("manga"), "MANGA", 5000, EstadoServicio.CON_SERVICIO);
        given(sectores.listarTodos()).willReturn(List.of(conServicio));
        CorteAgua futuro = CorteAgua.builder()
                .id(new CorteId("corte-1"))
                .sectoresAfectados(List.of(new SectorId("manga")))
                .inicio(AHORA.plus(3, ChronoUnit.DAYS))
                .finPrometido(AHORA.plus(3, ChronoUnit.DAYS).plus(6, ChronoUnit.HOURS))
                .causa("Mantenimiento planta El Bosque")
                .origen(OrigenCorte.VEEDOR)
                .build();

        servicio.registrar(futuro);

        verify(sectores).guardar(conServicio.conEstado(EstadoServicio.CORTE_PROGRAMADO));
    }

    @Test
    void debeDejarElSectorSinServicioCuandoElCorteYaEmpezo() {
        Sector conServicio = new Sector(new SectorId("manga"), "MANGA", 5000, EstadoServicio.CON_SERVICIO);
        given(sectores.listarTodos()).willReturn(List.of(conServicio));

        // INICIO es anterior a AHORA: el veedor registra un corte que ya esta en curso.
        servicio.registrar(corte(EstadoCorte.ANUNCIADO, null));

        verify(sectores).guardar(conServicio.conEstado(EstadoServicio.SIN_SERVICIO));
    }

    @Test
    void noDebeGuardarElSectorSiYaEstabaEnElEstadoQueCorresponde() {
        given(sectores.listarTodos()).willReturn(
                List.of(new Sector(new SectorId("manga"), "MANGA", 5000, EstadoServicio.SIN_SERVICIO)));

        servicio.registrar(corte(EstadoCorte.ANUNCIADO, null));

        // Guardar igual publicaria SectorActualizadoEvent y mandaria un correo por un cambio
        // que no ocurrio.
        verify(sectores, never()).guardar(any());
        // Pero el veedor sí sostuvo ese estado: queda verificado (ADR-073).
        verify(sectores).confirmarEstado(new SectorId("manga"), EstadoServicio.SIN_SERVICIO);
    }

    @Test
    void debeDevolverElSectorAConServicioAlCerrarElCorte() {
        Sector sinServicio = new Sector(new SectorId("manga"), "MANGA", 5000, EstadoServicio.SIN_SERVICIO);
        given(cortes.buscarPorId(new CorteId("corte-1"))).willReturn(Optional.of(corte(EstadoCorte.CONFIRMADO, null)));
        given(sectores.listarTodos()).willReturn(List.of(sinServicio));

        servicio.cerrar(new CorteId("corte-1"), INICIO.plus(5, ChronoUnit.HOURS));

        verify(sectores).guardar(sinServicio.conEstado(EstadoServicio.CON_SERVICIO));
    }

    @Test
    void noDebeRestablecerElSectorAlCerrarUnCorteSiOtroCorteDelMismoSectorSigueAbierto() {
        // B1 — dos cortes sobre "manga": uno masivo (corte-2) sigue abierto y uno local
        // (corte-1) se cierra. El sector debe seguir SIN_SERVICIO por el corte-2, no volver a
        // CON_SERVICIO.
        Sector sinServicio = new Sector(new SectorId("manga"), "MANGA", 5000, EstadoServicio.SIN_SERVICIO);
        CorteAgua corteLocal = corte(EstadoCorte.CONFIRMADO, null);
        CorteAgua corteMasivoAbierto = CorteAgua.builder()
                .id(new CorteId("corte-2"))
                .sectoresAfectados(List.of(new SectorId("manga"), new SectorId("bocagrande")))
                .inicio(INICIO.minus(2, ChronoUnit.HOURS))
                .finPrometido(INICIO.plus(10, ChronoUnit.HOURS))
                .causa("Falla en la PTAP El Bosque")
                .origen(OrigenCorte.OFICIAL_ACUACAR)
                .build();

        given(cortes.buscarPorId(new CorteId("corte-1"))).willReturn(Optional.of(corteLocal));
        given(cortes.listarPorSectores(List.of(new SectorId("manga"))))
                .willReturn(List.of(corteLocal, corteMasivoAbierto));
        given(sectores.listarTodos()).willReturn(List.of(sinServicio));

        servicio.cerrar(new CorteId("corte-1"), INICIO.plus(5, ChronoUnit.HOURS));

        verify(sectores, never()).guardar(any());
    }

    @Test
    void debeRestablecerElSectorAlCerrarElUltimoCorteAbierto() {
        Sector sinServicio = new Sector(new SectorId("manga"), "MANGA", 5000, EstadoServicio.SIN_SERVICIO);
        CorteAgua corteLocal = corte(EstadoCorte.CONFIRMADO, null);

        given(cortes.buscarPorId(new CorteId("corte-1"))).willReturn(Optional.of(corteLocal));
        given(cortes.listarPorSectores(List.of(new SectorId("manga")))).willReturn(List.of(corteLocal));
        given(sectores.listarTodos()).willReturn(List.of(sinServicio));

        servicio.cerrar(new CorteId("corte-1"), INICIO.plus(5, ChronoUnit.HOURS));

        verify(sectores).guardar(sinServicio.conEstado(EstadoServicio.CON_SERVICIO));
    }

    @Test
    void noDebeDegradarUnSectorSinServicioAlRegistrarUnSegundoCorteFuturo() {
        // Un corte ya en curso deja "manga" SIN_SERVICIO; registrar un segundo corte futuro
        // sobre el mismo sector no debe retroceder el estado a CORTE_PROGRAMADO.
        Sector sinServicio = new Sector(new SectorId("manga"), "MANGA", 5000, EstadoServicio.SIN_SERVICIO);
        CorteAgua corteEnCurso = CorteAgua.builder()
                .id(new CorteId("corte-en-curso"))
                .sectoresAfectados(List.of(new SectorId("manga")))
                .inicio(INICIO)
                .finPrometido(INICIO.plus(6, ChronoUnit.HOURS))
                .causa("Falla en la PTAP El Bosque")
                .origen(OrigenCorte.OFICIAL_ACUACAR)
                .build();
        CorteAgua futuro = CorteAgua.builder()
                .id(new CorteId("corte-futuro"))
                .sectoresAfectados(List.of(new SectorId("manga")))
                .inicio(AHORA.plus(3, ChronoUnit.DAYS))
                .finPrometido(AHORA.plus(3, ChronoUnit.DAYS).plus(6, ChronoUnit.HOURS))
                .causa("Mantenimiento planta El Bosque")
                .origen(OrigenCorte.VEEDOR)
                .build();

        given(sectores.listarTodos()).willReturn(List.of(sinServicio));
        given(cortes.listarPorSectores(List.of(new SectorId("manga")))).willReturn(List.of(corteEnCurso, futuro));

        servicio.registrar(futuro);

        verify(sectores, never()).guardar(any());
    }

    /**
     * Todo el bucle de sectores de un mismo corte va en una sola transacción, no una por sector:
     * antes, un fallo a mitad de camino dejaba "algunos sectores movidos de estado y otros no"
     * (ver el javadoc que tenía `anexarYMoverEstado` antes de la Fase 3).
     */
    @Test
    void debeAnexarYMoverTodosLosSectoresDeUnCorteEnUnaSolaTransaccion() {
        given(sectores.listarTodos()).willReturn(List.of(
                new Sector(new SectorId("manga"), "MANGA", 5000, EstadoServicio.SIN_SERVICIO),
                new Sector(new SectorId("bocagrande"), "BOCAGRANDE", 5000, EstadoServicio.SIN_SERVICIO)));
        CorteAgua corteConDosSectores = CorteAgua.builder()
                .id(new CorteId("corte-1"))
                .sectoresAfectados(List.of(new SectorId("manga"), new SectorId("bocagrande")))
                .inicio(INICIO)
                .finPrometido(INICIO.plus(6, ChronoUnit.HOURS))
                .causa("Mantenimiento")
                .origen(OrigenCorte.VEEDOR)
                .build();

        servicio.registrar(corteConDosSectores);

        verify(transaccion, org.mockito.Mockito.times(1)).ejecutar(any());
    }

    @Test
    void debePropagarLaFallaSiElRegistroDeEventoFallaEnAlgunSectorParaQueLaTransaccionRevierta() {
        given(sectores.listarTodos()).willReturn(List.of(
                new Sector(new SectorId("manga"), "MANGA", 5000, EstadoServicio.SIN_SERVICIO),
                new Sector(new SectorId("bocagrande"), "BOCAGRANDE", 5000, EstadoServicio.SIN_SERVICIO)));
        CorteAgua corteConDosSectores = CorteAgua.builder()
                .id(new CorteId("corte-1"))
                .sectoresAfectados(List.of(new SectorId("manga"), new SectorId("bocagrande")))
                .inicio(INICIO)
                .finPrometido(INICIO.plus(6, ChronoUnit.HOURS))
                .causa("Mantenimiento")
                .origen(OrigenCorte.VEEDOR)
                .build();
        org.mockito.Mockito.doThrow(new IllegalStateException("Mongo caído anexando el segundo sector"))
                .when(registrarEvento).registrar(argThat(evento -> evento.sectorId().equals(new SectorId("bocagrande"))));

        assertThatThrownBy(() -> servicio.registrar(corteConDosSectores)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void debeRechazarElRegistroSiAlgunSectorNoExiste() {
        given(sectores.listarTodos()).willReturn(List.of());

        assertThatThrownBy(() -> servicio.registrar(corte(EstadoCorte.ANUNCIADO, null)))
                .isInstanceOf(IllegalArgumentException.class);

        verify(cortes, never()).guardar(any());
        verify(registrarEvento, never()).registrar(any());
    }

    @Test
    void debeCerrarUnCorteAbiertoConLaHoraReal() {
        given(cortes.buscarPorId(new CorteId("corte-1"))).willReturn(Optional.of(corte(EstadoCorte.CONFIRMADO, null)));
        Instant finReal = INICIO.plus(5, ChronoUnit.HOURS);

        CorteAgua cerrado = servicio.cerrar(new CorteId("corte-1"), finReal);

        assertThat(cerrado.estado()).isEqualTo(EstadoCorte.RESTABLECIDO);
        assertThat(cerrado.ventana().finReal()).isEqualTo(finReal);

        ArgumentCaptor<EventoBitacora> captor = ArgumentCaptor.forClass(EventoBitacora.class);
        verify(registrarEvento).registrar(captor.capture());
        assertThat(captor.getValue().tipo()).isEqualTo(TipoEvento.CORTE_RESTABLECIDO);
        assertThat(captor.getValue().sectorId()).isEqualTo(new SectorId("manga"));
    }

    @Test
    void debeRechazarCerrarUnCorteQueNoExiste() {
        given(cortes.buscarPorId(new CorteId("no-existe"))).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.cerrar(new CorteId("no-existe"), INICIO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void debeRechazarCerrarUnCorteYaCerrado() {
        Instant finRealPrevio = INICIO.plus(4, ChronoUnit.HOURS);
        given(cortes.buscarPorId(new CorteId("corte-1")))
                .willReturn(Optional.of(corte(EstadoCorte.RESTABLECIDO, finRealPrevio)));

        assertThatThrownBy(() -> servicio.cerrar(new CorteId("corte-1"), INICIO.plus(5, ChronoUnit.HOURS)))
                .isInstanceOf(IllegalStateException.class);

        verify(cortes, never()).guardar(any());
        verify(registrarEvento, never()).registrar(any());
    }
}
