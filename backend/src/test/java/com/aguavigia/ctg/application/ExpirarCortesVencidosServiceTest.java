package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.CierreDeCorte;
import com.aguavigia.ctg.domain.CorteAgua;
import com.aguavigia.ctg.domain.CorteId;
import com.aguavigia.ctg.domain.EstadoCorte;
import com.aguavigia.ctg.domain.EstadoPublicado;
import com.aguavigia.ctg.domain.EventoBitacora;
import com.aguavigia.ctg.domain.OrigenCorte;
import com.aguavigia.ctg.domain.OrigenEstado;
import com.aguavigia.ctg.domain.ResultadoDeRecalculo;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.TipoEvento;
import com.aguavigia.ctg.domain.port.in.RecalcularSectorUseCase;
import com.aguavigia.ctg.domain.port.in.RegistrarEventoBitacoraUseCase;
import com.aguavigia.ctg.domain.port.out.CorteAguaRepository;
import com.aguavigia.ctg.domain.port.out.TransaccionPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Un corte que nadie confirmó ni cerró no puede quedar abierto para siempre: pasado el plazo de expiración
 * (D3) el corte pasa a EXPIRADO, el barrio vuelve a «sin datos» y la bitácora lo dice. Un mapa congelado en
 * rojo es peor que admitir que no se sabe.
 */
class ExpirarCortesVencidosServiceTest {

    private static final SectorId MANGA = new SectorId("manga");
    private static final SectorId BOCAGRANDE = new SectorId("bocagrande");
    private static final Instant INICIO = Instant.parse("2026-08-21T14:00:00Z");
    private static final Instant FIN = Instant.parse("2026-08-21T23:00:00Z");
    private static final Duration PLAZO = Duration.ofHours(72);

    private CorteAguaRepository cortes;
    private RegistrarEventoBitacoraUseCase registrarEvento;
    private RecalcularSectorUseCase recalcular;
    private TransaccionPort transaccion;
    private Instant ahora;
    private ExpirarCortesVencidosService servicio;

    @BeforeEach
    void montar() {
        cortes = mock(CorteAguaRepository.class);
        registrarEvento = mock(RegistrarEventoBitacoraUseCase.class);
        recalcular = mock(RecalcularSectorUseCase.class);
        transaccion = spy(new TransaccionPort() {
            @Override
            public <T> T ejecutar(java.util.function.Supplier<T> accion) {
                return accion.get();
            }
        });
        ahora = FIN.plus(PLAZO);
        given(cortes.listarAbiertos()).willReturn(List.of());
        // Releer un corte dentro de la transacción devuelve lo que la prueba haya dejado como abierto.
        given(cortes.buscarPorId(any())).willAnswer(invocacion -> cortes.listarAbiertos().stream()
                .filter(corte -> corte.id().equals(invocacion.getArgument(0))).findFirst());
        given(recalcular.recalcular(any())).willReturn(ResultadoDeRecalculo.sinCambio(EstadoPublicado.sinDatos()));
        servicio = new ExpirarCortesVencidosService(cortes, registrarEvento, recalcular, () -> ahora, transaccion, PLAZO);
    }

    private static CorteAgua corte(String id, OrigenCorte origen, SectorId... sectores) {
        return CorteAgua.builder().id(new CorteId(id)).sectoresAfectados(List.of(sectores))
                .inicio(INICIO).finPrometido(FIN).causa("Suspensión").origen(origen).build();
    }

    @Test
    void expiraUnCorteAbiertoCuandoPasoElPlazoDesdeElFinPrometido() {
        given(cortes.listarAbiertos()).willReturn(List.of(corte("c1", OrigenCorte.INGESTA_IA, MANGA)));

        int expirados = servicio.expirarVencidos();

        assertThat(expirados).isEqualTo(1);
        ArgumentCaptor<CorteAgua> guardado = ArgumentCaptor.forClass(CorteAgua.class);
        verify(cortes).guardar(guardado.capture());
        assertThat(guardado.getValue().estado()).isEqualTo(EstadoCorte.EXPIRADO);
    }

    @Test
    void noExpiraUnCorteAntesDeQueVenzaElPlazo() {
        ahora = FIN.plus(PLAZO).minusSeconds(1);
        given(cortes.listarAbiertos()).willReturn(List.of(corte("c1", OrigenCorte.INGESTA_IA, MANGA)));

        assertThat(servicio.expirarVencidos()).isZero();

        verify(cortes, never()).guardar(any());
        verify(registrarEvento, never()).registrar(any());
    }

