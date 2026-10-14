package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.CierreDeCorte;
import com.aguavigia.ctg.domain.AccionAuditada;
import com.aguavigia.ctg.domain.ContextoDeAccion;
import com.aguavigia.ctg.domain.CorteAgua;
import com.aguavigia.ctg.domain.CorteId;
import com.aguavigia.ctg.domain.EntidadNoEncontradaException;
import com.aguavigia.ctg.domain.EstadoCorte;
import com.aguavigia.ctg.domain.EstadoServicio;
import com.aguavigia.ctg.domain.EventoBitacora;
import com.aguavigia.ctg.domain.OrigenCorte;
import com.aguavigia.ctg.domain.OrigenEstado;
import com.aguavigia.ctg.domain.ResultadoDeRecalculo;
import com.aguavigia.ctg.domain.EstadoPublicado;
import com.aguavigia.ctg.domain.Sector;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.TipoEvento;
import com.aguavigia.ctg.domain.port.in.RecalcularSectorUseCase;
import com.aguavigia.ctg.domain.port.in.RegistrarEventoBitacoraUseCase;
import com.aguavigia.ctg.domain.port.out.CorteAguaRepository;
import com.aguavigia.ctg.domain.port.out.RelojPort;
import com.aguavigia.ctg.domain.port.out.SectorRepository;
import com.aguavigia.ctg.domain.port.out.TransaccionPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * RF016-RF017 — el veedor registra un corte y lo cierra barrio por barrio. Este servicio ya no mueve el
 * estado de los barrios: guarda el corte, anexa los eventos que le tocan y le pide al recálculo que
 * decida. Qué estado corresponde (y que un corte abierto no se pise con otro) está en el resolutor.
 */
class GestionarCorteOficialServiceTest {

    private static final SectorId MANGA = new SectorId("manga");
    private static final SectorId BOCAGRANDE = new SectorId("bocagrande");
    private static final Instant INICIO = Instant.parse("2026-08-09T10:00:00Z");
    private static final Instant AHORA = Instant.parse("2026-08-09T20:00:00Z");

    private CorteAguaRepository cortes;
    private SectorRepository sectores;
    private RegistrarEventoBitacoraUseCase registrarEvento;
    private RecalcularSectorUseCase recalcular;
    private TransaccionPort transaccion;
    private RegistroDeAuditoria auditoria;
    private static final ContextoDeAccion CONTEXTO = new ContextoDeAccion(null, "10.0.0.1");
    private GestionarCorteOficialService servicio;

    @BeforeEach
    void montar() {
        cortes = mock(CorteAguaRepository.class);
        sectores = mock(SectorRepository.class);
        registrarEvento = mock(RegistrarEventoBitacoraUseCase.class);
        recalcular = mock(RecalcularSectorUseCase.class);
        transaccion = spy(new TransaccionPort() {
            @Override
            public <T> T ejecutar(java.util.function.Supplier<T> accion) {
                return accion.get();
            }
        });
        auditoria = mock(RegistroDeAuditoria.class);
        servicio = new GestionarCorteOficialService(cortes, sectores, registrarEvento, recalcular, () -> AHORA, transaccion,
                auditoria);

        given(cortes.guardar(any(CorteAgua.class))).willAnswer(invocacion -> invocacion.getArgument(0));
        given(recalcular.recalcular(any())).willReturn(ResultadoDeRecalculo.sinCambio(EstadoPublicado.sinDatos()));
        given(sectores.listarTodos()).willReturn(List.of(
                new Sector(MANGA, "MANGA", 5000, null), new Sector(BOCAGRANDE, "BOCAGRANDE", 5000, null)));
    }

    private static CorteAgua.Builder corteBase(SectorId... sectores) {
        return CorteAgua.builder()
                .id(new CorteId("corte-1"))
                .sectoresAfectados(List.of(sectores))
                .inicio(INICIO)
                .finPrometido(INICIO.plus(6, ChronoUnit.HOURS))
                .causa("Mantenimiento planta El Bosque")
                .origen(OrigenCorte.VEEDOR);
    }

    private void dadoElCorte(CorteAgua corte) {
        given(cortes.buscarPorId(corte.id())).willReturn(Optional.of(corte));
    }

    private List<EventoBitacora> eventosRegistrados(int cantidad) {
        ArgumentCaptor<EventoBitacora> captor = ArgumentCaptor.forClass(EventoBitacora.class);
        verify(registrarEvento, times(cantidad)).registrar(captor.capture());
        return captor.getAllValues();
    }

    @Nested
    class Registrar {

