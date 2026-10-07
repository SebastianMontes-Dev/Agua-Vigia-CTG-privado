package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.CierreDeCorte;
import com.aguavigia.ctg.domain.CorteAgua;
import com.aguavigia.ctg.domain.CorteId;
import com.aguavigia.ctg.domain.EstadoCorte;
import com.aguavigia.ctg.domain.EstadoPublicado;
import com.aguavigia.ctg.domain.EstadoServicio;
import com.aguavigia.ctg.domain.EstrategiaConsenso;
import com.aguavigia.ctg.domain.EventoBitacora;
import com.aguavigia.ctg.domain.HuellaDispositivo;
import com.aguavigia.ctg.domain.MarcasDeEstado;
import com.aguavigia.ctg.domain.NivelDeVerificacion;
import com.aguavigia.ctg.domain.OrigenCorte;
import com.aguavigia.ctg.domain.OrigenEstado;
import com.aguavigia.ctg.domain.PropuestaId;
import com.aguavigia.ctg.domain.PropuestaIngesta;
import com.aguavigia.ctg.domain.ReglasDeEstado;
import com.aguavigia.ctg.domain.ResultadoDeRecalculo;
import com.aguavigia.ctg.domain.ReporteCiudadano;
import com.aguavigia.ctg.domain.ReporteId;
import com.aguavigia.ctg.domain.RespaldoVecinal;
import com.aguavigia.ctg.domain.ResolutorDeEstadoSector;
import com.aguavigia.ctg.domain.Sector;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.TipoEvento;
import com.aguavigia.ctg.domain.TipoReporte;
import com.aguavigia.ctg.domain.VentanaTiempo;
import com.aguavigia.ctg.domain.port.in.RegistrarEventoBitacoraUseCase;
import com.aguavigia.ctg.domain.port.out.CorteAguaRepository;
import com.aguavigia.ctg.domain.port.out.MetricasDelSistemaPort;
import com.aguavigia.ctg.domain.port.out.PropuestaIngestaRepository;
import com.aguavigia.ctg.domain.port.out.ReporteCiudadanoRepository;
import com.aguavigia.ctg.domain.port.out.SectorRepository;
import com.aguavigia.ctg.domain.port.out.TransaccionPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * El único escritor del estado de un barrio: reúne lo que cada fuente afirma, deja que el resolutor
 * decida y escribe una sola vez. Los repositorios son dobles; el resolutor es el real, porque lo que
 * se prueba aquí es el cableado entre las fuentes y él.
 */
class RecalcularSectorServiceTest {

    private static final SectorId MANGA = new SectorId("manga");
    private static final Instant INICIO = Instant.parse("2026-08-21T14:00:00Z");
    private static final Instant FIN = Instant.parse("2026-08-21T23:00:00Z");
    private static final Duration VENTANA_CONSENSO = Duration.ofMinutes(30);

    private SectorRepository sectores;
    private CorteAguaRepository cortes;
    private PropuestaIngestaRepository propuestas;
    private ReporteCiudadanoRepository reportes;
    private RegistrarEventoBitacoraUseCase registrarEvento;
    private TransaccionPort transaccion;
    private Instant ahora;
    private RecalcularSectorService servicio;

    @BeforeEach
    void montar() {
        sectores = mock(SectorRepository.class);
        cortes = mock(CorteAguaRepository.class);
        propuestas = mock(PropuestaIngestaRepository.class);
        reportes = mock(ReporteCiudadanoRepository.class);
        registrarEvento = mock(RegistrarEventoBitacoraUseCase.class);
        transaccion = spy(new TransaccionPort() {
            @Override
            public <T> T ejecutar(java.util.function.Supplier<T> accion) {
                return accion.get();
            }
        });
        ahora = INICIO.plusSeconds(3600);

        given(cortes.listarPorSector(MANGA)).willReturn(List.of());
        given(propuestas.listarAprobadasPorSector(MANGA)).willReturn(List.of());
        // Releer un corte dentro de la transacción devuelve lo que el escenario de la prueba haya dejado en el barrio.
        given(cortes.buscarPorId(any())).willAnswer(invocacion -> cortes.listarPorSector(MANGA).stream()
                .filter(corte -> corte.id().equals(invocacion.getArgument(0))).findFirst());
        given(reportes.contarVotosRecientes(any(), any())).willReturn(Map.of());
        given(sectores.publicarSiEs(any(), any(), any(), any())).willReturn(true);
        given(sectores.abrirDisputaSiEs(any(), any(), any())).willReturn(true);

        EstrategiaConsenso umbralDeTres = sector -> 3;
        servicio = new RecalcularSectorService(sectores, cortes, propuestas, reportes, umbralDeTres,
                new ResolutorDeEstadoSector(ReglasDeEstado.porDefecto()), registrarEvento,
                () -> ahora, transaccion, VENTANA_CONSENSO, 2);
    }

    // --- ayudantes ---------------------------------------------------------------------------------

    private EstadoPublicado recalcular() {
        return servicio.recalcular(MANGA).publicado();
    }

    private void dadoUnSector(EstadoServicio estado) {
        given(sectores.buscarPorId(MANGA)).willReturn(Optional.of(new Sector(MANGA, "MANGA", 1000, estado)));
    }

    private void dadoUnSector(EstadoServicio estado, Instant actualizadoEn, Instant verificadoEn, MarcasDeEstado marcas) {
        given(sectores.buscarPorId(MANGA)).willReturn(
                Optional.of(new Sector(MANGA, "MANGA", 1000, estado, actualizadoEn, verificadoEn, marcas)));
    }

    private static PropuestaIngesta boletin(String fuente, EstadoServicio estado, Instant inicio, Instant fin) {
        return new PropuestaIngesta(new PropuestaId("p-" + fuente), MANGA, estado, fuente,
                "https://acuacar.com/2854", "cita", 0.85, INICIO.minusSeconds(3600), inicio, fin).aprobar();
    }

    private static PropuestaIngesta boletinDeAcuacar() {
        return boletin("acuacar", EstadoServicio.SIN_SERVICIO, INICIO, FIN);
    }

    private static CorteAgua corteDeIngesta(PropuestaIngesta propuesta, CierreDeCorte cierre) {
        CorteAgua abierto = CorteAgua.builder()
                .id(propuesta.idDelCorte())
                .sectoresAfectados(List.of(MANGA))
                .inicio(propuesta.inicioDeclarado())
                .finPrometido(propuesta.finPrometido())
                .causa("Habrá suspensión del servicio")
                .origen(OrigenCorte.INGESTA_IA)
                .build();
        return cierre == null ? abierto : abierto.cerrarSector(MANGA, cierre);
    }

    private static CorteAgua corteDelVeedor() {
        return CorteAgua.builder()
                .id(new CorteId("corte-veedor"))
                .sectoresAfectados(List.of(MANGA))
                .inicio(INICIO)
                .finPrometido(FIN)
                .causa("Corte del veedor")
                .origen(OrigenCorte.VEEDOR)
                .estado(EstadoCorte.CONFIRMADO)
                .build();
    }

    private static ReporteCiudadano reporte(String id, TipoReporte tipo, Instant cuando) {
        return new ReporteCiudadano(new ReporteId(id), MANGA, tipo, null, new HuellaDispositivo("h-" + id), cuando)
                .conIdentidad(NivelDeVerificacion.CUENTA_VERIFICADA, "red-" + id);
    }

    private void dadosLosVotos(TipoReporte tipo, long votos, ReporteCiudadano... sustento) {
        given(reportes.contarVotosRecientes(any(), any())).willReturn(Map.of(tipo, votos));
        given(reportes.listarRecientesPorSector(any(), any())).willReturn(List.of(sustento));
    }

    private static MarcasDeEstado marcasDeAcuacar() {
        return new MarcasDeEstado(OrigenEstado.ACUACAR, new VentanaTiempo(INICIO, FIN), false, false, 0, null);
    }

    private EventoBitacora eventoRegistrado() {
        ArgumentCaptor<EventoBitacora> captor = ArgumentCaptor.forClass(EventoBitacora.class);
        verify(registrarEvento).registrar(captor.capture());
        return captor.getValue();
    }

    /** Con 0 redes mínimas la composición del quórum no exigiría nada: la configuración errónea debe fallar al arrancar. */
    @Test
    void rechazaUnMinimoDeRedesMenorQueUno() {
        for (int invalido : new int[] {0, -1}) {
            assertThatThrownBy(() -> new RecalcularSectorService(sectores, cortes, propuestas, reportes, sector -> 3,
                    new ResolutorDeEstadoSector(ReglasDeEstado.porDefecto()), registrarEvento,
                    () -> ahora, transaccion, VENTANA_CONSENSO, invalido))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("redes");
        }
    }

