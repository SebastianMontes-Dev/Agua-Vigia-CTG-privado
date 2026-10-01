package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.AccionAuditada;
import com.aguavigia.ctg.domain.ContextoDeAccion;
import com.aguavigia.ctg.domain.EntidadNoEncontradaException;
import com.aguavigia.ctg.domain.EstadoCorte;
import com.aguavigia.ctg.domain.EstadoPublicado;
import com.aguavigia.ctg.domain.EstadoRevision;
import com.aguavigia.ctg.domain.EstadoServicio;
import com.aguavigia.ctg.domain.EventoBitacora;
import com.aguavigia.ctg.domain.OrigenCorte;
import com.aguavigia.ctg.domain.PropuestaId;
import com.aguavigia.ctg.domain.PropuestaIngesta;
import com.aguavigia.ctg.domain.ResultadoDeRecalculo;
import com.aguavigia.ctg.domain.Sector;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.TipoEvento;
import com.aguavigia.ctg.domain.port.in.RecalcularSectorUseCase;
import com.aguavigia.ctg.domain.port.in.RegistrarEventoBitacoraUseCase;
import com.aguavigia.ctg.domain.port.out.CorteAguaRepository;
import com.aguavigia.ctg.domain.port.out.PropuestaIngestaRepository;
import com.aguavigia.ctg.domain.port.out.SectorRepository;
import com.aguavigia.ctg.domain.port.out.TransaccionPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * M9 + M5 — el punto donde una propuesta automatizada se convierte (o no) en dato público. Aprobar guarda
 * la propuesta y el corte del boletín y le pide al recálculo que decida qué estado corresponde: ya no fija
 * el estado aquí. Descartar no toca nada, y anular deshace lo que una aprobación por error publicó.
 */
class RevisarPropuestaIngestaServiceTest {

    private static final Instant AHORA = Instant.parse("2026-08-09T15:30:00Z");
    private static final Instant INICIO_DECLARADO = Instant.parse("2026-08-09T14:00:00Z");
    private static final PropuestaId ID = new PropuestaId("p-1");
    private static final SectorId MANGA = new SectorId("manga");

    private PropuestaIngestaRepository propuestas;
    private SectorRepository sectores;
    private RegistrarEventoBitacoraUseCase registrarEvento;
    private CorteAguaRepository cortes;
    private RecalcularSectorUseCase recalcular;
    private TransaccionPort transaccion;
    private RegistroDeAuditoria auditoria;
    private static final ContextoDeAccion CONTEXTO = new ContextoDeAccion(null, "10.0.0.1");
    private RevisarPropuestaIngestaService servicio;

    @BeforeEach
    void montar() {
        propuestas = mock(PropuestaIngestaRepository.class);
        sectores = mock(SectorRepository.class);
        registrarEvento = mock(RegistrarEventoBitacoraUseCase.class);
        cortes = mock(CorteAguaRepository.class);
        recalcular = mock(RecalcularSectorUseCase.class);
        transaccion = spy(new TransaccionPort() {
            @Override
            public <T> T ejecutar(java.util.function.Supplier<T> accion) {
                return accion.get();
            }
        });
        auditoria = mock(RegistroDeAuditoria.class);
        servicio = new RevisarPropuestaIngestaService(propuestas, sectores, registrarEvento, cortes, recalcular,
                () -> AHORA, transaccion, auditoria);

        given(propuestas.guardar(any())).willAnswer(invocacion -> invocacion.getArgument(0));
        given(propuestas.buscarPorId(ID)).willReturn(Optional.of(propuestaConVentana()));
        given(sectores.buscarPorId(MANGA)).willReturn(Optional.of(new Sector(MANGA, "MANGA", 5000, null)));
        given(recalcular.recalcular(any())).willReturn(ResultadoDeRecalculo.sinCambio(EstadoPublicado.sinDatos()));
    }

    private static PropuestaIngesta propuestaConVentana() {
        return new PropuestaIngesta(ID, MANGA, EstadoServicio.SIN_SERVICIO, "acuacar",
                "https://acuacar.com/x", "cita", 0.6, AHORA, INICIO_DECLARADO, INICIO_DECLARADO.plusSeconds(9 * 3600));
    }

    private static PropuestaIngesta propuestaSinVentana() {
        return new PropuestaIngesta(ID, MANGA, EstadoServicio.SIN_SERVICIO, "acuacar",
                "https://acuacar.com/x", "cita", 0.6, AHORA);
    }