        @Test
        void debeRegistrarUnCorteCuandoTodosLosSectoresExisten() {
            CorteAgua guardado = servicio.registrar(corteBase(MANGA).build());

            assertThat(guardado.estado()).isEqualTo(EstadoCorte.ANUNCIADO);
            verify(cortes).guardar(any(CorteAgua.class));
        }

        @Test
        void debeAnexarUnEventoAnunciadoPorCadaSectorAfectado() {
            servicio.registrar(corteBase(MANGA, BOCAGRANDE).build());

            List<EventoBitacora> eventos = eventosRegistrados(2);
            assertThat(eventos).extracting(EventoBitacora::tipo)
                    .containsExactly(TipoEvento.CORTE_ANUNCIADO, TipoEvento.CORTE_ANUNCIADO);
            assertThat(eventos).extracting(EventoBitacora::sectorId).containsExactly(MANGA, BOCAGRANDE);
            assertThat(eventos).allSatisfy(evento -> {
                assertThat(evento.corteId()).isEqualTo(new CorteId("corte-1"));
                assertThat(evento.timestamp()).isEqualTo(AHORA);
            });
        }

        /** El estado de cada barrio ya no lo decide este servicio: lo recalcula el único escritor, con el corte ya guardado. */
        @Test
        void debePedirElRecalculoDeCadaSectorDespuesDeGuardarElCorteYSusEventos() {
            servicio.registrar(corteBase(MANGA, BOCAGRANDE).build());

            InOrder orden = inOrder(cortes, registrarEvento, recalcular);
            orden.verify(cortes).guardar(any(CorteAgua.class));
            orden.verify(registrarEvento, times(2)).registrar(any());
            orden.verify(recalcular).recalcular(MANGA);
            orden.verify(recalcular).recalcular(BOCAGRANDE);
        }

        @Test
        void noDebeTocarElEstadoDeLosSectoresDirectamente() {
            servicio.registrar(corteBase(MANGA).build());

            verify(sectores, never()).guardar(any());
            verify(sectores, never()).confirmarEstado(any(), any());
        }

        /** Corte, eventos y estado de todos los barrios son una sola unidad: si algo falla, ninguno queda a medias. */
        @Test
        void debeHacerTodoEnUnaSolaTransaccion() {
            servicio.registrar(corteBase(MANGA, BOCAGRANDE).build());

            verify(transaccion, times(1)).ejecutar(any());
        }

        @Test
        void debePropagarLaFallaSiElRegistroDeEventoFallaEnAlgunSectorParaQueLaTransaccionRevierta() {
            org.mockito.Mockito.doThrow(new IllegalStateException("Mongo caído anexando el segundo sector"))
                    .when(registrarEvento).registrar(argThat(evento -> evento.sectorId().equals(BOCAGRANDE)));

            assertThatThrownBy(() -> servicio.registrar(corteBase(MANGA, BOCAGRANDE).build()))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void debeRechazarElRegistroSiAlgunSectorNoExiste() {
            given(sectores.listarTodos()).willReturn(List.of());

            assertThatThrownBy(() -> servicio.registrar(corteBase(MANGA).build()))
                    .isInstanceOf(IllegalArgumentException.class);

            verify(cortes, never()).guardar(any());
            verify(registrarEvento, never()).registrar(any());
            verify(recalcular, never()).recalcular(any());
        }
    }

    @Nested
    class CerrarTodo {

        @Test
        void debeCerrarUnCorteAbiertoConLaHoraRealYAnexarElEventoDeRestablecimiento() {
            dadoElCorte(corteBase(MANGA).estado(EstadoCorte.CONFIRMADO).build());
            Instant finReal = INICIO.plus(5, ChronoUnit.HOURS);

            CorteAgua cerrado = servicio.cerrar(new CorteId("corte-1"), finReal);

            assertThat(cerrado.estado()).isEqualTo(EstadoCorte.RESTABLECIDO);
            assertThat(cerrado.ventana().finReal()).isEqualTo(finReal);
            EventoBitacora evento = eventosRegistrados(1).get(0);
            assertThat(evento.tipo()).isEqualTo(TipoEvento.CORTE_RESTABLECIDO);
            assertThat(evento.sectorId()).isEqualTo(MANGA);
        }

        @Test
        void debeRecalcularCadaSectorDelCorteDespuesDeCerrarlo() {
            dadoElCorte(corteBase(MANGA, BOCAGRANDE).build());

            servicio.cerrar(new CorteId("corte-1"), INICIO.plus(5, ChronoUnit.HOURS));

            verify(recalcular).recalcular(MANGA);
            verify(recalcular).recalcular(BOCAGRANDE);
            verify(sectores, never()).guardar(any());
        }

