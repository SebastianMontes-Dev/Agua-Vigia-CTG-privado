package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.EstadoRevision;
import com.aguavigia.ctg.domain.EstadoServicio;
import com.aguavigia.ctg.domain.EventoBitacora;
import com.aguavigia.ctg.domain.PropuestaId;
import com.aguavigia.ctg.domain.PropuestaIngesta;
import com.aguavigia.ctg.domain.Sector;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.TipoEvento;
import com.aguavigia.ctg.domain.port.in.RegistrarEventoBitacoraUseCase;
import com.aguavigia.ctg.domain.OrigenCorte;
import com.aguavigia.ctg.domain.EstadoCorte;
import com.aguavigia.ctg.domain.port.out.CorteAguaRepository;
import com.aguavigia.ctg.domain.port.out.PropuestaIngestaRepository;
import com.aguavigia.ctg.domain.port.out.RelojPort;
import com.aguavigia.ctg.domain.port.out.SectorRepository;
import com.aguavigia.ctg.domain.port.out.TransaccionPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

class RevisarPropuestaIngestaServiceTest {

    private static final Instant AHORA = Instant.parse("2026-08-09T15:30:00Z");
    private static final PropuestaId ID = new PropuestaId("p-1");
    private static final SectorId MANGA = new SectorId("manga");

    private PropuestaIngestaRepository propuestas;
    private SectorRepository sectores;
    private RegistrarEventoBitacoraUseCase registrarEvento;
    private CorteAguaRepository cortes;
    private TransaccionPort transaccion;
    private RevisarPropuestaIngestaService servicio;

    @BeforeEach
    void montar() {
        propuestas = mock(PropuestaIngestaRepository.class);
        sectores = mock(SectorRepository.class);
        registrarEvento = mock(RegistrarEventoBitacoraUseCase.class);
        cortes = mock(CorteAguaRepository.class);
        RelojPort reloj = () -> AHORA;
        transaccion = spy(new TransaccionPasoDirecto());
        servicio = new RevisarPropuestaIngestaService(propuestas, sectores, registrarEvento, cortes, reloj, transaccion);

        given(propuestas.guardar(any())).willAnswer(invocacion -> invocacion.getArgument(0));
        given(propuestas.buscarPorId(ID)).willReturn(Optional.of(propuestaPendiente()));
    }

    /** Ejecuta la acción directamente, sin Mongo real — la atomicidad real se prueba en TransaccionMongoAdapterIntegrationTest. */
    private static class TransaccionPasoDirecto implements TransaccionPort {
        @Override
        public <T> T ejecutar(java.util.function.Supplier<T> accion) {
            return accion.get();
        }
    }

    /** Con ventana declarada: es lo único que puede fijar el estado actual de un barrio. */
    private PropuestaIngesta propuestaPendiente() {
        return new PropuestaIngesta(ID, MANGA, EstadoServicio.SIN_SERVICIO, "acuacar",
                "https://acuacar.com/x", "cita", 0.6, AHORA,
                INICIO_DECLARADO, INICIO_DECLARADO.plusSeconds(9 * 3600));
    }

    private static final Instant INICIO_DECLARADO = Instant.parse("2026-08-09T14:00:00Z");

    private void sectorEsta(EstadoServicio estado) {
        given(sectores.buscarPorId(MANGA)).willReturn(Optional.of(new Sector(MANGA, "Manga", 1000, estado)));
    }

    @Test
    void aprobarDebeAplicarElEstadoAlSectorYAnexarloALaBitacora() {
        sectorEsta(EstadoServicio.CON_SERVICIO);

        PropuestaIngesta resultado = servicio.aprobar(ID);

        assertThat(resultado.estadoRevision()).isEqualTo(EstadoRevision.APROBADA);
        verify(sectores).guardar(new Sector(MANGA, "Manga", 1000, EstadoServicio.SIN_SERVICIO));

        ArgumentCaptor<EventoBitacora> captor = ArgumentCaptor.forClass(EventoBitacora.class);
        verify(registrarEvento).registrar(captor.capture());
        assertThat(captor.getValue().tipo()).isEqualTo(TipoEvento.CORTE_DETECTADO_POR_INGESTA);
        assertThat(captor.getValue().sectorId()).isEqualTo(MANGA);
        // La bitácora fecha el hecho cuando el boletín dice que ocurre, no cuando corrió el colector:
        // sin eso, recuperar el histórico sella cientos de eventos con la hora de la recuperación.
        assertThat(captor.getValue().timestamp()).isEqualTo(INICIO_DECLARADO);
        // Redactado para un vecino: nombre del barrio, no su identificador, y sin el nombre del enum.
        assertThat(captor.getValue().descripcion())
                .isEqualTo("Suspensión del servicio en Manga, según Acuacar");
        assertThat(captor.getValue().estado()).isEqualTo(EstadoServicio.SIN_SERVICIO);
        assertThat(captor.getValue().urlOriginal()).isEqualTo("https://acuacar.com/x");
    }