    @Nested
    class Aprobar {

        @Test
        void debeGuardarLaPropuestaComoAprobada() {
            PropuestaIngesta aprobada = servicio.aprobar(ID);

            assertThat(aprobada.estadoRevision()).isEqualTo(EstadoRevision.APROBADA);
            verify(propuestas).guardar(aprobada);
        }

        /** Lo que mueve el mapa ya no es esta clase: pide el recálculo con la propuesta ya guardada, para que la vea. */
        @Test
        void debePedirElRecalculoDelBarrioDespuesDeGuardarLaPropuestaYElCorte() {
            servicio.aprobar(ID);

            InOrder orden = inOrder(propuestas, cortes, recalcular);
            orden.verify(propuestas).guardar(any());
            orden.verify(cortes).anexarSectorAlCorte(any(), eq(MANGA), any(), any(), any(), any(), any());
            orden.verify(recalcular).recalcular(MANGA);
        }

        @Test
        void noDebeTocarElEstadoDelSectorDirectamente() {
            servicio.aprobar(ID);

            verify(sectores, never()).guardar(any());
            verify(sectores, never()).confirmarEstado(any(), any());
            verify(registrarEvento, never()).registrar(any());
        }

        @Test
        void debeRegistrarElCorteDelBoletinCuandoDeclaraVentana() {
            servicio.aprobar(ID);

            verify(cortes).anexarSectorAlCorte(eq(propuestaConVentana().idDelCorte()), eq(MANGA),
                    eq(INICIO_DECLARADO), eq(INICIO_DECLARADO.plusSeconds(9 * 3600)), eq("cita"),
                    eq(OrigenCorte.INGESTA_IA), eq(EstadoCorte.ANUNCIADO));
        }

        /** Un boletín sin ventana no dice cuándo ocurre: no es un corte y no entra a las estadísticas. */
        @Test
        void noDebeRegistrarCorteSiElBoletinNoDeclaraVentana() {
            given(propuestas.buscarPorId(ID)).willReturn(Optional.of(propuestaSinVentana()));

            servicio.aprobar(ID);

            verify(cortes, never()).anexarSectorAlCorte(any(), any(), any(), any(), any(), any(), any());
            verify(recalcular).recalcular(MANGA);
        }

        @Test
        void todoVaEnUnaSolaTransaccion() {
            servicio.aprobar(ID);

            verify(transaccion).ejecutar(any());
        }

        @Test
        void siElRecalculoFallaLaFallaSePropagaParaQueLaTransaccionRevierta() {
            doThrow(new IllegalStateException("Mongo caído")).when(recalcular).recalcular(MANGA);

            assertThatThrownBy(() -> servicio.aprobar(ID)).isInstanceOf(IllegalStateException.class);
        }

        @Test
        void aprobarUnaPropuestaYaDescartadaNoDebeTocarNada() {
            given(propuestas.buscarPorId(ID)).willReturn(Optional.of(propuestaConVentana().descartar()));

            assertThatThrownBy(() -> servicio.aprobar(ID)).isInstanceOf(IllegalStateException.class);

            verify(propuestas, never()).guardar(any());
            verify(cortes, never()).anexarSectorAlCorte(any(), any(), any(), any(), any(), any(), any());
            verify(recalcular, never()).recalcular(any());
        }

        @Test
        void aprobarUnaPropuestaAnuladaNoDebeTocarNada() {
            given(propuestas.buscarPorId(ID)).willReturn(Optional.of(propuestaConVentana().aprobar().anular("Por error")));

            assertThatThrownBy(() -> servicio.aprobar(ID)).isInstanceOf(IllegalStateException.class);

            verify(propuestas, never()).guardar(any());
            verify(recalcular, never()).recalcular(any());
        }

        @Test
        void debeRechazarRevisarUnaPropuestaQueNoExiste() {
            given(propuestas.buscarPorId(new PropuestaId("no-existe"))).willReturn(Optional.empty());

            assertThatThrownBy(() -> servicio.aprobar(new PropuestaId("no-existe")))
                    .isInstanceOf(EntidadNoEncontradaException.class);
        }