        /** El atajo «cerrar todo» no vuelve a anexar el restablecimiento de un barrio que ya se había cerrado. */
        @Test
        void soloAnexaElRestablecimientoDeLosBarriosQueSeCierranAhora() {
            dadoElCorte(corteBase(MANGA, BOCAGRANDE).build()
                    .cerrarSector(MANGA, new CierreDeCorte(INICIO.plus(3, ChronoUnit.HOURS), OrigenEstado.VECINOS, true)));

            servicio.cerrar(new CorteId("corte-1"), INICIO.plus(5, ChronoUnit.HOURS));

            assertThat(eventosRegistrados(1).get(0).sectorId()).isEqualTo(BOCAGRANDE);
        }

        @Test
        void debeCerrarTambienUnCorteNacidoDeLaIngesta() {
            dadoElCorte(corteBase(MANGA).origen(OrigenCorte.INGESTA_IA).build());

            CorteAgua cerrado = servicio.cerrar(new CorteId("corte-1"), INICIO.plus(5, ChronoUnit.HOURS));

            assertThat(cerrado.estado()).isEqualTo(EstadoCorte.RESTABLECIDO);
            assertThat(cerrado.origen()).isEqualTo(OrigenCorte.INGESTA_IA);
        }

        @Test
        void debeRechazarCerrarUnCorteQueNoExiste() {
            given(cortes.buscarPorId(new CorteId("no-existe"))).willReturn(Optional.empty());

            assertThatThrownBy(() -> servicio.cerrar(new CorteId("no-existe"), INICIO))
                    .isInstanceOf(EntidadNoEncontradaException.class);
        }

        @Test
        void debeRechazarCerrarUnCorteYaCerrado() {
            dadoElCorte(corteBase(MANGA).build().cerrar(INICIO.plus(4, ChronoUnit.HOURS)));

            assertThatThrownBy(() -> servicio.cerrar(new CorteId("corte-1"), INICIO.plus(5, ChronoUnit.HOURS)))
                    .isInstanceOf(IllegalStateException.class);

            verify(cortes, never()).guardar(any());
            verify(registrarEvento, never()).registrar(any());
            verify(recalcular, never()).recalcular(any());
        }
    }

    @Nested
    class CerrarUnBarrio {

        @Test
        void cierraSoloEseBarrioYDejaElCorteAbiertoParaLosDemas() {
            dadoElCorte(corteBase(MANGA, BOCAGRANDE).build());
            Instant hora = INICIO.plus(3, ChronoUnit.HOURS);

            CorteAgua parcial = servicio.cerrarSector(new CorteId("corte-1"), MANGA, hora);

            assertThat(parcial.estado()).isEqualTo(EstadoCorte.ANUNCIADO);
            assertThat(parcial.cierreDe(MANGA)).contains(new CierreDeCorte(hora, OrigenEstado.VEEDOR, false));
            assertThat(parcial.cierreDe(BOCAGRANDE)).isEmpty();
        }

        @Test
        void anexaElRestablecimientoYRecalculaSoloEseBarrio() {
            dadoElCorte(corteBase(MANGA, BOCAGRANDE).build());

            servicio.cerrarSector(new CorteId("corte-1"), MANGA, INICIO.plus(3, ChronoUnit.HOURS));

            EventoBitacora evento = eventosRegistrados(1).get(0);
            assertThat(evento.tipo()).isEqualTo(TipoEvento.CORTE_RESTABLECIDO);
            assertThat(evento.sectorId()).isEqualTo(MANGA);
            verify(recalcular).recalcular(MANGA);
            verify(recalcular, never()).recalcular(BOCAGRANDE);
        }

        @Test
        void alCerrarElUltimoBarrioElCorteQuedaRestablecido() {
            dadoElCorte(corteBase(MANGA).build());

            assertThat(servicio.cerrarSector(new CorteId("corte-1"), MANGA, INICIO.plus(3, ChronoUnit.HOURS)).estado())
                    .isEqualTo(EstadoCorte.RESTABLECIDO);
        }

