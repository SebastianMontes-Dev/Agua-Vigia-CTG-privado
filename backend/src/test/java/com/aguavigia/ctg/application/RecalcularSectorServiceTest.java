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
import com.aguavigia.ctg.domain.OrigenCorte;
import com.aguavigia.ctg.domain.OrigenEstado;
import com.aguavigia.ctg.domain.PropuestaId;
import com.aguavigia.ctg.domain.PropuestaIngesta;
import com.aguavigia.ctg.domain.ReglasDeEstado;
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
        given(reportes.contarVotosRecientes(any(), any())).willReturn(Map.of());
        given(sectores.publicarSiEs(any(), any(), any(), any())).willReturn(true);

        EstrategiaConsenso umbralDeTres = sector -> 3;
        servicio = new RecalcularSectorService(sectores, cortes, propuestas, reportes, umbralDeTres,
                new ResolutorDeEstadoSector(ReglasDeEstado.porDefecto()), registrarEvento,
                () -> ahora, transaccion, VENTANA_CONSENSO);
    }

    // --- ayudantes ---------------------------------------------------------------------------------

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
        return new ReporteCiudadano(new ReporteId(id), MANGA, tipo, null, new HuellaDispositivo("h-" + id), cuando);
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

    @Test
    void unBarrioInexistenteSeRechaza() {
        given(sectores.buscarPorId(MANGA)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.recalcular(MANGA)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sinNingunaFuenteYSinEstadoNoEscribeNada() {
        dadoUnSector(null);

        EstadoPublicado publicado = servicio.recalcular(MANGA);

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

            EstadoPublicado publicado = servicio.recalcular(MANGA);

            assertThat(publicado.estado()).isEqualTo(EstadoServicio.SIN_SERVICIO);
            verify(sectores).publicarSiEs(MANGA, EstadoServicio.CON_SERVICIO, EstadoServicio.SIN_SERVICIO, marcasDeAcuacar());
            EventoBitacora evento = eventoRegistrado();
            assertThat(evento.tipo()).isEqualTo(TipoEvento.CORTE_DETECTADO_POR_INGESTA);
            assertThat(evento.estado()).isEqualTo(EstadoServicio.SIN_SERVICIO);
            assertThat(evento.urlOriginal()).isEqualTo("https://acuacar.com/2854");
        }

        /** El par estado + evento es una sola unidad: si el evento falla, el estado no puede quedar publicado. */
        @Test
        void elEstadoYSuEventoVanEnUnaSolaTransaccion() {
            dadoUnSector(EstadoServicio.CON_SERVICIO);
            given(propuestas.listarAprobadasPorSector(MANGA)).willReturn(List.of(boletinDeAcuacar()));

            servicio.recalcular(MANGA);

            verify(transaccion).ejecutar(any());
        }

        @Test
        void siElEventoFallaLaFallaSePropagaParaQueLaTransaccionRevierta() {
            dadoUnSector(EstadoServicio.CON_SERVICIO);
            given(propuestas.listarAprobadasPorSector(MANGA)).willReturn(List.of(boletinDeAcuacar()));
            doThrow(new IllegalStateException("Mongo caído")).when(registrarEvento).registrar(any());

            assertThatThrownBy(() -> servicio.recalcular(MANGA)).isInstanceOf(IllegalStateException.class);
        }

        /** Dos recálculos simultáneos: solo el que gana la escritura anexa el evento. */
        @Test
        void siOtroProcesoGanoLaCarreraNoAnexaNingunEvento() {
            dadoUnSector(EstadoServicio.CON_SERVICIO);
            given(propuestas.listarAprobadasPorSector(MANGA)).willReturn(List.of(boletinDeAcuacar()));
            given(sectores.publicarSiEs(any(), any(), any(), any())).willReturn(false);

            servicio.recalcular(MANGA);

            verify(registrarEvento, never()).registrar(any());
        }

        @Test
        void siElEstadoYLasMarcasYaSonLasQueCorrespondenNoEscribeNiAnexaNada() {
            dadoUnSector(EstadoServicio.SIN_SERVICIO, ahora, ahora, marcasDeAcuacar());
            given(propuestas.listarAprobadasPorSector(MANGA)).willReturn(List.of(boletinDeAcuacar()));

            servicio.recalcular(MANGA);

            verify(sectores, never()).publicarSiEs(any(), any(), any(), any());
            verify(registrarEvento, never()).registrar(any());
        }

        /** Que la promesa venza o se abra una disputa cambia una marca, no el estado: ni correo ni bitácora. */
        @Test
        void siSoloCambianLasMarcasLasEscribeSinAvisarNiAnexar() {
            ahora = FIN.plusSeconds(60);
            dadoUnSector(EstadoServicio.SIN_SERVICIO, INICIO, INICIO, marcasDeAcuacar());
            given(propuestas.listarAprobadasPorSector(MANGA)).willReturn(List.of(boletinDeAcuacar()));

            servicio.recalcular(MANGA);

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

            assertThat(servicio.recalcular(MANGA).estado()).isNull();
            verify(sectores, never()).publicarSiEs(any(), any(), any(), any());
        }

        @Test
        void unBoletinDeRestablecimientoDeAcuacarDejaElBarrioConServicio() {
            dadoUnSector(EstadoServicio.SIN_SERVICIO, INICIO, INICIO, marcasDeAcuacar());
            PropuestaIngesta restablecimiento = new PropuestaIngesta(new PropuestaId("normal"), MANGA,
                    EstadoServicio.CON_SERVICIO, "acuacar", "https://acuacar.com/2860", "servicio restablecido", 0.85,
                    ahora.minusSeconds(60)).aprobar();
            given(propuestas.listarAprobadasPorSector(MANGA)).willReturn(List.of(boletinDeAcuacar(), restablecimiento));

            EstadoPublicado publicado = servicio.recalcular(MANGA);

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

            assertThat(servicio.recalcular(MANGA).estado()).isEqualTo(EstadoServicio.SIN_SERVICIO);
        }

        @Test
        void unBoletinDePrensaPublicaConOrigenPrensa() {
            dadoUnSector(null);
            given(propuestas.listarAprobadasPorSector(MANGA))
                    .willReturn(List.of(boletin("zona-cero", EstadoServicio.SIN_SERVICIO, INICIO, FIN)));

            EstadoPublicado publicado = servicio.recalcular(MANGA);

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

            EstadoPublicado publicado = servicio.recalcular(MANGA);

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

            EstadoPublicado publicado = servicio.recalcular(MANGA);

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

            assertThat(servicio.recalcular(MANGA).estado()).isNull();
        }

        @Test
        void unCorteCerradoSoloEnOtroBarrioSigueSosteniendoEste() {
            SectorId bocagrande = new SectorId("bocagrande");
            dadoUnSector(null);
            CorteAgua dosBarrios = CorteAgua.builder().id(new CorteId("c2")).sectoresAfectados(List.of(MANGA, bocagrande))
                    .inicio(INICIO).finPrometido(FIN).causa("x").origen(OrigenCorte.VEEDOR).build()
                    .cerrarSector(bocagrande, new CierreDeCorte(INICIO.plusSeconds(1800), OrigenEstado.VEEDOR, false));
            given(cortes.listarPorSector(MANGA)).willReturn(List.of(dosBarrios));

            assertThat(servicio.recalcular(MANGA).estado()).isEqualTo(EstadoServicio.SIN_SERVICIO);
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

            EstadoPublicado publicado = servicio.recalcular(MANGA);

            assertThat(publicado.estado()).isEqualTo(EstadoServicio.SIN_SERVICIO);
            assertThat(publicado.respaldo()).isEqualTo(new RespaldoVecinal(3, 3));
            MarcasDeEstado marcas = new MarcasDeEstado(OrigenEstado.VECINOS, null, false, false, 0, new RespaldoVecinal(3, 3));
            verify(sectores).publicarSiEs(MANGA, EstadoServicio.CON_SERVICIO, EstadoServicio.SIN_SERVICIO, marcas);
            EventoBitacora evento = eventoRegistrado();
            assertThat(evento.tipo()).isEqualTo(TipoEvento.CORTE_CONFIRMADO_POR_CIUDADANOS);
            assertThat(evento.reportesSustento()).containsExactlyInAnyOrder(
                    new ReporteId("r1"), new ReporteId("r2"), new ReporteId("r3"));
        }

        /** En una avería masiva no se cargan los reportes de la ventana en cada petición: primero se cuentan. */
        @Test
        void sinVotosSuficientesNoCargaLosReportes() {
            dadoUnSector(null);
            given(reportes.contarVotosRecientes(any(), any())).willReturn(Map.of(TipoReporte.SIN_AGUA, 1L));

            assertThat(servicio.recalcular(MANGA).estado()).isNull();

            verify(reportes, never()).listarRecientesPorSector(any(), any());
        }

        @Test
        void cadaDispositivoCuentaUnaVezConSuReporteMasReciente() {
            dadoUnSector(null);
            ReporteCiudadano viejoDeH1 = new ReporteCiudadano(new ReporteId("a"), MANGA, TipoReporte.SERVICIO_RESTABLECIDO,
                    null, new HuellaDispositivo("h1"), ahora.minusSeconds(900));
            ReporteCiudadano nuevoDeH1 = new ReporteCiudadano(new ReporteId("b"), MANGA, TipoReporte.SIN_AGUA,
                    null, new HuellaDispositivo("h1"), ahora.minusSeconds(100));
            dadosLosVotos(TipoReporte.SIN_AGUA, 3, nuevoDeH1, viejoDeH1,
                    reporte("r2", TipoReporte.SIN_AGUA, ahora.minusSeconds(200)),
                    reporte("r3", TipoReporte.SIN_AGUA, ahora.minusSeconds(300)));

            servicio.recalcular(MANGA);

            assertThat(eventoRegistrado().reportesSustento()).containsExactlyInAnyOrder(
                    new ReporteId("b"), new ReporteId("r2"), new ReporteId("r3"));
        }

        /** El quórum deja de ser visible a los 30 minutos, pero el estado que produjo se recuerda en el barrio. */
        @Test
        void unEstadoQueSostienenLosVecinosSeRecuerdaSinReportesNuevos() {
            MarcasDeEstado deLosVecinos = new MarcasDeEstado(OrigenEstado.VECINOS, null, false, false, 0, new RespaldoVecinal(3, 3));
            dadoUnSector(EstadoServicio.SIN_SERVICIO, ahora.minus(Duration.ofHours(2)), ahora.minus(Duration.ofHours(2)), deLosVecinos);

            EstadoPublicado publicado = servicio.recalcular(MANGA);

            assertThat(publicado.estado()).isEqualTo(EstadoServicio.SIN_SERVICIO);
            assertThat(publicado.origen()).isEqualTo(OrigenEstado.VECINOS);
            verify(sectores, never()).publicarSiEs(any(), any(), any(), any());
            // Sin reportes nuevos nadie lo verificó: renovar la marca lo haría inmortal.
            verify(sectores, never()).confirmarEstado(any(), any());
        }

        @Test
        void unEstadoDeLosVecinosSinRenovarVuelveASinDatosALas24HorasSinAvisarANadie() {
            MarcasDeEstado deLosVecinos = new MarcasDeEstado(OrigenEstado.VECINOS, null, false, false, 0, new RespaldoVecinal(3, 3));
            Instant hace24h = ahora.minus(Duration.ofHours(24));
            dadoUnSector(EstadoServicio.SIN_SERVICIO, hace24h, hace24h, deLosVecinos);

            EstadoPublicado publicado = servicio.recalcular(MANGA);

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

            servicio.recalcular(MANGA);

            verify(sectores).confirmarEstado(MANGA, EstadoServicio.SIN_SERVICIO);
            verify(sectores, never()).publicarSiEs(any(), any(), any(), any());
        }

        @Test
        void unQuorumQueContradiceAAcuacarMarcaLaDisputaSinCambiarElEstadoNiAvisar() {
            dadoUnSector(EstadoServicio.SIN_SERVICIO, INICIO, INICIO, marcasDeAcuacar());
            given(propuestas.listarAprobadasPorSector(MANGA)).willReturn(List.of(boletinDeAcuacar()));
            dadosLosVotos(TipoReporte.SERVICIO_RESTABLECIDO, 3,
                    reporte("r1", TipoReporte.SERVICIO_RESTABLECIDO, ahora.minusSeconds(300)),
                    reporte("r2", TipoReporte.SERVICIO_RESTABLECIDO, ahora.minusSeconds(200)),
                    reporte("r3", TipoReporte.SERVICIO_RESTABLECIDO, ahora.minusSeconds(100)));

            servicio.recalcular(MANGA);

            MarcasDeEstado enDisputa = new MarcasDeEstado(OrigenEstado.ACUACAR, new VentanaTiempo(INICIO, FIN), false, true, 3, null);
            verify(sectores).publicarSiEs(MANGA, EstadoServicio.SIN_SERVICIO, EstadoServicio.SIN_SERVICIO, enDisputa);
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

            EstadoPublicado publicado = servicio.recalcular(MANGA);

            assertThat(publicado.estado()).isEqualTo(EstadoServicio.CON_SERVICIO);
            assertThat(publicado.origen()).isEqualTo(OrigenEstado.VECINOS);
            assertThat(eventoRegistrado().tipo()).isEqualTo(TipoEvento.CORTE_CONFIRMADO_POR_CIUDADANOS);
        }
    }

    @Nested
    class VerificacionDelEstado {

        /** Un boletín que sigue sosteniendo el estado lo verifica, pero sin escribir en cada barrido. */
        @Test
        void unaFuenteOficialQueSostieneElEstadoLoVerificaCuandoTocaYNoAntes() {
            dadoUnSector(EstadoServicio.SIN_SERVICIO, INICIO, ahora.minus(Duration.ofMinutes(10)), marcasDeAcuacar());
            given(propuestas.listarAprobadasPorSector(MANGA)).willReturn(List.of(boletinDeAcuacar()));

            servicio.recalcular(MANGA);
            verify(sectores).confirmarEstado(MANGA, EstadoServicio.SIN_SERVICIO);
        }

        @Test
        void noVuelveAVerificarSiLoHizoHaceMenosDeCincoMinutos() {
            dadoUnSector(EstadoServicio.SIN_SERVICIO, INICIO, ahora.minus(Duration.ofMinutes(2)), marcasDeAcuacar());
            given(propuestas.listarAprobadasPorSector(MANGA)).willReturn(List.of(boletinDeAcuacar()));

            servicio.recalcular(MANGA);

            verify(sectores, never()).confirmarEstado(any(), any());
        }
    }
}