    /**
     * `aprobar()` puede correr mucho después de `detectadaEn` — una propuesta de prensa espera
     * revisión del veedor días o semanas. Si para entonces la ventana declarada ya terminó, fijar el
     * `estadoPropuesto` original (calculado al detectarla) deja el barrio en SIN_SERVICIO por un
     * corte que ya se restableció. Debe fijar lo que la propia ventana dice que corresponde ahora
     * ({@link PropuestaIngesta#estadoVigenteEn}), no el valor congelado en la detección.
     */
    @Test
    void aprobarUnaPropuestaCuyaVentanaYaVencioDebeFijarConServicioYNoElEstadoDetectado() {
        sectorEsta(EstadoServicio.SIN_SERVICIO);
        given(propuestas.buscarPorId(ID)).willReturn(Optional.of(
                new PropuestaIngesta(ID, MANGA, EstadoServicio.SIN_SERVICIO, "acuacar",
                        "https://acuacar.com/x", "cita", 0.6, AHORA,
                        AHORA.minusSeconds(20 * 3600), AHORA.minusSeconds(3600))));

        servicio.aprobar(ID);

        verify(sectores).guardar(new Sector(MANGA, "Manga", 1000, EstadoServicio.CON_SERVICIO));

        ArgumentCaptor<EventoBitacora> captor = ArgumentCaptor.forClass(EventoBitacora.class);
        verify(registrarEvento).registrar(captor.capture());
        assertThat(captor.getValue().estado()).isEqualTo(EstadoServicio.CON_SERVICIO);
    }

    /**
     * Un boletín que no dice cuándo ocurre el corte no puede afirmar que el barrio está sin agua
     * hoy. Al recuperar el histórico, boletines de meses atrás sin ventana dejaron 128 barrios
     * pintados como sin servicio por cortes ya terminados.
     */
    @Test
    void noDebeFijarElEstadoActualSiElBoletinNoDeclaraCuandoOcurreElCorte() {
        sectorEsta(EstadoServicio.CON_SERVICIO);
        given(propuestas.buscarPorId(ID)).willReturn(Optional.of(
                new PropuestaIngesta(ID, MANGA, EstadoServicio.SIN_SERVICIO, "acuacar",
                        "https://acuacar.com/x", "cita", 0.6, AHORA)));

        PropuestaIngesta resultado = servicio.aprobar(ID);

        assertThat(resultado.estadoRevision()).isEqualTo(EstadoRevision.APROBADA);
        verify(sectores, never()).guardar(any());
        verify(registrarEvento, never()).registrar(any());
    }

    /**
     * Sin corte no hay estadísticas: `sectoresMasAfectados` y `cortesPorDiaDeSemana` agregan sobre
     * la colección de cortes, y la ingesta nunca la alimentaba. La escritura es atómica
     * (`anexarSectorAlCorte`, un `$addToSet`+`$setOnInsert` en Mongo), no un leer→modificar→guardar
     * en memoria: dos aprobaciones del mismo boletín procesadas a la vez ya no pueden pisarse la
     * lista de sectores calculada por separado (ver javadoc del servicio).
     */
    @Test
    void debeRegistrarElCorteDelBoletinCuandoDeclaraVentana() {
        sectorEsta(EstadoServicio.CON_SERVICIO);

        servicio.aprobar(ID);

        verify(cortes).anexarSectorAlCorte(any(), eq(MANGA), eq(INICIO_DECLARADO),
                eq(INICIO_DECLARADO.plusSeconds(9 * 3600)), eq("cita"), eq(OrigenCorte.INGESTA_IA),
                eq(EstadoCorte.ANUNCIADO));
    }