    @Test
    void unBarrioInexistenteSeRechaza() {
        given(sectores.buscarPorId(MANGA)).willReturn(Optional.empty());

        assertThatThrownBy(() -> recalcular()).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sinNingunaFuenteYSinEstadoNoEscribeNada() {
        dadoUnSector(null);

        EstadoPublicado publicado = recalcular();

        assertThat(publicado.estado()).isNull();
        verify(sectores, never()).publicarSiEs(any(), any(), any(), any());
        verify(registrarEvento, never()).registrar(any());
    }

    @Nested
    class FuenteOficial {

        @Test
        void unBoletinDeAcuacarEnCursoPublicaSinServicioYLoAnexaALaBitacora() {
            dadoUnSector(EstadoServicio.CON_SERVICIO);
            given(propuestas.listarAprobadasPorSector(MANGA)).willReturn(List.of(boletinDeAcuacar()));

            EstadoPublicado publicado = recalcular();

            assertThat(publicado.estado()).isEqualTo(EstadoServicio.SIN_SERVICIO);
            verify(sectores).publicarSiEs(MANGA, EstadoServicio.CON_SERVICIO, EstadoServicio.SIN_SERVICIO, marcasDeAcuacar());
            EventoBitacora evento = eventoRegistrado();
            assertThat(evento.tipo()).isEqualTo(TipoEvento.CORTE_DETECTADO_POR_INGESTA);
            assertThat(evento.estado()).isEqualTo(EstadoServicio.SIN_SERVICIO);
            assertThat(evento.urlOriginal()).isEqualTo("https://acuacar.com/2854");
        }

        /**
         * La ingesta etiqueta CORTE_PROGRAMADO un boletín cuya ventana aún no empieza (PipelineOrquestador). Esa etiqueta es del
         * momento, no de la ventana: la propuesta aprobada debe seguir afirmando un corte, y el resolutor lo muestra programado
         * hasta que empiece. Antes la ventana rechazaba la etiqueta y el boletín anunciado para mañana nunca llegaba al mapa.
         */
        @Test
        void unBoletinAnunciadoAntesDeQueEmpieceSuVentanaPublicaCorteProgramado() {
            ahora = INICIO.minusSeconds(7200);
            dadoUnSector(EstadoServicio.CON_SERVICIO);
            given(propuestas.listarAprobadasPorSector(MANGA)).willReturn(
                    List.of(boletin("acuacar", EstadoServicio.CORTE_PROGRAMADO, INICIO, FIN)));

            EstadoPublicado publicado = recalcular();

            assertThat(publicado.estado()).isEqualTo(EstadoServicio.CORTE_PROGRAMADO);
        }

        /** Y la misma propuesta, ya dentro de la ventana, es un corte en curso. */
        @Test
        void esaMismaPropuestaDentroDeLaVentanaPublicaSinServicio() {
            dadoUnSector(EstadoServicio.CORTE_PROGRAMADO);
            given(propuestas.listarAprobadasPorSector(MANGA)).willReturn(
                    List.of(boletin("acuacar", EstadoServicio.CORTE_PROGRAMADO, INICIO, FIN)));

            EstadoPublicado publicado = recalcular();

            assertThat(publicado.estado()).isEqualTo(EstadoServicio.SIN_SERVICIO);
        }

        /** El par estado + evento es una sola unidad: si el evento falla, el estado no puede quedar publicado. */
        @Test
        void elEstadoYSuEventoVanEnUnaSolaTransaccion() {
            dadoUnSector(EstadoServicio.CON_SERVICIO);
            given(propuestas.listarAprobadasPorSector(MANGA)).willReturn(List.of(boletinDeAcuacar()));

            recalcular();

            verify(transaccion).ejecutar(any());
        }

        @Test
        void siElEventoFallaLaFallaSePropagaParaQueLaTransaccionRevierta() {
            dadoUnSector(EstadoServicio.CON_SERVICIO);
            given(propuestas.listarAprobadasPorSector(MANGA)).willReturn(List.of(boletinDeAcuacar()));
            doThrow(new IllegalStateException("Mongo caído")).when(registrarEvento).registrar(any());

            assertThatThrownBy(() -> recalcular()).isInstanceOf(IllegalStateException.class);
        }

        /** Dos recálculos simultáneos: solo el que gana la escritura anexa el evento. */
        @Test
        void siOtroProcesoGanoLaCarreraNoAnexaNingunEvento() {
            dadoUnSector(EstadoServicio.CON_SERVICIO);
            given(propuestas.listarAprobadasPorSector(MANGA)).willReturn(List.of(boletinDeAcuacar()));
            given(sectores.publicarSiEs(any(), any(), any(), any())).willReturn(false);

            recalcular();

            verify(registrarEvento, never()).registrar(any());
        }

        @Test
        void siElEstadoYLasMarcasYaSonLasQueCorrespondenNoEscribeNiAnexaNada() {
            dadoUnSector(EstadoServicio.SIN_SERVICIO, ahora, ahora, marcasDeAcuacar());
            given(propuestas.listarAprobadasPorSector(MANGA)).willReturn(List.of(boletinDeAcuacar()));

            recalcular();

            verify(sectores, never()).publicarSiEs(any(), any(), any(), any());
            verify(registrarEvento, never()).registrar(any());
        }

        /** Que la promesa venza o se abra una disputa cambia una marca, no el estado: ni correo ni bitácora. */
        @Test
        void siSoloCambianLasMarcasLasEscribeSinAvisarNiAnexar() {
            ahora = FIN.plusSeconds(60);
            dadoUnSector(EstadoServicio.SIN_SERVICIO, INICIO, INICIO, marcasDeAcuacar());
            given(propuestas.listarAprobadasPorSector(MANGA)).willReturn(List.of(boletinDeAcuacar()));

            recalcular();

            MarcasDeEstado porConfirmar = new MarcasDeEstado(OrigenEstado.ACUACAR, new VentanaTiempo(INICIO, FIN),
                    true, false, 0, null);
            verify(sectores).publicarSiEs(MANGA, EstadoServicio.SIN_SERVICIO, EstadoServicio.SIN_SERVICIO, porConfirmar);
            verify(registrarEvento, never()).registrar(any());
        }

        @Test
        void unBoletinSinVentanaNoAfirmaNadaDelPresente() {
            dadoUnSector(null);
            PropuestaIngesta historica = new PropuestaIngesta(new PropuestaId("vieja"), MANGA,
                    EstadoServicio.SIN_SERVICIO, "acuacar", "https://acuacar.com/1", "cita", 0.85, INICIO).aprobar();
            given(propuestas.listarAprobadasPorSector(MANGA)).willReturn(List.of(historica));

            assertThat(recalcular().estado()).isNull();
            verify(sectores, never()).publicarSiEs(any(), any(), any(), any());
        }

        @Test
        void unBoletinDeRestablecimientoDeAcuacarDejaElBarrioConServicio() {
            dadoUnSector(EstadoServicio.SIN_SERVICIO, INICIO, INICIO, marcasDeAcuacar());
            PropuestaIngesta restablecimiento = new PropuestaIngesta(new PropuestaId("normal"), MANGA,
                    EstadoServicio.CON_SERVICIO, "acuacar", "https://acuacar.com/2860", "servicio restablecido", 0.85,
                    ahora.minusSeconds(60)).aprobar();
            given(propuestas.listarAprobadasPorSector(MANGA)).willReturn(List.of(boletinDeAcuacar(), restablecimiento));

            EstadoPublicado publicado = recalcular();

            assertThat(publicado.estado()).isEqualTo(EstadoServicio.CON_SERVICIO);
            assertThat(publicado.origen()).isEqualTo(OrigenEstado.ACUACAR);
            assertThat(eventoRegistrado().urlOriginal()).isEqualTo("https://acuacar.com/2860");
        }

        /** Las buenas noticias piden una fuente más fuerte: una nota de prensa no devuelve el servicio a un barrio. */
        @Test
        void unaNotaDePrensaQueDiceQueVolvioElAguaNoCuenta() {
            dadoUnSector(EstadoServicio.SIN_SERVICIO, INICIO, INICIO, marcasDeAcuacar());
            PropuestaIngesta deLaPrensa = new PropuestaIngesta(new PropuestaId("prensa"), MANGA,
                    EstadoServicio.CON_SERVICIO, "zona-cero", "https://zonacero.com/x", "ya hay agua", 0.6,
                    ahora.minusSeconds(60)).aprobar();
            given(propuestas.listarAprobadasPorSector(MANGA)).willReturn(List.of(boletinDeAcuacar(), deLaPrensa));

            assertThat(recalcular().estado()).isEqualTo(EstadoServicio.SIN_SERVICIO);
        }

        @Test
        void unBoletinDePrensaPublicaConOrigenPrensa() {
            dadoUnSector(null);
            given(propuestas.listarAprobadasPorSector(MANGA))
                    .willReturn(List.of(boletin("zona-cero", EstadoServicio.SIN_SERVICIO, INICIO, FIN)));

            EstadoPublicado publicado = recalcular();

            assertThat(publicado.origen()).isEqualTo(OrigenEstado.PRENSA);
            assertThat(eventoRegistrado().tipo()).isEqualTo(TipoEvento.CORTE_DETECTADO_POR_INGESTA);
        }

        /** El cierre del veedor vive en el corte que creó la aprobación, y se casa con el boletín por su id. */
        @Test
        void elCierreDelVeedorEnElCorteDelBoletinDejaElBarrioConServicio() {
            dadoUnSector(EstadoServicio.SIN_SERVICIO, INICIO, INICIO, marcasDeAcuacar());
            PropuestaIngesta boletin = boletinDeAcuacar();
            given(propuestas.listarAprobadasPorSector(MANGA)).willReturn(List.of(boletin));
            given(cortes.listarPorSector(MANGA)).willReturn(List.of(corteDeIngesta(boletin,
                    new CierreDeCorte(INICIO.plus(Duration.ofMinutes(30)), OrigenEstado.VEEDOR, false))));

            EstadoPublicado publicado = recalcular();

            assertThat(publicado.estado()).isEqualTo(EstadoServicio.CON_SERVICIO);
            assertThat(publicado.origen()).isEqualTo(OrigenEstado.VEEDOR);
            // El veedor ya dejó su propio evento al cerrar el corte: repetirlo aquí lo duplicaría.
            verify(registrarEvento, never()).registrar(any());
        }
    }

    @Nested
    class CorteDelVeedor {

        @Test
        void unCorteDelVeedorAbiertoPublicaSinServicioConOrigenVeedorYSinEventoPropio() {
            dadoUnSector(null);
            given(cortes.listarPorSector(MANGA)).willReturn(List.of(corteDelVeedor()));

            EstadoPublicado publicado = recalcular();

            assertThat(publicado.estado()).isEqualTo(EstadoServicio.SIN_SERVICIO);
            assertThat(publicado.origen()).isEqualTo(OrigenEstado.VEEDOR);
            verify(registrarEvento, never()).registrar(any());
        }

        @Test
        void unCorteAnuladoOExpiradoNoAfirmaNada() {
            dadoUnSector(null);
            given(cortes.listarPorSector(MANGA)).willReturn(List.of(
                    corteDelVeedor().anular("Registrado por error"),
                    CorteAgua.builder().id(new CorteId("viejo")).sectoresAfectados(List.of(MANGA))
                            .inicio(INICIO).finPrometido(FIN).causa("x").origen(OrigenCorte.VEEDOR).build().expirar()));

            assertThat(recalcular().estado()).isNull();
        }

        /** El override del veedor puede traer su propia caducidad: pasada, el barrio deja de afirmarse por él. */
        @Test
        void unCorteDelVeedorCaducadoNoAfirmaNada() {
            dadoUnSector(null);
            CorteAgua conCaducidad = CorteAgua.builder().id(new CorteId("caduca")).sectoresAfectados(List.of(MANGA))
                    .inicio(INICIO).finPrometido(FIN).caducaEn(ahora.minusSeconds(1))
                    .causa("x").origen(OrigenCorte.VEEDOR).build();
            given(cortes.listarPorSector(MANGA)).willReturn(List.of(conCaducidad));

            assertThat(recalcular().estado()).isNull();
        }

        @Test
        void unCorteDelVeedorConCaducidadFuturaSigueAfirmando() {
            dadoUnSector(null);
            CorteAgua conCaducidad = CorteAgua.builder().id(new CorteId("caduca")).sectoresAfectados(List.of(MANGA))
                    .inicio(INICIO).finPrometido(FIN).caducaEn(ahora.plusSeconds(60))
                    .causa("x").origen(OrigenCorte.VEEDOR).build();
            given(cortes.listarPorSector(MANGA)).willReturn(List.of(conCaducidad));

            assertThat(recalcular().estado()).isEqualTo(EstadoServicio.SIN_SERVICIO);
        }

        @Test
        void unCorteCerradoSoloEnOtroBarrioSigueSosteniendoEste() {
            SectorId bocagrande = new SectorId("bocagrande");
            dadoUnSector(null);
            CorteAgua dosBarrios = CorteAgua.builder().id(new CorteId("c2")).sectoresAfectados(List.of(MANGA, bocagrande))
                    .inicio(INICIO).finPrometido(FIN).causa("x").origen(OrigenCorte.VEEDOR).build()
                    .cerrarSector(bocagrande, new CierreDeCorte(INICIO.plusSeconds(1800), OrigenEstado.VEEDOR, false));
            given(cortes.listarPorSector(MANGA)).willReturn(List.of(dosBarrios));

            assertThat(recalcular().estado()).isEqualTo(EstadoServicio.SIN_SERVICIO);
        }
    }

    @Nested
    class Vecinos {

        @Test
        void unQuorumDeVecinosPublicaSuEstadoConSuRespaldoYAnexaElConsenso() {
            dadoUnSector(EstadoServicio.CON_SERVICIO);
            dadosLosVotos(TipoReporte.SIN_AGUA, 3,
                    reporte("r1", TipoReporte.SIN_AGUA, ahora.minusSeconds(300)),
                    reporte("r2", TipoReporte.SIN_AGUA, ahora.minusSeconds(200)),
                    reporte("r3", TipoReporte.SIN_AGUA, ahora.minusSeconds(100)));

            EstadoPublicado publicado = recalcular();

            assertThat(publicado.estado()).isEqualTo(EstadoServicio.SIN_SERVICIO);
            assertThat(publicado.respaldo()).isEqualTo(new RespaldoVecinal(3, 3));
            MarcasDeEstado marcas = new MarcasDeEstado(OrigenEstado.VECINOS, null, false, false, 0, new RespaldoVecinal(3, 3));
            verify(sectores).publicarSiEs(MANGA, EstadoServicio.CON_SERVICIO, EstadoServicio.SIN_SERVICIO, marcas);
            EventoBitacora evento = eventoRegistrado();
            assertThat(evento.tipo()).isEqualTo(TipoEvento.CORTE_CONFIRMADO_POR_CIUDADANOS);
            assertThat(evento.fuente()).isEqualTo(OrigenEstado.VECINOS);
            assertThat(evento.respaldo()).isEqualTo(new RespaldoVecinal(3, 3));
            assertThat(evento.reportesSustento()).containsExactlyInAnyOrder(
                    new ReporteId("r1"), new ReporteId("r2"), new ReporteId("r3"));
        }

        // --- composición del quórum (D9, D16) ---

        private ReporteCiudadano deLaRed(String id, NivelDeVerificacion nivel, String red) {
            return new ReporteCiudadano(new ReporteId(id), MANGA, TipoReporte.SIN_AGUA, null,
                    new HuellaDispositivo("h-" + id), ahora.minusSeconds(100)).conIdentidad(nivel, red);
        }

        /** Escenario 10: un vecino verificado más dos anónimos de redes distintas bastan en un barrio de umbral 3. */
        @Test
        void unQuorumConUnTercioVerificadoYDosRedesCambiaElEstado() {
            dadoUnSector(null);
            dadosLosVotos(TipoReporte.SIN_AGUA, 3,
                    deLaRed("r1", NivelDeVerificacion.CUENTA_VERIFICADA, "red-a"),
                    deLaRed("r2", NivelDeVerificacion.NINGUNA, "red-b"),
                    deLaRed("r3", NivelDeVerificacion.NINGUNA, "red-b"));

            EstadoPublicado publicado = recalcular();

            assertThat(publicado.estado()).isEqualTo(EstadoServicio.SIN_SERVICIO);
            assertThat(publicado.origen()).isEqualTo(OrigenEstado.VECINOS);
        }

        /** Escenario 9: tres huellas desde una misma red no cambian el mapa, aunque lleguen al umbral. */
        @Test
        void tresReportesDeUnaSolaRedNoCambianElEstado() {
            dadoUnSector(null);
            dadosLosVotos(TipoReporte.SIN_AGUA, 3,
                    deLaRed("r1", NivelDeVerificacion.CUENTA_VERIFICADA, "red-a"),
                    deLaRed("r2", NivelDeVerificacion.CUENTA_VERIFICADA, "red-a"),
                    deLaRed("r3", NivelDeVerificacion.CUENTA_VERIFICADA, "red-a"));

            assertThat(recalcular().estado()).isNull();
            verify(sectores, never()).publicarSiEs(any(), any(), any(), any());
        }

        /** Tres anónimos sin ninguna prueba de ubicación, aunque de redes distintas, no mueven el mapa solos. */
        @Test
        void tresAnonimosSinVerificacionNoCambianElEstado() {
            dadoUnSector(null);
            dadosLosVotos(TipoReporte.SIN_AGUA, 3,
                    deLaRed("r1", NivelDeVerificacion.NINGUNA, "red-a"),
                    deLaRed("r2", NivelDeVerificacion.NINGUNA, "red-b"),
                    deLaRed("r3", NivelDeVerificacion.NINGUNA, "red-c"));

            assertThat(recalcular().estado()).isNull();
        }

        /** Escenario 18: con `redes-minimas=1`, dicho abiertamente, una sala con un solo WiFi sí alcanza el quórum. */
        @Test
        void conUnaSolaRedMinimaUnaSolaRedBasta() {
            servicio = new RecalcularSectorService(sectores, cortes, propuestas, reportes, sector -> 3,
                    new ResolutorDeEstadoSector(ReglasDeEstado.porDefecto()), registrarEvento,
                    () -> ahora, transaccion, VENTANA_CONSENSO, 1);
            dadoUnSector(null);
            dadosLosVotos(TipoReporte.SIN_AGUA, 3,
                    deLaRed("r1", NivelDeVerificacion.CUENTA_VERIFICADA, "red-a"),
                    deLaRed("r2", NivelDeVerificacion.CUENTA_VERIFICADA, "red-a"),
                    deLaRed("r3", NivelDeVerificacion.CUENTA_VERIFICADA, "red-a"));

            assertThat(recalcular().estado()).isEqualTo(EstadoServicio.SIN_SERVICIO);
        }

        /**
         * Un quórum fresco sin composición válida no puede borrar lo que el barrio ya recuerda con una composición
         * que sí lo era: el estado sostenido por los vecinos sigue, y no se publica nada nuevo.
         */
        @Test
        void unQuorumFrescoDeComposicionInvalidaNoBorraLoQueElBarrioRecuerda() {
            MarcasDeEstado deLosVecinos = new MarcasDeEstado(OrigenEstado.VECINOS, null, false, false, 0, new RespaldoVecinal(3, 3));
            dadoUnSector(EstadoServicio.SIN_SERVICIO, ahora.minus(Duration.ofHours(2)), ahora.minus(Duration.ofHours(2)), deLosVecinos);
            dadosLosVotos(TipoReporte.SIN_AGUA, 3,
                    deLaRed("r1", NivelDeVerificacion.NINGUNA, "red-a"),
                    deLaRed("r2", NivelDeVerificacion.NINGUNA, "red-a"),
                    deLaRed("r3", NivelDeVerificacion.NINGUNA, "red-a"));

            EstadoPublicado publicado = recalcular();

            assertThat(publicado.estado()).isEqualTo(EstadoServicio.SIN_SERVICIO);
            assertThat(publicado.respaldo()).isEqualTo(new RespaldoVecinal(3, 3));
        }

        /** En una avería masiva no se cargan los reportes de la ventana en cada petición: primero se cuentan. */
        @Test
        void sinVotosSuficientesNoCargaLosReportes() {
            dadoUnSector(null);
            given(reportes.contarVotosRecientes(any(), any())).willReturn(Map.of(TipoReporte.SIN_AGUA, 1L));

            assertThat(recalcular().estado()).isNull();

            verify(reportes, never()).listarRecientesPorSector(any(), any());
        }

        @Test
        void cadaDispositivoCuentaUnaVezConSuReporteMasReciente() {
            dadoUnSector(null);
            ReporteCiudadano viejoDeH1 = new ReporteCiudadano(new ReporteId("a"), MANGA, TipoReporte.SERVICIO_RESTABLECIDO,
                    null, new HuellaDispositivo("h1"), ahora.minusSeconds(900))
                    .conIdentidad(NivelDeVerificacion.CUENTA_VERIFICADA, "red-a");
            ReporteCiudadano nuevoDeH1 = new ReporteCiudadano(new ReporteId("b"), MANGA, TipoReporte.SIN_AGUA,
                    null, new HuellaDispositivo("h1"), ahora.minusSeconds(100))
                    .conIdentidad(NivelDeVerificacion.CUENTA_VERIFICADA, "red-b");
            dadosLosVotos(TipoReporte.SIN_AGUA, 3, nuevoDeH1, viejoDeH1,
                    reporte("r2", TipoReporte.SIN_AGUA, ahora.minusSeconds(200)),
                    reporte("r3", TipoReporte.SIN_AGUA, ahora.minusSeconds(300)));

            recalcular();

            assertThat(eventoRegistrado().reportesSustento()).containsExactlyInAnyOrder(
                    new ReporteId("b"), new ReporteId("r2"), new ReporteId("r3"));
        }

        /** El quórum deja de ser visible a los 30 minutos, pero el estado que produjo se recuerda en el barrio. */
        @Test
        void unEstadoQueSostienenLosVecinosSeRecuerdaSinReportesNuevos() {
            MarcasDeEstado deLosVecinos = new MarcasDeEstado(OrigenEstado.VECINOS, null, false, false, 0, new RespaldoVecinal(3, 3));
            dadoUnSector(EstadoServicio.SIN_SERVICIO, ahora.minus(Duration.ofHours(2)), ahora.minus(Duration.ofHours(2)), deLosVecinos);

            EstadoPublicado publicado = recalcular();

            assertThat(publicado.estado()).isEqualTo(EstadoServicio.SIN_SERVICIO);
            assertThat(publicado.origen()).isEqualTo(OrigenEstado.VECINOS);
            verify(sectores, never()).publicarSiEs(any(), any(), any(), any());
            // Sin reportes nuevos nadie lo verificó: renovar la marca lo haría inmortal.
            verify(sectores, never()).confirmarEstado(any(), any());
        }

        /**
         * Los reportes que fijaron el estado envejecen y salen de la ventana de uno en uno: los pocos que quedan
         * forman un quórum fresco que ya no llega al umbral. Eso no puede borrar lo que el barrio ya recuerda:
         * el estado caduca a las 24 h sin reportes nuevos, no cuando el primer reporte sale de la ventana.
         */
        @Test
        void unQuorumFrescoQueNoLlegaAlUmbralNoBorraLaMemoriaDelBarrio() {
            MarcasDeEstado deLosVecinos = new MarcasDeEstado(OrigenEstado.VECINOS, null, false, false, 0, new RespaldoVecinal(3, 3));
            dadoUnSector(EstadoServicio.SIN_SERVICIO, ahora.minus(Duration.ofHours(1)), ahora.minus(Duration.ofHours(1)), deLosVecinos);
            dadosLosVotos(TipoReporte.SIN_AGUA, 2,
                    reporte("r2", TipoReporte.SIN_AGUA, ahora.minusSeconds(200)),
                    reporte("r3", TipoReporte.SIN_AGUA, ahora.minusSeconds(100)));

            EstadoPublicado publicado = recalcular();

            assertThat(publicado.estado()).isEqualTo(EstadoServicio.SIN_SERVICIO);
            assertThat(publicado.origen()).isEqualTo(OrigenEstado.VECINOS);
            verify(sectores, never()).publicarSiEs(any(), any(), any(), any());
        }

        @Test
        void unEstadoDeLosVecinosSinRenovarVuelveASinDatosALas24HorasSinAvisarANadie() {
            MarcasDeEstado deLosVecinos = new MarcasDeEstado(OrigenEstado.VECINOS, null, false, false, 0, new RespaldoVecinal(3, 3));
            Instant hace24h = ahora.minus(Duration.ofHours(24));
            dadoUnSector(EstadoServicio.SIN_SERVICIO, hace24h, hace24h, deLosVecinos);

            EstadoPublicado publicado = recalcular();

            assertThat(publicado.estado()).isNull();
            verify(sectores).publicarSiEs(MANGA, EstadoServicio.SIN_SERVICIO, null, MarcasDeEstado.ninguna());
            verify(registrarEvento, never()).registrar(any());
        }

        /** Un reporte coherente con el estado vigente lo renueva: «cada reporte coherente renueva la verificación». */
        @Test
        void losReportesNuevosQueCoincidenConElEstadoLoVerifican() {
            MarcasDeEstado deLosVecinos = new MarcasDeEstado(OrigenEstado.VECINOS, null, false, false, 0, new RespaldoVecinal(3, 3));
            dadoUnSector(EstadoServicio.SIN_SERVICIO, ahora.minus(Duration.ofHours(2)), ahora.minus(Duration.ofHours(2)), deLosVecinos);
            dadosLosVotos(TipoReporte.SIN_AGUA, 3,
                    reporte("r1", TipoReporte.SIN_AGUA, ahora.minusSeconds(300)),
                    reporte("r2", TipoReporte.SIN_AGUA, ahora.minusSeconds(200)),
                    reporte("r3", TipoReporte.SIN_AGUA, ahora.minusSeconds(100)));

            recalcular();

            verify(sectores).confirmarEstado(MANGA, EstadoServicio.SIN_SERVICIO);
            verify(sectores, never()).publicarSiEs(any(), any(), any(), any());
        }

        @Test
        void unQuorumQueContradiceAAcuacarMarcaLaDisputaSinCambiarElEstadoYLaDejaEnLaBitacora() {
            dadoUnSector(EstadoServicio.SIN_SERVICIO, INICIO, INICIO, marcasDeAcuacar());
            given(propuestas.listarAprobadasPorSector(MANGA)).willReturn(List.of(boletinDeAcuacar()));
            dadosLosVotos(TipoReporte.SERVICIO_RESTABLECIDO, 3,
                    reporte("r1", TipoReporte.SERVICIO_RESTABLECIDO, ahora.minusSeconds(300)),
                    reporte("r2", TipoReporte.SERVICIO_RESTABLECIDO, ahora.minusSeconds(200)),
                    reporte("r3", TipoReporte.SERVICIO_RESTABLECIDO, ahora.minusSeconds(100)));

            recalcular();

            MarcasDeEstado enDisputa = new MarcasDeEstado(OrigenEstado.ACUACAR, new VentanaTiempo(INICIO, FIN), false, true, 3, null);
            verify(sectores).abrirDisputaSiEs(MANGA, EstadoServicio.SIN_SERVICIO, enDisputa);
            // El estado no cambia, así que no hay correo ni push (eso lo publica el adaptador); la disputa sí queda anotada.
            EventoBitacora evento = eventoRegistrado();
            assertThat(evento.tipo()).isEqualTo(TipoEvento.ESTADO_EN_DISPUTA);
            assertThat(evento.estado()).isEqualTo(EstadoServicio.SIN_SERVICIO);
        }

        /** Dos recálculos simultáneos: solo el que abre la disputa la anota; la bitácora no admite retirar un duplicado. */
        @Test
        void siOtroProcesoAbrioLaDisputaAntesNoAnexaNingunEvento() {
            dadoUnSector(EstadoServicio.SIN_SERVICIO, INICIO, INICIO, marcasDeAcuacar());
            given(propuestas.listarAprobadasPorSector(MANGA)).willReturn(List.of(boletinDeAcuacar()));
            given(sectores.abrirDisputaSiEs(any(), any(), any())).willReturn(false);
            dadosLosVotos(TipoReporte.SERVICIO_RESTABLECIDO, 3,
                    reporte("r1", TipoReporte.SERVICIO_RESTABLECIDO, ahora.minusSeconds(300)),
                    reporte("r2", TipoReporte.SERVICIO_RESTABLECIDO, ahora.minusSeconds(200)),
                    reporte("r3", TipoReporte.SERVICIO_RESTABLECIDO, ahora.minusSeconds(100)));

            recalcular();

            verify(registrarEvento, never()).registrar(any());
        }

        /** La disputa se anota al abrirse, no en cada minuto que sigue abierta. */
        @Test
        void unaDisputaQueYaEstabaAbiertaNoVuelveAAnotarse() {
            MarcasDeEstado yaEnDisputa = new MarcasDeEstado(OrigenEstado.ACUACAR, new VentanaTiempo(INICIO, FIN), false, true, 3, null);
            dadoUnSector(EstadoServicio.SIN_SERVICIO, INICIO, ahora, yaEnDisputa);
            given(propuestas.listarAprobadasPorSector(MANGA)).willReturn(List.of(boletinDeAcuacar()));
            dadosLosVotos(TipoReporte.SERVICIO_RESTABLECIDO, 3,
                    reporte("r1", TipoReporte.SERVICIO_RESTABLECIDO, ahora.minusSeconds(300)),
                    reporte("r2", TipoReporte.SERVICIO_RESTABLECIDO, ahora.minusSeconds(200)),
                    reporte("r3", TipoReporte.SERVICIO_RESTABLECIDO, ahora.minusSeconds(100)));

            recalcular();

            verify(sectores, never()).publicarSiEs(any(), any(), any(), any());
            verify(registrarEvento, never()).registrar(any());
        }

        /** Pasada la promesa basta la mitad del umbral: dos vecinos confirman lo que tres no hacían falta para el corte. */
        @Test
        void trasLaPromesaBastaElQuorumReducidoParaConfirmarElRestablecimiento() {
            ahora = FIN.plusSeconds(1800);
            dadoUnSector(EstadoServicio.SIN_SERVICIO, INICIO, INICIO, marcasDeAcuacar());
            given(propuestas.listarAprobadasPorSector(MANGA)).willReturn(List.of(boletinDeAcuacar()));
            dadosLosVotos(TipoReporte.SERVICIO_RESTABLECIDO, 2,
                    reporte("r1", TipoReporte.SERVICIO_RESTABLECIDO, ahora.minusSeconds(200)),
                    reporte("r2", TipoReporte.SERVICIO_RESTABLECIDO, ahora.minusSeconds(100)));

            EstadoPublicado publicado = recalcular();

            assertThat(publicado.estado()).isEqualTo(EstadoServicio.CON_SERVICIO);
            assertThat(publicado.origen()).isEqualTo(OrigenEstado.VECINOS);
            assertThat(eventoRegistrado().tipo()).isEqualTo(TipoEvento.RESTABLECIMIENTO_POR_VECINOS);
        }
    }

    @Nested
    class BajaDePresionOficial {

        /** Un boletín que anuncia baja presión con ventana no es un corte: no debe endurecerse a «sin servicio». */
        @Test
        void unBoletinDeBajaPresionConVentanaPublicaPresionBaja() {
            dadoUnSector(null);
            given(propuestas.listarAprobadasPorSector(MANGA))
                    .willReturn(List.of(boletin("acuacar", EstadoServicio.PRESION_BAJA, INICIO, FIN)));

            assertThat(recalcular().estado()).isEqualTo(EstadoServicio.PRESION_BAJA);
        }
    }

    @Nested
    class Resultado {

        @Test
        void avisaQueCambioElEstadoYTraeLosReportesQueLoSustentan() {
            dadoUnSector(EstadoServicio.CON_SERVICIO);
            dadosLosVotos(TipoReporte.SIN_AGUA, 3,
                    reporte("r1", TipoReporte.SIN_AGUA, ahora.minusSeconds(300)),
                    reporte("r2", TipoReporte.SIN_AGUA, ahora.minusSeconds(200)),
                    reporte("r3", TipoReporte.SIN_AGUA, ahora.minusSeconds(100)));

            ResultadoDeRecalculo resultado = servicio.recalcular(MANGA);

            assertThat(resultado.cambioElEstado()).isTrue();
            assertThat(resultado.reportesQueSustentan()).containsExactlyInAnyOrder(
                    new ReporteId("r1"), new ReporteId("r2"), new ReporteId("r3"));
        }

        @Test
        void unCambioDeUnBoletinNoTraeReportes() {
            dadoUnSector(EstadoServicio.CON_SERVICIO);
            given(propuestas.listarAprobadasPorSector(MANGA)).willReturn(List.of(boletinDeAcuacar()));

            ResultadoDeRecalculo resultado = servicio.recalcular(MANGA);

            assertThat(resultado.cambioElEstado()).isTrue();
            assertThat(resultado.reportesQueSustentan()).isEmpty();
        }

        @Test
        void sinCambioDeEstadoNoHayCambioNiReportesQueSustenten() {
            dadoUnSector(EstadoServicio.SIN_SERVICIO, ahora, ahora, marcasDeAcuacar());
            given(propuestas.listarAprobadasPorSector(MANGA)).willReturn(List.of(boletinDeAcuacar()));

            ResultadoDeRecalculo resultado = servicio.recalcular(MANGA);

            assertThat(resultado.cambioElEstado()).isFalse();
            assertThat(resultado.reportesQueSustentan()).isEmpty();
        }

        @Test
        void siSoloCambianLasMarcasElEstadoNoCambio() {
            ahora = FIN.plusSeconds(60);
            dadoUnSector(EstadoServicio.SIN_SERVICIO, INICIO, INICIO, marcasDeAcuacar());
            given(propuestas.listarAprobadasPorSector(MANGA)).willReturn(List.of(boletinDeAcuacar()));

            assertThat(servicio.recalcular(MANGA).cambioElEstado()).isFalse();
        }

        /** Quien pierde la carrera no cambió nada: otro proceso lo hizo, y a él le toca el evento. */
        @Test
        void siPerdioLaCarreraNoCambioElEstadoNiSustentaNada() {
            dadoUnSector(EstadoServicio.CON_SERVICIO);
            dadosLosVotos(TipoReporte.SIN_AGUA, 3,
                    reporte("r1", TipoReporte.SIN_AGUA, ahora.minusSeconds(300)),
                    reporte("r2", TipoReporte.SIN_AGUA, ahora.minusSeconds(200)),
                    reporte("r3", TipoReporte.SIN_AGUA, ahora.minusSeconds(100)));
            given(sectores.publicarSiEs(any(), any(), any(), any())).willReturn(false);

            ResultadoDeRecalculo resultado = servicio.recalcular(MANGA);

            assertThat(resultado.cambioElEstado()).isFalse();
            assertThat(resultado.reportesQueSustentan()).isEmpty();
        }
    }

    /**
     * Si los vecinos confirman que volvió el agua pero el corte sigue «abierto» en el barrio, a las 24 horas
     * —cuando su memoria caduca— el barrio volvería a «sin servicio por confirmar». El cierre provisional lo evita.
     */
    @Nested
    class CierreProvisionalPorVecinos {

        private CorteAgua guardado() {
            ArgumentCaptor<CorteAgua> captor = ArgumentCaptor.forClass(CorteAgua.class);
            verify(cortes).guardar(captor.capture());
            return captor.getValue();
        }

        @Test
        void alConfirmarLosVecinosElRestablecimientoCierraProvisionalmenteElCorteConLaHoraDelPrimerReporte() {
            ahora = FIN.plusSeconds(1800);
            dadoUnSector(EstadoServicio.SIN_SERVICIO, INICIO, INICIO, marcasDeAcuacar());
            PropuestaIngesta boletin = boletinDeAcuacar();
            given(propuestas.listarAprobadasPorSector(MANGA)).willReturn(List.of(boletin));
            given(cortes.listarPorSector(MANGA)).willReturn(List.of(corteDeIngesta(boletin, null)));
            dadosLosVotos(TipoReporte.SERVICIO_RESTABLECIDO, 2,
                    reporte("r1", TipoReporte.SERVICIO_RESTABLECIDO, ahora.minusSeconds(200)),
                    reporte("r2", TipoReporte.SERVICIO_RESTABLECIDO, ahora.minusSeconds(100)));

            recalcular();

            assertThat(guardado().cierreDe(MANGA))
                    .contains(new CierreDeCorte(ahora.minusSeconds(200), OrigenEstado.VECINOS, true));
        }

        /** El cierre no puede ser anterior al inicio del corte aunque el primer reporte de la ventana lo sea. */
        @Test
        void laHoraDelCierreNuncaPrecedeAlInicioDelCorte() {
            Instant inicioCorto = FIN.minusSeconds(600);
            ahora = FIN.plusSeconds(600);
            PropuestaIngesta boletin = boletin("acuacar", EstadoServicio.SIN_SERVICIO, inicioCorto, FIN);
            dadoUnSector(EstadoServicio.SIN_SERVICIO, inicioCorto, inicioCorto,
                    new MarcasDeEstado(OrigenEstado.ACUACAR, new VentanaTiempo(inicioCorto, FIN), true, false, 0, null));
            given(propuestas.listarAprobadasPorSector(MANGA)).willReturn(List.of(boletin));
            given(cortes.listarPorSector(MANGA)).willReturn(List.of(corteDeIngesta(boletin, null)));
            dadosLosVotos(TipoReporte.SERVICIO_RESTABLECIDO, 2,
                    reporte("r1", TipoReporte.SERVICIO_RESTABLECIDO, FIN.minusSeconds(900)),
                    reporte("r2", TipoReporte.SERVICIO_RESTABLECIDO, ahora.minusSeconds(100)));

            recalcular();

            assertThat(guardado().cierreDe(MANGA).orElseThrow().hora()).isEqualTo(inicioCorto);
        }

        @Test
        void tambienCierraUnCorteDelVeedorCuyaPromesaYaVencio() {
            ahora = FIN.plusSeconds(1800);
            dadoUnSector(EstadoServicio.SIN_SERVICIO, INICIO, INICIO,
                    new MarcasDeEstado(OrigenEstado.VEEDOR, new VentanaTiempo(INICIO, FIN), true, false, 0, null));
            given(cortes.listarPorSector(MANGA)).willReturn(List.of(corteDelVeedor()));
            dadosLosVotos(TipoReporte.SERVICIO_RESTABLECIDO, 2,
                    reporte("r1", TipoReporte.SERVICIO_RESTABLECIDO, ahora.minusSeconds(200)),
                    reporte("r2", TipoReporte.SERVICIO_RESTABLECIDO, ahora.minusSeconds(100)));

            recalcular();

            assertThat(guardado().estado()).isEqualTo(EstadoCorte.RESTABLECIDO);
        }

        @Test
        void elCorteSeCierraEnLaMismaTransaccionQueElEstado() {
            ahora = FIN.plusSeconds(1800);
            dadoUnSector(EstadoServicio.SIN_SERVICIO, INICIO, INICIO, marcasDeAcuacar());
            PropuestaIngesta boletin = boletinDeAcuacar();
            given(propuestas.listarAprobadasPorSector(MANGA)).willReturn(List.of(boletin));
            given(cortes.listarPorSector(MANGA)).willReturn(List.of(corteDeIngesta(boletin, null)));
            dadosLosVotos(TipoReporte.SERVICIO_RESTABLECIDO, 2,
                    reporte("r1", TipoReporte.SERVICIO_RESTABLECIDO, ahora.minusSeconds(200)),
                    reporte("r2", TipoReporte.SERVICIO_RESTABLECIDO, ahora.minusSeconds(100)));

            recalcular();

            verify(transaccion).ejecutar(any());
        }

        /** Un corte cuya promesa sigue vigente no se cierra porque los vecinos digan que «ya volvió»: eso es una disputa. */
        @Test
        void noCierraUnCorteCuyaPromesaTodaviaNoVencio() {
            dadoUnSector(EstadoServicio.SIN_SERVICIO, INICIO, INICIO, marcasDeAcuacar());
            PropuestaIngesta boletin = boletinDeAcuacar();
            given(propuestas.listarAprobadasPorSector(MANGA)).willReturn(List.of(boletin));
            given(cortes.listarPorSector(MANGA)).willReturn(List.of(corteDeIngesta(boletin, null)));
            dadosLosVotos(TipoReporte.SERVICIO_RESTABLECIDO, 3,
                    reporte("r1", TipoReporte.SERVICIO_RESTABLECIDO, ahora.minusSeconds(300)),
                    reporte("r2", TipoReporte.SERVICIO_RESTABLECIDO, ahora.minusSeconds(200)),
                    reporte("r3", TipoReporte.SERVICIO_RESTABLECIDO, ahora.minusSeconds(100)));

            recalcular();

            verify(cortes, never()).guardar(any());
        }
    }

    /** Un restablecimiento que solo sostenían los vecinos puede no ser efectivo: el mismo corte se reabre (D15). */
    @Nested
    class ReaperturaDelMismoCorte {

        private final Instant cierreA = FIN.plusSeconds(1800);

        private void dadoUnCierreDe(OrigenEstado fuente, boolean provisional, Instant hora) {
            PropuestaIngesta boletin = boletinDeAcuacar();
            given(propuestas.listarAprobadasPorSector(MANGA)).willReturn(List.of(boletin));
            given(cortes.listarPorSector(MANGA)).willReturn(List.of(
                    corteDeIngesta(boletin, new CierreDeCorte(hora, fuente, provisional))));
            MarcasDeEstado deLosVecinos = new MarcasDeEstado(OrigenEstado.VECINOS, null, false, false, 0, new RespaldoVecinal(2, 3));
            dadoUnSector(EstadoServicio.CON_SERVICIO, hora, hora, deLosVecinos);
        }

        private void dadosReportesDeSinAguaPosterioresA(Instant cierre) {
            dadosLosVotos(TipoReporte.SIN_AGUA, 3,
                    reporte("r1", TipoReporte.SIN_AGUA, cierre.plusSeconds(600)),
                    reporte("r2", TipoReporte.SIN_AGUA, cierre.plusSeconds(700)),
                    reporte("r3", TipoReporte.SIN_AGUA, cierre.plusSeconds(800)));
        }

        @Test
        void unQuorumDeSinAguaDentroDeLas3HorasReabreElMismoCorte() {
            ahora = cierreA.plus(Duration.ofHours(1));
            dadoUnCierreDe(OrigenEstado.VECINOS, true, cierreA);
            dadosReportesDeSinAguaPosterioresA(cierreA);

            recalcular();

            ArgumentCaptor<CorteAgua> captor = ArgumentCaptor.forClass(CorteAgua.class);
            verify(cortes).guardar(captor.capture());
            assertThat(captor.getValue().cierreDe(MANGA)).isEmpty();
            assertThat(captor.getValue().estado()).isEqualTo(EstadoCorte.ANUNCIADO);
        }

        @Test
        void trasReabrirElBarrioVuelveAAfirmarloLaFuenteOficial() {
            ahora = cierreA.plus(Duration.ofHours(1));
            dadoUnCierreDe(OrigenEstado.VECINOS, true, cierreA);
            dadosReportesDeSinAguaPosterioresA(cierreA);

            EstadoPublicado publicado = recalcular();

            assertThat(publicado.estado()).isEqualTo(EstadoServicio.SIN_SERVICIO);
            assertThat(publicado.origen()).isEqualTo(OrigenEstado.ACUACAR);
            assertThat(publicado.restablecimientoPorConfirmar()).isTrue();
        }

        /** Pasado ese plazo una intermitencia ya es otro evento: el cierre se respeta y los vecinos lo contradicen. */
        @Test
        void pasadasLas3HorasNoSeReabreYLosVecinosContradicenElRestablecimiento() {
            ahora = cierreA.plus(Duration.ofHours(4));
            dadoUnCierreDe(OrigenEstado.VECINOS, true, cierreA);
            dadosReportesDeSinAguaPosterioresA(cierreA.plus(Duration.ofHours(3)));

            EstadoPublicado publicado = recalcular();

            verify(cortes, never()).guardar(any());
            assertThat(publicado.estado()).isEqualTo(EstadoServicio.SIN_SERVICIO);
            assertThat(publicado.origen()).isEqualTo(OrigenEstado.VECINOS);
        }

        @Test
        void unCierreConfirmadoPorElVeedorNoSeReabre() {
            ahora = cierreA.plus(Duration.ofHours(1));
            dadoUnCierreDe(OrigenEstado.VEEDOR, false, cierreA);
            dadosReportesDeSinAguaPosterioresA(cierreA);

            recalcular();

            verify(cortes, never()).guardar(any());
        }

        private void dadosVotosEnContraYAFavorDelRestablecimiento(int sinAgua, int restablecido, Instant cierre) {
            List<ReporteCiudadano> sinAguaReportes = java.util.stream.IntStream.range(0, sinAgua)
                    .mapToObj(i -> reporte("a" + i, TipoReporte.SIN_AGUA, cierre.plusSeconds(600 + i))).toList();
            List<ReporteCiudadano> restablecidoReportes = java.util.stream.IntStream.range(0, restablecido)
                    .mapToObj(i -> reporte("b" + i, TipoReporte.SERVICIO_RESTABLECIDO, cierre.plusSeconds(100 + i))).toList();
            given(reportes.contarVotosRecientes(any(), any())).willReturn(
                    Map.of(TipoReporte.SIN_AGUA, (long) sinAgua, TipoReporte.SERVICIO_RESTABLECIDO, (long) restablecido));
            given(reportes.listarRecientesPorSector(any(), any())).willReturn(
                    java.util.stream.Stream.concat(sinAguaReportes.stream(), restablecidoReportes.stream()).toList());
        }

        /**
         * Si hay tantos vecinos que dicen «ya volvió» como «sigue sin agua», ninguno gana: reabrir el corte por
         * un empate dejaría el barrio con el corte abierto y «con servicio» a la vez.
         */
        @Test
        void unQuorumContrarioQueNoSuperaAlDeRestablecimientoNoReabreElCorte() {
            ahora = cierreA.plus(Duration.ofHours(1));
            dadoUnCierreDe(OrigenEstado.VECINOS, true, cierreA);
            dadosVotosEnContraYAFavorDelRestablecimiento(3, 3, cierreA);

            recalcular();

            verify(cortes, never()).guardar(any());
        }

        @Test
        void unQuorumContrarioMayorQueElDeRestablecimientoReabreElCorteYElBarrioVuelveASinServicio() {
            ahora = cierreA.plus(Duration.ofHours(1));
            dadoUnCierreDe(OrigenEstado.VECINOS, true, cierreA);
            dadosVotosEnContraYAFavorDelRestablecimiento(4, 3, cierreA);

            EstadoPublicado publicado = recalcular();

            verify(cortes).guardar(any(CorteAgua.class));
            assertThat(publicado.estado()).isEqualTo(EstadoServicio.SIN_SERVICIO);
            assertThat(eventoRegistrado().tipo()).isEqualTo(TipoEvento.CORTE_CONFIRMADO_POR_CIUDADANOS);
        }

        /** Reabrir un corte es un hecho de la bitácora aunque el estado ya fuera el que corresponde. */
        @Test
        void reabrirUnCorteDejaSuEventoAunqueElEstadoNoCambie() {
            ahora = cierreA.plus(Duration.ofHours(1));
            PropuestaIngesta boletin = boletinDeAcuacar();
            given(propuestas.listarAprobadasPorSector(MANGA)).willReturn(List.of(boletin));
            given(cortes.listarPorSector(MANGA)).willReturn(List.of(
                    corteDeIngesta(boletin, new CierreDeCorte(cierreA, OrigenEstado.VECINOS, true))));
            // El barrio ya figuraba sin servicio por Acuacar (la promesa venció): el estado no se mueve al reabrir.
            dadoUnSector(EstadoServicio.SIN_SERVICIO, cierreA, cierreA,
                    new MarcasDeEstado(OrigenEstado.ACUACAR, new VentanaTiempo(INICIO, FIN), true, false, 0, null));
            dadosReportesDeSinAguaPosterioresA(cierreA);

            recalcular();

            verify(cortes).guardar(any(CorteAgua.class));
            assertThat(eventoRegistrado().tipo()).isEqualTo(TipoEvento.CORTE_CONFIRMADO_POR_CIUDADANOS);
        }

        /**
         * El recálculo lee el corte, decide y escribe: en medio un veedor pudo confirmar el cierre o anular el
         * corte. Reabrirlo con la copia vieja deshacería lo que el veedor ya recibió como hecho, así que el corte se
         * vuelve a leer dentro de la transacción y, si ya no admite el cambio, no se escribe nada.
         */
        @Test
        void siElVeedorConfirmoElCierreEntreLaLecturaYLaEscrituraNoSeReabreNiSeEscribeNada() {
            ahora = cierreA.plus(Duration.ofHours(1));
            dadoUnCierreDe(OrigenEstado.VECINOS, true, cierreA);
            dadosReportesDeSinAguaPosterioresA(cierreA);
            PropuestaIngesta boletin = boletinDeAcuacar();
            CorteAgua yaConfirmado = corteDeIngesta(boletin, new CierreDeCorte(cierreA, OrigenEstado.VEEDOR, false));
            org.mockito.Mockito.doReturn(Optional.of(yaConfirmado)).when(cortes).buscarPorId(boletin.idDelCorte());

            ResultadoDeRecalculo resultado = servicio.recalcular(MANGA);

            verify(cortes, never()).guardar(any());
            verify(sectores, never()).publicarSiEs(any(), any(), any(), any());
            verify(registrarEvento, never()).registrar(any());
            assertThat(resultado.cambioElEstado()).isFalse();
        }

        @Test
        void siElCorteYaNoExisteAlEscribirNoSeEscribeNada() {
            ahora = cierreA.plus(Duration.ofHours(1));
            dadoUnCierreDe(OrigenEstado.VECINOS, true, cierreA);
            dadosReportesDeSinAguaPosterioresA(cierreA);
            org.mockito.Mockito.doReturn(Optional.empty()).when(cortes).buscarPorId(any());

            servicio.recalcular(MANGA);

            verify(cortes, never()).guardar(any());
            verify(sectores, never()).publicarSiEs(any(), any(), any(), any());
        }

        @Test
        void unQuorumAnteriorAlCierreNoLoReabre() {
            ahora = cierreA.plus(Duration.ofHours(1));
            dadoUnCierreDe(OrigenEstado.VECINOS, true, cierreA);
            dadosLosVotos(TipoReporte.SIN_AGUA, 3,
                    reporte("r1", TipoReporte.SIN_AGUA, cierreA.minusSeconds(900)),
                    reporte("r2", TipoReporte.SIN_AGUA, cierreA.minusSeconds(800)),
                    reporte("r3", TipoReporte.SIN_AGUA, cierreA.minusSeconds(700)));

            recalcular();

            verify(cortes, never()).guardar(any());
        }
    }

    /** Descartar reportes de un abusador no puede dejar publicado un estado que solo ellos sostenían (D23). */
    @Nested
    class TrasDescartarReportes {

        private MarcasDeEstado marcasDeLosVecinos(int respaldo) {
            return new MarcasDeEstado(OrigenEstado.VECINOS, null, false, false, 0, new RespaldoVecinal(respaldo, 3));
        }

        private ResultadoDeRecalculo reevaluar() {
            return servicio.reevaluarTrasDescarte(MANGA);
        }

        @Test
        void siLosReportesQueQuedanNoSostienenElQuorumElBarrioVuelveASinDatosYQuedaElEventoDeReversion() {
            dadoUnSector(EstadoServicio.SIN_SERVICIO, ahora.minus(Duration.ofHours(2)), ahora.minus(Duration.ofHours(2)),
                    marcasDeLosVecinos(3));
            given(reportes.listarRecientesPorSector(any(), any())).willReturn(List.of(
                    reporte("r1", TipoReporte.SIN_AGUA, ahora.minus(Duration.ofHours(2)))));

            ResultadoDeRecalculo resultado = reevaluar();

            assertThat(resultado.publicado().estado()).isNull();
            assertThat(resultado.cambioElEstado()).isTrue();
            verify(sectores).publicarSiEs(MANGA, EstadoServicio.SIN_SERVICIO, null, MarcasDeEstado.ninguna());
            assertThat(eventoRegistrado().tipo()).isEqualTo(TipoEvento.CONSENSO_REVERTIDO);
        }

        @Test
        void siLosReportesValidosSiguenSosteniendoElQuorumNoCambiaNada() {
            dadoUnSector(EstadoServicio.SIN_SERVICIO, ahora.minus(Duration.ofHours(2)), ahora.minus(Duration.ofHours(2)),
                    marcasDeLosVecinos(3));
            given(reportes.listarRecientesPorSector(any(), any())).willReturn(List.of(
                    reporte("r1", TipoReporte.SIN_AGUA, ahora.minus(Duration.ofHours(2))),
                    reporte("r2", TipoReporte.SIN_AGUA, ahora.minus(Duration.ofHours(2)).plusSeconds(60)),
                    reporte("r3", TipoReporte.SIN_AGUA, ahora.minus(Duration.ofHours(2)).plusSeconds(120))));

            ResultadoDeRecalculo resultado = reevaluar();

            assertThat(resultado.publicado().estado()).isEqualTo(EstadoServicio.SIN_SERVICIO);
            assertThat(resultado.cambioElEstado()).isFalse();
            verify(sectores, never()).publicarSiEs(any(), any(), any(), any());
        }

        /** Un restablecimiento que los vecinos confirmaron con el quórum reducido se vuelve a comprobar con ese mismo listón. */
        @Test
        void unRestablecimientoConfirmadoConElQuorumReducidoSeCompruebaConElReducido() {
            dadoUnSector(EstadoServicio.CON_SERVICIO, ahora.minus(Duration.ofHours(2)), ahora.minus(Duration.ofHours(2)),
                    marcasDeLosVecinos(2));
            given(reportes.listarRecientesPorSector(any(), any())).willReturn(List.of(
                    reporte("r1", TipoReporte.SERVICIO_RESTABLECIDO, ahora.minus(Duration.ofHours(2))),
                    reporte("r2", TipoReporte.SERVICIO_RESTABLECIDO, ahora.minus(Duration.ofHours(2)).plusSeconds(60))));

            assertThat(reevaluar().publicado().estado()).isEqualTo(EstadoServicio.CON_SERVICIO);
        }

        /** Recalcular por otro motivo (un boletín, el barrido) no vuelve a auditar los reportes: la memoria se respeta. */
        @Test
        void unRecalculoNormalNoRevisaLosReportesDeLaMemoria() {
            dadoUnSector(EstadoServicio.SIN_SERVICIO, ahora.minus(Duration.ofHours(2)), ahora.minus(Duration.ofHours(2)),
                    marcasDeLosVecinos(3));

            recalcular();

            verify(reportes, never()).listarRecientesPorSector(any(), any());
        }
    }

    /**
     * Los sensores votan como los vecinos (D-sensores), pero se dice quién sostiene el estado: «según los sensores
     * de la red» no es «según N vecinos». Solo cuenta como sensor lo que entró por el endpoint de sensores.
     */
    @Nested
    class OrigenSensor {

        private ReporteCiudadano deSensor(String id, TipoReporte tipo, Instant cuando) {
            return reporte(id, tipo, cuando).comoDeSensor();
        }

        @Test
        void unQuorumDeSoloSensoresPublicaConOrigenSensor() {
            dadoUnSector(EstadoServicio.CON_SERVICIO);
            dadosLosVotos(TipoReporte.PRESION_BAJA, 3,
                    deSensor("s1", TipoReporte.PRESION_BAJA, ahora.minusSeconds(300)),
                    deSensor("s2", TipoReporte.PRESION_BAJA, ahora.minusSeconds(200)),
                    deSensor("s3", TipoReporte.PRESION_BAJA, ahora.minusSeconds(100)));

            EstadoPublicado publicado = recalcular();

            assertThat(publicado.estado()).isEqualTo(EstadoServicio.PRESION_BAJA);
            assertThat(publicado.origen()).isEqualTo(OrigenEstado.SENSOR);
            MarcasDeEstado marcas = new MarcasDeEstado(OrigenEstado.SENSOR, null, false, false, 0, new RespaldoVecinal(3, 3));
            verify(sectores).publicarSiEs(MANGA, EstadoServicio.CON_SERVICIO, EstadoServicio.PRESION_BAJA, marcas);
            assertThat(eventoRegistrado().fuente()).isEqualTo(OrigenEstado.SENSOR);
        }

        /** Con un solo vecino de por medio el estado lo sostienen vecinos y sensores juntos: se llama VECINOS. */
        @Test
        void siHayUnVecinoDeporMedioElOrigenEsVecinos() {
            dadoUnSector(EstadoServicio.CON_SERVICIO);
            dadosLosVotos(TipoReporte.PRESION_BAJA, 3,
                    deSensor("s1", TipoReporte.PRESION_BAJA, ahora.minusSeconds(300)),
                    deSensor("s2", TipoReporte.PRESION_BAJA, ahora.minusSeconds(200)),
                    reporte("v1", TipoReporte.PRESION_BAJA, ahora.minusSeconds(100)));

            assertThat(recalcular().origen()).isEqualTo(OrigenEstado.VECINOS);
        }

        @Test
        void unEstadoQueSostienenLosSensoresSeRecuerdaConSuOrigenSinReportesNuevos() {
            MarcasDeEstado deLosSensores = new MarcasDeEstado(OrigenEstado.SENSOR, null, false, false, 0, new RespaldoVecinal(3, 3));
            dadoUnSector(EstadoServicio.PRESION_BAJA, ahora.minus(Duration.ofHours(2)), ahora.minus(Duration.ofHours(2)),
                    deLosSensores);

            EstadoPublicado publicado = recalcular();

            assertThat(publicado.estado()).isEqualTo(EstadoServicio.PRESION_BAJA);
            assertThat(publicado.origen()).isEqualTo(OrigenEstado.SENSOR);
            verify(sectores, never()).publicarSiEs(any(), any(), any(), any());
        }

        /** El cierre por presión normal sostenida es provisional y lleva su fuente: sensor, no vecinos. */
        @Test
        void unRestablecimientoSostenidoSoloPorSensoresCierraElCorteConFuenteSensor() {
            ahora = FIN.plusSeconds(1800);
            dadoUnSector(EstadoServicio.SIN_SERVICIO, INICIO, INICIO, marcasDeAcuacar());
            PropuestaIngesta boletin = boletinDeAcuacar();
            given(propuestas.listarAprobadasPorSector(MANGA)).willReturn(List.of(boletin));
            given(cortes.listarPorSector(MANGA)).willReturn(List.of(corteDeIngesta(boletin, null)));
            dadosLosVotos(TipoReporte.SERVICIO_RESTABLECIDO, 2,
                    deSensor("s1", TipoReporte.SERVICIO_RESTABLECIDO, ahora.minusSeconds(200)),
                    deSensor("s2", TipoReporte.SERVICIO_RESTABLECIDO, ahora.minusSeconds(100)));

            EstadoPublicado publicado = recalcular();

            assertThat(publicado.estado()).isEqualTo(EstadoServicio.CON_SERVICIO);
            assertThat(publicado.origen()).isEqualTo(OrigenEstado.SENSOR);
            ArgumentCaptor<CorteAgua> captor = ArgumentCaptor.forClass(CorteAgua.class);
            verify(cortes).guardar(captor.capture());
            assertThat(captor.getValue().cierreDe(MANGA))
                    .contains(new CierreDeCorte(ahora.minusSeconds(200), OrigenEstado.SENSOR, true));
        }
    }

    @Nested
    class VerificacionDelEstado {

        /** Un boletín que sigue sosteniendo el estado lo verifica, pero sin escribir en cada barrido. */
        @Test
        void unaFuenteOficialQueSostieneElEstadoLoVerificaCuandoTocaYNoAntes() {
            dadoUnSector(EstadoServicio.SIN_SERVICIO, INICIO, ahora.minus(Duration.ofMinutes(10)), marcasDeAcuacar());
            given(propuestas.listarAprobadasPorSector(MANGA)).willReturn(List.of(boletinDeAcuacar()));

            recalcular();
            verify(sectores).confirmarEstado(MANGA, EstadoServicio.SIN_SERVICIO);
        }

        @Test
        void noVuelveAVerificarSiLoHizoHaceMenosDeCincoMinutos() {
            dadoUnSector(EstadoServicio.SIN_SERVICIO, INICIO, ahora.minus(Duration.ofMinutes(2)), marcasDeAcuacar());
            given(propuestas.listarAprobadasPorSector(MANGA)).willReturn(List.of(boletinDeAcuacar()));

            recalcular();

            verify(sectores, never()).confirmarEstado(any(), any());
        }
    }

    /**
     * D37: lo que hay que medir para calibrar los umbrales. Cada cifra sale del único escritor del estado: si se contara en otra capa, un
     * cambio que pierde la carrera contra otro proceso se contaría dos veces.
     */
    @Nested
    class Metricas {

        private MetricasDelSistemaPort metricas;

        @BeforeEach
        void conMetricas() {
            metricas = mock(MetricasDelSistemaPort.class);
            servicio = new RecalcularSectorService(sectores, cortes, propuestas, reportes, sector -> 3,
                    new ResolutorDeEstadoSector(ReglasDeEstado.porDefecto()), registrarEvento,
                    () -> ahora, transaccion, VENTANA_CONSENSO, 2, metricas);
        }

        private ReporteCiudadano deLaRed(String id, NivelDeVerificacion nivel, String red) {
            return new ReporteCiudadano(new ReporteId(id), MANGA, TipoReporte.SIN_AGUA, null,
                    new HuellaDispositivo("h-" + id), ahora.minusSeconds(100)).conIdentidad(nivel, red);
        }

        @Test
        void unCambioDeEstadoPorVecinosSeCuentaYSeMideCuantoTardoDesdeElPrimerReporte() {
            dadoUnSector(EstadoServicio.CON_SERVICIO);
            dadosLosVotos(TipoReporte.SIN_AGUA, 3,
                    reporte("r1", TipoReporte.SIN_AGUA, ahora.minusSeconds(300)),
                    reporte("r2", TipoReporte.SIN_AGUA, ahora.minusSeconds(200)),
                    reporte("r3", TipoReporte.SIN_AGUA, ahora.minusSeconds(100)));

            recalcular();

            verify(metricas).cambioDeEstado(MANGA, EstadoServicio.SIN_SERVICIO, OrigenEstado.VECINOS);
            verify(metricas).tiempoHastaElCambioDeEstado(Duration.ofSeconds(300));
        }

        @Test
        void sinCambioDeEstadoNoSeCuentaNiSeMideNada() {
            dadoUnSector(null);

            recalcular();

            verify(metricas, never()).cambioDeEstado(any(), any(), any());
            verify(metricas, never()).tiempoHastaElCambioDeEstado(any());
        }

        @Test
        void siOtroProcesoGanoLaCarreraEsteRecalculoNoCuentaElCambio() {
            dadoUnSector(EstadoServicio.CON_SERVICIO);
            given(sectores.publicarSiEs(any(), any(), any(), any())).willReturn(false);
            dadosLosVotos(TipoReporte.SIN_AGUA, 3,
                    reporte("r1", TipoReporte.SIN_AGUA, ahora.minusSeconds(300)),
                    reporte("r2", TipoReporte.SIN_AGUA, ahora.minusSeconds(200)),
                    reporte("r3", TipoReporte.SIN_AGUA, ahora.minusSeconds(100)));

            recalcular();

            verify(metricas, never()).cambioDeEstado(any(), any(), any());
        }

        @Test
        void unaDisputaQueSeAbreSeCuentaUnaVez() {
            dadoUnSector(EstadoServicio.SIN_SERVICIO, INICIO, INICIO, marcasDeAcuacar());
            given(propuestas.listarAprobadasPorSector(MANGA)).willReturn(List.of(boletinDeAcuacar()));
            dadosLosVotos(TipoReporte.SERVICIO_RESTABLECIDO, 3,
                    reporte("r1", TipoReporte.SERVICIO_RESTABLECIDO, ahora.minusSeconds(300)),
                    reporte("r2", TipoReporte.SERVICIO_RESTABLECIDO, ahora.minusSeconds(200)),
                    reporte("r3", TipoReporte.SERVICIO_RESTABLECIDO, ahora.minusSeconds(100)));

            recalcular();

            verify(metricas).disputaAbierta();
        }

        @Test
        void siOtroProcesoAbrioLaDisputaAntesNoSeCuenta() {
            dadoUnSector(EstadoServicio.SIN_SERVICIO, INICIO, INICIO, marcasDeAcuacar());
            given(propuestas.listarAprobadasPorSector(MANGA)).willReturn(List.of(boletinDeAcuacar()));
            given(sectores.abrirDisputaSiEs(any(), any(), any())).willReturn(false);
            dadosLosVotos(TipoReporte.SERVICIO_RESTABLECIDO, 3,
                    reporte("r1", TipoReporte.SERVICIO_RESTABLECIDO, ahora.minusSeconds(300)),
                    reporte("r2", TipoReporte.SERVICIO_RESTABLECIDO, ahora.minusSeconds(200)),
                    reporte("r3", TipoReporte.SERVICIO_RESTABLECIDO, ahora.minusSeconds(100)));

            recalcular();

            verify(metricas, never()).disputaAbierta();
        }

        /**
         * Un reintento de la transacción vuelve a decidir sobre datos frescos: si el primer intento marcó que escribía la
         * disputa pero se revirtió, y en el segundo otro proceso ya la había abierto, la métrica no puede quedar contada.
         */
        @Test
        void siLaTransaccionSeReintentaYElSegundoIntentoNoEscribeLaDisputaNoSeCuenta() {
            TransaccionPort conUnReintento = new TransaccionPort() {
                @Override
                public <T> T ejecutar(java.util.function.Supplier<T> accion) {
                    accion.get(); // primer intento: se da por revertido
                    return accion.get();
                }
            };
            servicio = new RecalcularSectorService(sectores, cortes, propuestas, reportes, sector -> 3,
                    new ResolutorDeEstadoSector(ReglasDeEstado.porDefecto()), registrarEvento,
                    () -> ahora, conUnReintento, VENTANA_CONSENSO, 2, metricas);
            dadoUnSector(EstadoServicio.SIN_SERVICIO, INICIO, INICIO, marcasDeAcuacar());
            given(propuestas.listarAprobadasPorSector(MANGA)).willReturn(List.of(boletinDeAcuacar()));
            given(sectores.abrirDisputaSiEs(any(), any(), any())).willReturn(true, false);
            dadosLosVotos(TipoReporte.SERVICIO_RESTABLECIDO, 3,
                    reporte("r1", TipoReporte.SERVICIO_RESTABLECIDO, ahora.minusSeconds(300)),
                    reporte("r2", TipoReporte.SERVICIO_RESTABLECIDO, ahora.minusSeconds(200)),
                    reporte("r3", TipoReporte.SERVICIO_RESTABLECIDO, ahora.minusSeconds(100)));

            recalcular();

            verify(metricas, never()).disputaAbierta();
        }

        /** Tres reportes anónimos de una sola red llegan al umbral pero no a la composición: es lo que más importa ver al calibrar. */
        @Test
        void unQuorumQueLlegaAlUmbralPeroNoALaComposicionSeCuentaComoRechazado() {
            dadoUnSector(null);
            dadosLosVotos(TipoReporte.SIN_AGUA, 3,
                    deLaRed("r1", NivelDeVerificacion.NINGUNA, "red-a"),
                    deLaRed("r2", NivelDeVerificacion.NINGUNA, "red-a"),
                    deLaRed("r3", NivelDeVerificacion.NINGUNA, "red-a"));

            recalcular();

            verify(metricas).quorumRechazadoPorComposicion(MANGA, TipoReporte.SIN_AGUA);
            verify(metricas, never()).cambioDeEstado(any(), any(), any());
        }

        @Test
        void unQuorumQueNoLlegaAlUmbralNoSeCuentaComoRechazado() {
            dadoUnSector(null);
            dadosLosVotos(TipoReporte.SIN_AGUA, 2,
                    deLaRed("r1", NivelDeVerificacion.NINGUNA, "red-a"),
                    deLaRed("r2", NivelDeVerificacion.NINGUNA, "red-a"));

            recalcular();

            verify(metricas, never()).quorumRechazadoPorComposicion(any(), any());
        }
    }
}