    @Test
    void dejaEnLaBitacoraQueElCorteVencioSinConfirmacionUnaVezPorBarrioPendiente() {
        given(cortes.listarAbiertos()).willReturn(List.of(corte("c1", OrigenCorte.VEEDOR, MANGA, BOCAGRANDE)));

        servicio.expirarVencidos();

        ArgumentCaptor<EventoBitacora> eventos = ArgumentCaptor.forClass(EventoBitacora.class);
        verify(registrarEvento, times(2)).registrar(eventos.capture());
        assertThat(eventos.getAllValues()).extracting(EventoBitacora::tipo).containsOnly(TipoEvento.CORTE_EXPIRADO);
        assertThat(eventos.getAllValues()).extracting(EventoBitacora::sectorId).containsExactlyInAnyOrder(MANGA, BOCAGRANDE);
    }

    /** El barrio que ya se restableció no vence con el corte: no hay nada que decir de él. */
    @Test
    void unBarrioYaCerradoNoRecibeEventoNiSeRecalcula() {
        CorteAgua conManga = corte("c1", OrigenCorte.VEEDOR, MANGA, BOCAGRANDE)
                .cerrarSector(MANGA, new CierreDeCorte(INICIO.plusSeconds(3600), OrigenEstado.VEEDOR, false));
        given(cortes.listarAbiertos()).willReturn(List.of(conManga));

        servicio.expirarVencidos();

        ArgumentCaptor<EventoBitacora> eventos = ArgumentCaptor.forClass(EventoBitacora.class);
        verify(registrarEvento).registrar(eventos.capture());
        assertThat(eventos.getValue().sectorId()).isEqualTo(BOCAGRANDE);
        verify(recalcular).recalcular(BOCAGRANDE);
        verify(recalcular, never()).recalcular(MANGA);
    }

    /** El corte y sus eventos son una unidad: si un evento falla, el corte no puede quedar expirado sin su cita. */
    @Test
    void guardaElCorteYAnexaSusEventosEnUnaSolaTransaccion() {
        given(cortes.listarAbiertos()).willReturn(List.of(corte("c1", OrigenCorte.INGESTA_IA, MANGA)));

        servicio.expirarVencidos();

        verify(transaccion).ejecutar(any());
    }

    @Test
    void recalculaElBarrioParaQueVuelvaASinDatos() {
        given(cortes.listarAbiertos()).willReturn(List.of(corte("c1", OrigenCorte.INGESTA_IA, MANGA)));

        servicio.expirarVencidos();

        verify(recalcular).recalcular(MANGA);
    }

    /**
     * La lista de abiertos se leyó antes: un veedor pudo anular o cerrar el corte desde entonces. Expirarlo con la
     * copia vieja pisaría esa decisión, así que se vuelve a leer dentro de la transacción y solo se expira si sigue abierto.
     */
    @Test
    void unCorteQueYaNoEstaAbiertoAlEscribirNoSeExpiraNiSeAnexaNada() {
        CorteAgua leido = corte("c1", OrigenCorte.INGESTA_IA, MANGA);
        given(cortes.listarAbiertos()).willReturn(List.of(leido));
        org.mockito.Mockito.doReturn(Optional.of(leido.anular("Registrado por error"))).when(cortes).buscarPorId(leido.id());

        int expirados = servicio.expirarVencidos();

        assertThat(expirados).isZero();
        verify(cortes, never()).guardar(any());
        verify(registrarEvento, never()).registrar(any());
        verify(recalcular, never()).recalcular(any());
    }

    @Test
    void unCorteQueYaNoExisteAlEscribirNoSeExpira() {
        CorteAgua leido = corte("c1", OrigenCorte.INGESTA_IA, MANGA);
        given(cortes.listarAbiertos()).willReturn(List.of(leido));
        org.mockito.Mockito.doReturn(Optional.empty()).when(cortes).buscarPorId(leido.id());

        assertThat(servicio.expirarVencidos()).isZero();

        verify(cortes, never()).guardar(any());
    }

    @Test
    void unCorteQueFallaNoImpideExpirarLosDemas() {
        CorteAgua malo = corte("malo", OrigenCorte.INGESTA_IA, MANGA);
        CorteAgua bueno = corte("bueno", OrigenCorte.INGESTA_IA, BOCAGRANDE);
        given(cortes.listarAbiertos()).willReturn(List.of(malo, bueno));
        doThrow(new IllegalStateException("Mongo caído")).when(cortes).guardar(malo.expirar());

        int expirados = servicio.expirarVencidos();

        assertThat(expirados).isEqualTo(1);
        verify(recalcular).recalcular(BOCAGRANDE);
    }

    @Test
    void siRecalcularFallaElCorteYaQuedoExpiradoYSeSigueConLosDemasBarrios() {
        given(cortes.listarAbiertos()).willReturn(List.of(corte("c1", OrigenCorte.VEEDOR, MANGA, BOCAGRANDE)));
        given(recalcular.recalcular(MANGA)).willThrow(new IllegalStateException("fallo"));

        int expirados = servicio.expirarVencidos();

        assertThat(expirados).isEqualTo(1);
        verify(recalcular).recalcular(BOCAGRANDE);
    }
}