        @Test
        void aprobarDebeFallarConConflictoSiElSectorYaNoExiste() {
            given(sectores.buscarPorId(MANGA)).willReturn(Optional.empty());

            assertThatThrownBy(() -> servicio.aprobar(ID)).isInstanceOf(IllegalStateException.class);

            verify(propuestas, never()).guardar(any());
        }
    }

    @Nested
    class Descartar {

        @Test
        void noDebeTocarElSectorNiLaBitacoraNiElCorte() {
            servicio.descartar(ID);

            verify(propuestas).guardar(any());
            verify(recalcular, never()).recalcular(any());
            verify(registrarEvento, never()).registrar(any());
            verify(cortes, never()).anexarSectorAlCorte(any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        void unaPropuestaYaAprobadaNoSePuedeDescartar() {
            given(propuestas.buscarPorId(ID)).willReturn(Optional.of(propuestaConVentana().aprobar()));

            assertThatThrownBy(() -> servicio.descartar(ID)).isInstanceOf(IllegalStateException.class);

            verify(propuestas, never()).guardar(any());
        }
    }

    @Nested
    class Anular {

        private void dadaUnaPropuestaAprobada() {
            given(propuestas.buscarPorId(ID)).willReturn(Optional.of(propuestaConVentana().aprobar()));
        }

        @Test
        void anulaLaPropuestaConSuMotivoYRecalculaElBarrio() {
            dadaUnaPropuestaAprobada();

            PropuestaIngesta anulada = servicio.anular(ID, "El extractor leyó mal el barrio", CONTEXTO);

            assertThat(anulada.estadoRevision()).isEqualTo(EstadoRevision.ANULADA);
            assertThat(anulada.motivoAnulacion()).isEqualTo("El extractor leyó mal el barrio");
            verify(propuestas).guardar(anulada);
            verify(recalcular).recalcular(MANGA);
        }

        /** La bitácora no se edita: la corrección es un evento nuevo que cita el boletín anulado y el motivo. */
        @Test
        void anexaUnEventoDeCorreccionALaBitacora() {
            dadaUnaPropuestaAprobada();

            servicio.anular(ID, "El extractor leyó mal el barrio", CONTEXTO);

            ArgumentCaptor<EventoBitacora> captor = ArgumentCaptor.forClass(EventoBitacora.class);
            verify(registrarEvento).registrar(captor.capture());
            assertThat(captor.getValue().tipo()).isEqualTo(TipoEvento.CORTE_ANULADO);
            assertThat(captor.getValue().sectorId()).isEqualTo(MANGA);
            assertThat(captor.getValue().urlOriginal()).isEqualTo("https://acuacar.com/x");
            assertThat(captor.getValue().descripcion()).contains("El extractor leyó mal el barrio");
        }

        @Test
        void dejaConstanciaEnLaAuditoriaDeQuienAnuloYPorQue() {
            dadaUnaPropuestaAprobada();

            servicio.anular(ID, "El extractor leyó mal el barrio", CONTEXTO);

            verify(auditoria).registrar(eq(AccionAuditada.PROPUESTA_ANULADA), isNull(),
                    argThat(detalle -> detalle.contains(ID.valor()) && detalle.contains("El extractor leyó mal el barrio")),
                    eq(CONTEXTO));
        }

        @Test
        void todoVaEnUnaSolaTransaccion() {
            dadaUnaPropuestaAprobada();

            servicio.anular(ID, "Por error", CONTEXTO);

            verify(transaccion).ejecutar(any());
        }

        @Test
        void exigeUnMotivoYSoloAnulaUnaPropuestaAprobada() {
            assertThatThrownBy(() -> servicio.anular(ID, " ", CONTEXTO)).isInstanceOf(IllegalArgumentException.class);
            // La del montaje está pendiente: nunca movió el mapa, no hay nada que anular.
            assertThatThrownBy(() -> servicio.anular(ID, "Por error", CONTEXTO)).isInstanceOf(IllegalStateException.class);

            verify(propuestas, never()).guardar(any());
            verify(registrarEvento, never()).registrar(any());
            verify(recalcular, never()).recalcular(any());
        }

        @Test
        void rechazaUnaPropuestaQueNoExiste() {
            given(propuestas.buscarPorId(new PropuestaId("no-existe"))).willReturn(Optional.empty());

            assertThatThrownBy(() -> servicio.anular(new PropuestaId("no-existe"), "Por error", CONTEXTO))
                    .isInstanceOf(EntidadNoEncontradaException.class);
        }
    }
}