    /** Sin ventana declarada no hay corte que registrar: no se sabe cuándo empieza ni cuánto dura. */
    @Test
    void noDebeRegistrarCorteSiElBoletinNoDeclaraVentana() {
        sectorEsta(EstadoServicio.SIN_SERVICIO);
        given(propuestas.buscarPorId(ID)).willReturn(Optional.of(
                new PropuestaIngesta(ID, MANGA, EstadoServicio.CON_SERVICIO, "acuacar",
                        "https://acuacar.com/x", "cita", 0.6, AHORA)));

        servicio.aprobar(ID);

        verify(cortes, never()).anexarSectorAlCorte(any(), any(), any(), any(), any(), any(), any());
    }

    /** Restablecer sí falla hacia el lado seguro: afirmar que hay agua no inventa una emergencia. */
    @Test
    void debeFijarConServicioAunSinVentanaDeclarada() {
        sectorEsta(EstadoServicio.SIN_SERVICIO);
        given(propuestas.buscarPorId(ID)).willReturn(Optional.of(
                new PropuestaIngesta(ID, MANGA, EstadoServicio.CON_SERVICIO, "acuacar",
                        "https://acuacar.com/x", "cita", 0.6, AHORA)));

        servicio.aprobar(ID);

        verify(sectores).guardar(new Sector(MANGA, "Manga", 1000, EstadoServicio.CON_SERVICIO));
    }

    @Test
    void aprobarUnaPropuestaCuyoEstadoYaRigeNoDebeDuplicarElEventoDeBitacora() {
        sectorEsta(EstadoServicio.SIN_SERVICIO);

        PropuestaIngesta resultado = servicio.aprobar(ID);

        assertThat(resultado.estadoRevision()).isEqualTo(EstadoRevision.APROBADA);
        verify(sectores, never()).guardar(any());
        verify(registrarEvento, never()).registrar(any());
    }

    /** ADR-073: el boletín aprobado sostiene el estado vigente aunque no lo cambie. */
    @Test
    void aprobarUnaPropuestaCuyoEstadoYaRigeDebeVerificarElEstado() {
        sectorEsta(EstadoServicio.SIN_SERVICIO);

        servicio.aprobar(ID);

        verify(sectores).confirmarEstado(MANGA, EstadoServicio.SIN_SERVICIO);
    }

    @Test
    void unBoletinSinVentanaNoDebeVerificarElEstado() {
        sectorEsta(EstadoServicio.SIN_SERVICIO);
        given(propuestas.buscarPorId(ID)).willReturn(Optional.of(
                new PropuestaIngesta(ID, MANGA, EstadoServicio.SIN_SERVICIO, "acuacar",
                        "https://acuacar.com/x", "cita", 0.6, AHORA)));

        servicio.aprobar(ID);

        verify(sectores, never()).confirmarEstado(any(), any());
    }

    @Test
    void debeGuardarElSectorYRegistrarElEventoEnUnaSolaTransaccion() {
        sectorEsta(EstadoServicio.CON_SERVICIO);

        servicio.aprobar(ID);

        verify(transaccion).ejecutar(any());
    }

    @Test
    void debePropagarLaFallaSiElRegistroDeEventoFallaParaQueLaTransaccionRevierta() {
        sectorEsta(EstadoServicio.CON_SERVICIO);
        org.mockito.Mockito.doThrow(new IllegalStateException("Mongo caído al anexar el evento"))
                .when(registrarEvento).registrar(any());

        assertThatThrownBy(() -> servicio.aprobar(ID)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void descartarNoDebeTocarElSectorNiLaBitacora() {
        PropuestaIngesta resultado = servicio.descartar(ID);

        assertThat(resultado.estadoRevision()).isEqualTo(EstadoRevision.DESCARTADA);
        verify(sectores, never()).guardar(any());
        verify(registrarEvento, never()).registrar(any());
    }

    @Test
    void debeRechazarRevisarUnaPropuestaQueNoExiste() {
        given(propuestas.buscarPorId(any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.aprobar(new PropuestaId("no-existe")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> servicio.descartar(new PropuestaId("no-existe")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** 409 y no 500: la petición está bien formada, es el estado del sistema lo que la impide. */
    @Test
    void aprobarDebeFallarConConflictoSiElSectorYaNoExiste() {
        given(sectores.buscarPorId(MANGA)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.aprobar(ID)).isInstanceOf(IllegalStateException.class);
        verify(propuestas, never()).guardar(any());
    }
}