        @Test
        void rechazaUnCorteInexistenteOUnBarrioQueElCorteNoAfecta() {
            given(cortes.buscarPorId(new CorteId("no-existe"))).willReturn(Optional.empty());
            dadoElCorte(corteBase(MANGA).build());

            assertThatThrownBy(() -> servicio.cerrarSector(new CorteId("no-existe"), MANGA, INICIO))
                    .isInstanceOf(EntidadNoEncontradaException.class);
            assertThatThrownBy(() -> servicio.cerrarSector(new CorteId("corte-1"), BOCAGRANDE, INICIO.plusSeconds(60)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class ConfirmarUnCierre {

        private CorteAgua cerradoPorLosVecinos() {
            return corteBase(MANGA, BOCAGRANDE).build()
                    .cerrarSector(MANGA, new CierreDeCorte(INICIO.plus(3, ChronoUnit.HOURS), OrigenEstado.VECINOS, true));
        }

        @Test
        void elVeedorConfirmaOCorrigeLaHoraYSeRecalculaElBarrio() {
            dadoElCorte(cerradoPorLosVecinos());
            Instant corregida = INICIO.plus(2, ChronoUnit.HOURS);

            CorteAgua confirmado = servicio.confirmarCierre(new CorteId("corte-1"), MANGA, corregida);

            assertThat(confirmado.cierreDe(MANGA)).contains(new CierreDeCorte(corregida, OrigenEstado.VEEDOR, false));
            verify(recalcular).recalcular(MANGA);
            // La confirmación corrige un dato; no es un restablecimiento nuevo que anunciar.
            verify(registrarEvento, never()).registrar(any());
        }

        @Test
        void rechazaConfirmarUnCierreQueYaEstabaConfirmado() {
            dadoElCorte(cerradoPorLosVecinos().confirmarCierre(MANGA,
                    new CierreDeCorte(INICIO.plus(2, ChronoUnit.HOURS), OrigenEstado.VEEDOR, false)));

            assertThatThrownBy(() -> servicio.confirmarCierre(new CorteId("corte-1"), MANGA, INICIO.plus(1, ChronoUnit.HOURS)))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    class Anular {

        @Test
        void anulaElCorteConSuMotivoAnexaUnEventoPorBarrioYRecalcula() {
            dadoElCorte(corteBase(MANGA, BOCAGRANDE).origen(OrigenCorte.INGESTA_IA).build());

            CorteAgua anulado = servicio.anular(new CorteId("corte-1"), "El boletín hablaba de otro barrio", CONTEXTO);

            assertThat(anulado.estado()).isEqualTo(EstadoCorte.ANULADO);
            assertThat(anulado.motivoAnulacion()).isEqualTo("El boletín hablaba de otro barrio");
            List<EventoBitacora> eventos = eventosRegistrados(2);
            assertThat(eventos).extracting(EventoBitacora::tipo)
                    .containsOnly(TipoEvento.CORTE_ANULADO);
            assertThat(eventos).extracting(EventoBitacora::sectorId).containsExactly(MANGA, BOCAGRANDE);
            verify(recalcular).recalcular(MANGA);
            verify(recalcular).recalcular(BOCAGRANDE);
        }

        /** Quién anuló qué y por qué queda en la auditoría: anular reescribe lo que el mapa mostró. */
        @Test
        void dejaConstanciaEnLaAuditoriaDeQuienAnuloYPorQue() {
            dadoElCorte(corteBase(MANGA).build());

            servicio.anular(new CorteId("corte-1"), "Registrado por error", CONTEXTO);

            verify(auditoria).registrar(eq(AccionAuditada.CORTE_ANULADO), isNull(),
                    argThat(detalle -> detalle.contains("corte-1") && detalle.contains("Registrado por error")), eq(CONTEXTO));
        }

        @Test
        void unaAnulacionRechazadaNoDejaAuditoria() {
            dadoElCorte(corteBase(MANGA).build().anular("Por error"));

            assertThatThrownBy(() -> servicio.anular(new CorteId("corte-1"), "Otra vez", CONTEXTO))
                    .isInstanceOf(IllegalStateException.class);

            verify(auditoria, never()).registrar(any(), any(), any(), any());
        }

        @Test
        void exigeUnMotivoYRechazaUnCorteInexistente() {
            dadoElCorte(corteBase(MANGA).build());
            given(cortes.buscarPorId(new CorteId("no-existe"))).willReturn(Optional.empty());

            assertThatThrownBy(() -> servicio.anular(new CorteId("corte-1"), " ", CONTEXTO))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> servicio.anular(new CorteId("no-existe"), "motivo", CONTEXTO))
                    .isInstanceOf(EntidadNoEncontradaException.class);
            verify(cortes, never()).guardar(any());
        }

        @Test
        void unCorteSoloSeAnulaUnaVez() {
            dadoElCorte(corteBase(MANGA).build().anular("Por error"));

            assertThatThrownBy(() -> servicio.anular(new CorteId("corte-1"), "Otra vez", CONTEXTO))
                    .isInstanceOf(IllegalStateException.class);
        }
    }
}
