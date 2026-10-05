package com.aguavigia.ctg.domain;

import com.aguavigia.ctg.domain.Afirmacion.CorteVeedor;
import com.aguavigia.ctg.domain.Afirmacion.PrensaAprobada;
import com.aguavigia.ctg.domain.Afirmacion.QuorumVecinos;
import com.aguavigia.ctg.domain.Afirmacion.VentanaOficial;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tabla de verdad del resolutor único (plan §3.2): qué estado publica un barrio según las
 * afirmaciones que llegan de cada fuente y la hora. Es dominio puro: nada de mocks ni reloj real.
 * Asimetría deliberada: las malas noticias viajan rápido y las buenas despacio.
 */
class ResolutorDeEstadoSectorTest {

    private static final Instant INICIO = Instant.parse("2026-08-21T14:00:00Z");
    private static final Instant FIN = Instant.parse("2026-08-21T23:00:00Z");

    private final ResolutorDeEstadoSector resolutor = new ResolutorDeEstadoSector(ReglasDeEstado.porDefecto());

    private EstadoPublicado resolver(Instant ahora, Afirmacion... afirmaciones) {
        return resolutor.resolver(List.of(afirmaciones), ahora);
    }

    private static VentanaOficial ventanaDeAcuacar() {
        return new VentanaOficial(INICIO, FIN, null);
    }

    /**
     * Un boletín de «servicio restablecido» de hace meses —lo normal al ingerir el histórico de Acuacar— dice qué pasó
     * entonces, no qué pasa hoy. Fijar CON_SERVICIO con él sería el mismo dato congelado que D3 evita con los cortes.
     */
    @Test
    void unRestablecimientoOficialMuyViejoNoFijaElEstadoDeHoy() {
        Instant ahora = INICIO.plus(Duration.ofDays(30));

        EstadoPublicado publicado = resolver(ahora, new Afirmacion.RestablecimientoOficial(ahora.minus(Duration.ofDays(400))));

        assertThat(publicado.estado()).isNull();
    }

    @Test
    void unRestablecimientoOficialDentroDelPlazoSiFijaConServicio() {
        EstadoPublicado publicado = resolver(INICIO,
                new Afirmacion.RestablecimientoOficial(INICIO.minus(Duration.ofHours(71))));

        assertThat(publicado.estado()).isEqualTo(EstadoServicio.CON_SERVICIO);
        assertThat(publicado.origen()).isEqualTo(OrigenEstado.ACUACAR);
    }

    @Test
    void sinNingunaAfirmacionNadieSabeYElEstadoEsNulo() {
        EstadoPublicado publicado = resolver(INICIO);

        assertThat(publicado.estado()).isNull();
        assertThat(publicado.origen()).isNull();
        assertThat(publicado.enDisputa()).isFalse();
    }

    @Nested
    class VentanaDeAcuacar {

        @Test
        void antesDeQueEmpieceEsCorteProgramadoYDeclaraLaVentanaPrometida() {
            EstadoPublicado publicado = resolver(INICIO.minusSeconds(1800), ventanaDeAcuacar());

            assertThat(publicado.estado()).isEqualTo(EstadoServicio.CORTE_PROGRAMADO);
            assertThat(publicado.origen()).isEqualTo(OrigenEstado.ACUACAR);
            assertThat(publicado.ventanaPrometida()).isEqualTo(new VentanaTiempo(INICIO, FIN));
        }

        @Test
        void duranteLaVentanaEsSinServicio() {
            EstadoPublicado publicado = resolver(INICIO.plusSeconds(3600), ventanaDeAcuacar());

            assertThat(publicado.estado()).isEqualTo(EstadoServicio.SIN_SERVICIO);
            assertThat(publicado.origen()).isEqualTo(OrigenEstado.ACUACAR);
            assertThat(publicado.restablecimientoPorConfirmar()).isFalse();
        }

        /** Que la promesa se cumpla no es prueba de que volvió el agua: el barrio sigue sin servicio hasta que alguien lo confirme. */
        @Test
        void alVencerLaPromesaSigueSinServicioYQuedaPorConfirmar() {
            EstadoPublicado publicado = resolver(FIN.plusSeconds(60), ventanaDeAcuacar());

            assertThat(publicado.estado()).isEqualTo(EstadoServicio.SIN_SERVICIO);
            assertThat(publicado.restablecimientoPorConfirmar()).isTrue();
            assertThat(publicado.ventanaPrometida()).isEqualTo(new VentanaTiempo(INICIO, FIN));
        }

        @Test
        void unCorteSinConfirmarExpiraALas72HorasYElBarrioVuelveASinDatos() {
            Instant expira = FIN.plus(Duration.ofHours(72));

            assertThat(resolver(expira.minusSeconds(1), ventanaDeAcuacar()).estado())
                    .isEqualTo(EstadoServicio.SIN_SERVICIO);

            EstadoPublicado expirado = resolver(expira, ventanaDeAcuacar());
            assertThat(expirado.estado()).isNull();
            assertThat(expirado.origen()).isNull();
            assertThat(expirado.ventanaPrometida()).isNull();
        }
    }

    /** Un boletín que anuncia baja presión con ventana no es un corte: durante la ventana el barrio sigue con presión baja. */
    @Nested
    class VentanaDePresionBaja {

        private VentanaOficial presionBajaDeAcuacar() {
            return new VentanaOficial(INICIO, FIN, null, EstadoServicio.PRESION_BAJA);
        }

        @Test
        void duranteLaVentanaPublicaPresionBajaYNoSinServicio() {
            EstadoPublicado publicado = resolver(INICIO.plusSeconds(3600), presionBajaDeAcuacar());

            assertThat(publicado.estado()).isEqualTo(EstadoServicio.PRESION_BAJA);
            assertThat(publicado.origen()).isEqualTo(OrigenEstado.ACUACAR);
        }

        @Test
        void antesDeQueEmpieceEsCorteProgramadoComoCualquierVentana() {
            assertThat(resolver(INICIO.minusSeconds(60), presionBajaDeAcuacar()).estado())
                    .isEqualTo(EstadoServicio.CORTE_PROGRAMADO);
        }

        @Test
        void alVencerLaPromesaSigueConPresionBajaYQuedaPorConfirmar() {
            EstadoPublicado publicado = resolver(FIN.plusSeconds(60), presionBajaDeAcuacar());

            assertThat(publicado.estado()).isEqualTo(EstadoServicio.PRESION_BAJA);
            assertThat(publicado.restablecimientoPorConfirmar()).isTrue();
        }

        @Test
        void unCorteSinServicioPrevaleceSobreUnaPresionBajaEnCualquierOrden() {
            Instant ahora = INICIO.plusSeconds(3600);
            PrensaAprobada corte = new PrensaAprobada(INICIO, FIN, null);

            assertThat(resolver(ahora, presionBajaDeAcuacar(), corte).estado()).isEqualTo(EstadoServicio.SIN_SERVICIO);
            assertThat(resolver(ahora, corte, presionBajaDeAcuacar()).estado()).isEqualTo(EstadoServicio.SIN_SERVICIO);
        }

        @Test
        void unaVentanaSoloPuedeDeclararSinServicioOPresionBaja() {
            org.assertj.core.api.Assertions.assertThatThrownBy(
                    () -> new VentanaOficial(INICIO, FIN, null, EstadoServicio.CON_SERVICIO))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class VentanaDePrensaAprobada {

        @Test
        void duranteLaVentanaEsSinServicioConOrigenPrensa() {
            EstadoPublicado publicado = resolver(INICIO.plusSeconds(3600), new PrensaAprobada(INICIO, FIN, null));

            assertThat(publicado.estado()).isEqualTo(EstadoServicio.SIN_SERVICIO);
            assertThat(publicado.origen()).isEqualTo(OrigenEstado.PRENSA);
        }

        @Test
        void siAcuacarYLaPrensaCoincidenGanaAcuacarEnCualquierOrden() {
            Instant ahora = INICIO.plusSeconds(3600);
            PrensaAprobada prensa = new PrensaAprobada(INICIO, FIN, null);

            assertThat(resolver(ahora, ventanaDeAcuacar(), prensa).origen()).isEqualTo(OrigenEstado.ACUACAR);
            assertThat(resolver(ahora, prensa, ventanaDeAcuacar()).origen()).isEqualTo(OrigenEstado.ACUACAR);
        }

        /** Dos avisos solapados no pueden dar un resultado que dependa del orden en que lleguen. */
        @Test
        void ganaLaVentanaMasSeveraSinImportarElOrden() {
            Instant ahora = INICIO.plusSeconds(3600);
            VentanaOficial yaEnCurso = ventanaDeAcuacar();
            PrensaAprobada anunciadaParaDespues = new PrensaAprobada(FIN.plusSeconds(3600), FIN.plusSeconds(7200), null);

            assertThat(resolver(ahora, yaEnCurso, anunciadaParaDespues).estado()).isEqualTo(EstadoServicio.SIN_SERVICIO);
            assertThat(resolver(ahora, anunciadaParaDespues, yaEnCurso).estado()).isEqualTo(EstadoServicio.SIN_SERVICIO);
        }
    }

    @Nested
    class CorteDelVeedor {

        @Test
        void duranteLaVentanaEsSinServicioConOrigenVeedor() {
            EstadoPublicado publicado = resolver(INICIO.plusSeconds(3600), new CorteVeedor(INICIO, FIN, null, null));

            assertThat(publicado.estado()).isEqualTo(EstadoServicio.SIN_SERVICIO);
            assertThat(publicado.origen()).isEqualTo(OrigenEstado.VEEDOR);
        }

        @Test
        void elVeedorPrevaleceSobreAcuacarCuandoHablanDelMismoMomento() {
            Instant ahora = INICIO.plusSeconds(3600);
            CorteVeedor delVeedor = new CorteVeedor(INICIO, FIN, null, null);

            assertThat(resolver(ahora, ventanaDeAcuacar(), delVeedor).origen()).isEqualTo(OrigenEstado.VEEDOR);
            assertThat(resolver(ahora, delVeedor, ventanaDeAcuacar()).origen()).isEqualTo(OrigenEstado.VEEDOR);
        }

        @Test
        void unCorteDelVeedorCaducadoSeIgnoraYDejaHablarALaVentanaOficial() {
            Instant caducaEn = INICIO.plusSeconds(1800);
            CorteVeedor caducado = new CorteVeedor(INICIO, FIN, caducaEn, null);

            EstadoPublicado publicado = resolver(caducaEn, caducado, ventanaDeAcuacar());
            assertThat(publicado.origen()).isEqualTo(OrigenEstado.ACUACAR);

            assertThat(resolver(caducaEn, caducado).estado()).isNull();
        }

        @Test
        void unCorteDelVeedorSinCaducidadSigueVigenteHastaQueAlguienLoCierre() {
            EstadoPublicado publicado = resolver(FIN.plus(Duration.ofHours(10)), new CorteVeedor(INICIO, FIN, null, null));

            assertThat(publicado.estado()).isEqualTo(EstadoServicio.SIN_SERVICIO);
            assertThat(publicado.restablecimientoPorConfirmar()).isTrue();
        }
    }

    @Nested
    class Restablecimiento {

        private final Instant cierreA = INICIO.plus(Duration.ofHours(5));

        @Test
        void unCorteCerradoPorElVeedorDejaElBarrioConServicio() {
            CierreDeCorte delVeedor = new CierreDeCorte(cierreA, OrigenEstado.VEEDOR, false);

            EstadoPublicado publicado = resolver(cierreA.plusSeconds(60), new VentanaOficial(INICIO, FIN, delVeedor));

            assertThat(publicado.estado()).isEqualTo(EstadoServicio.CON_SERVICIO);
            assertThat(publicado.origen()).isEqualTo(OrigenEstado.VEEDOR);
            assertThat(publicado.restablecimientoPorConfirmar()).isFalse();
        }

        @Test
        void unCierreQueSostienenLosVecinosAtribuyeElEstadoALosVecinos() {
            CierreDeCorte deLosVecinos = new CierreDeCorte(cierreA, OrigenEstado.VECINOS, true);

            EstadoPublicado publicado = resolver(cierreA.plusSeconds(60), new VentanaOficial(INICIO, FIN, deLosVecinos));

            assertThat(publicado.estado()).isEqualTo(EstadoServicio.CON_SERVICIO);
            assertThat(publicado.origen()).isEqualTo(OrigenEstado.VECINOS);
        }

        /** Mirar el barrio en un momento anterior a su cierre debe dar lo que valía entonces. */
        @Test
        void unCierreFuturoTodaviaNoCuenta() {
            CierreDeCorte cierre = new CierreDeCorte(cierreA, OrigenEstado.VEEDOR, false);

            EstadoPublicado publicado = resolver(cierreA.minusSeconds(60), new VentanaOficial(INICIO, FIN, cierre));

            assertThat(publicado.estado()).isEqualTo(EstadoServicio.SIN_SERVICIO);
        }

        @Test
        void unBoletinDeRestablecimientoCierraLaVentanaDeAcuacar() {
            Instant publicado = INICIO.plus(Duration.ofHours(6));

            EstadoPublicado resuelto = resolver(publicado.plusSeconds(60),
                    ventanaDeAcuacar(), new Afirmacion.RestablecimientoOficial(publicado));

            assertThat(resuelto.estado()).isEqualTo(EstadoServicio.CON_SERVICIO);
            assertThat(resuelto.origen()).isEqualTo(OrigenEstado.ACUACAR);
            assertThat(resuelto.restablecimientoPorConfirmar()).isFalse();
        }

        @Test
        void unBoletinDeRestablecimientoSinNingunaVentanaDejaElBarrioConServicio() {
            EstadoPublicado resuelto = resolver(INICIO, new Afirmacion.RestablecimientoOficial(INICIO.minusSeconds(60)));

            assertThat(resuelto.estado()).isEqualTo(EstadoServicio.CON_SERVICIO);
            assertThat(resuelto.origen()).isEqualTo(OrigenEstado.ACUACAR);
        }

        /** Un corte futuro nunca se cierra por una señal de restablecimiento que es anterior a su inicio. */
        @Test
        void unRestablecimientoAnteriorAlInicioNoCierraUnCorteProgramado() {
            EstadoPublicado resuelto = resolver(INICIO.minusSeconds(3600),
                    ventanaDeAcuacar(), new Afirmacion.RestablecimientoOficial(INICIO.minus(Duration.ofHours(5))));

            assertThat(resuelto.estado()).isEqualTo(EstadoServicio.CORTE_PROGRAMADO);
        }

        /** El corte del veedor es el override: solo el propio veedor lo cierra. */
        @Test
        void unBoletinDeRestablecimientoNoCierraUnCorteDelVeedor() {
            Instant publicado = INICIO.plus(Duration.ofHours(6));

            EstadoPublicado resuelto = resolver(publicado.plusSeconds(60),
                    new CorteVeedor(INICIO, FIN, null, null), new Afirmacion.RestablecimientoOficial(publicado));

            assertThat(resuelto.estado()).isEqualTo(EstadoServicio.SIN_SERVICIO);
            assertThat(resuelto.origen()).isEqualTo(OrigenEstado.VEEDOR);
        }

        /** Cerrar un corte no puede ocultar que otro sigue abierto sobre el mismo barrio. */
        @Test
        void unCorteCerradoNoOcultaOtroCorteAbiertoDelMismoBarrio() {
            CierreDeCorte cierre = new CierreDeCorte(cierreA, OrigenEstado.VEEDOR, false);
            VentanaOficial cerrada = new VentanaOficial(INICIO, FIN, cierre);
            PrensaAprobada abierta = new PrensaAprobada(INICIO, FIN.plusSeconds(7200), null);

            assertThat(resolver(cierreA.plusSeconds(60), cerrada, abierta).estado()).isEqualTo(EstadoServicio.SIN_SERVICIO);
            assertThat(resolver(cierreA.plusSeconds(60), abierta, cerrada).estado()).isEqualTo(EstadoServicio.SIN_SERVICIO);
        }

        @Test
        void conVariosCierresLaFuenteEsLaDelMasReciente() {
            VentanaOficial cerradaPorLosVecinos = new VentanaOficial(INICIO, FIN,
                    new CierreDeCorte(cierreA, OrigenEstado.VECINOS, true));
            PrensaAprobada cerradaPorElVeedor = new PrensaAprobada(INICIO, FIN,
                    new CierreDeCorte(cierreA.plus(Duration.ofMinutes(20)), OrigenEstado.VEEDOR, false));
            Instant ahora = cierreA.plus(Duration.ofHours(1));

            assertThat(resolver(ahora, cerradaPorLosVecinos, cerradaPorElVeedor).origen()).isEqualTo(OrigenEstado.VEEDOR);
            assertThat(resolver(ahora, cerradaPorElVeedor, cerradaPorLosVecinos).origen()).isEqualTo(OrigenEstado.VEEDOR);
        }
    }

    private static QuorumVecinos quorum(TipoReporte tipo, int respaldo, int umbral, Instant ultimoReporte) {
        return new QuorumVecinos(tipo, respaldo, umbral, true, ultimoReporte.minusSeconds(600), ultimoReporte);
    }

    @Nested
    class SoloVecinos {

        private final Instant ahora = INICIO;

        @Test
        void unQuorumDeSinAguaPublicaSinServicioConElRespaldoDeLosVecinos() {
            EstadoPublicado publicado = resolver(ahora, quorum(TipoReporte.SIN_AGUA, 3, 3, ahora.minusSeconds(300)));

            assertThat(publicado.estado()).isEqualTo(EstadoServicio.SIN_SERVICIO);
            assertThat(publicado.origen()).isEqualTo(OrigenEstado.VECINOS);
            assertThat(publicado.respaldo()).isEqualTo(new RespaldoVecinal(3, 3));
            assertThat(publicado.enDisputa()).isFalse();
        }

        @Test
        void cadaTipoDeReporteDaSuEstado() {
            assertThat(resolver(ahora, quorum(TipoReporte.PRESION_BAJA, 4, 4, ahora)).estado())
                    .isEqualTo(EstadoServicio.PRESION_BAJA);
            assertThat(resolver(ahora, quorum(TipoReporte.SERVICIO_RESTABLECIDO, 4, 4, ahora)).estado())
                    .isEqualTo(EstadoServicio.CON_SERVICIO);
        }

        @Test
        void sinLosVecinosSuficientesNadieSabe() {
            assertThat(resolver(ahora, quorum(TipoReporte.SIN_AGUA, 2, 3, ahora)).estado()).isNull();
        }

        /** Tres reportes desde una misma red, sin ninguno verificado, no son un quórum aunque sean tres. */
        @Test
        void unQuorumConComposicionInvalidaNoCuenta() {
            QuorumVecinos deUnaSolaRed = new QuorumVecinos(TipoReporte.SIN_AGUA, 3, 3, false, ahora, ahora);

            assertThat(resolver(ahora, deUnaSolaRed).estado()).isNull();
        }

        /**
         * Un quórum que el barrio ya recuerda se alcanzó en su momento —quizá con el umbral reducido de un
         * restablecimiento— y no se vuelve a exigir: solo caduca con el tiempo.
         */
        @Test
        void unQuorumSostenidoNoVuelveAPedirElUmbralCompleto() {
            QuorumVecinos recordado = new QuorumVecinos(TipoReporte.SERVICIO_RESTABLECIDO, 2, 3, true,
                    ahora.minus(Duration.ofHours(2)), ahora.minus(Duration.ofHours(2)), true);

            EstadoPublicado publicado = resolver(ahora, recordado);

            assertThat(publicado.estado()).isEqualTo(EstadoServicio.CON_SERVICIO);
            assertThat(publicado.respaldo()).isEqualTo(new RespaldoVecinal(2, 3));
        }

        @Test
        void unQuorumSostenidoTambienCaducaALas24Horas() {
            Instant ultimo = ahora.minus(Duration.ofHours(24));
            QuorumVecinos recordado = new QuorumVecinos(TipoReporte.SIN_AGUA, 3, 3, true, ultimo, ultimo, true);

            assertThat(resolver(ahora, recordado).estado()).isNull();
        }

        @Test
        void unEmpateEntreDosQuorumsEsEvidenciaAmbiguaYNoPublicaNada() {
            assertThat(resolver(ahora,
                    quorum(TipoReporte.SIN_AGUA, 3, 3, ahora), quorum(TipoReporte.SERVICIO_RESTABLECIDO, 3, 3, ahora))
                    .estado()).isNull();
        }

        @Test
        void conDosQuorumsGanaElQueTieneMasRespaldoEnCualquierOrden() {
            QuorumVecinos pocos = quorum(TipoReporte.SIN_AGUA, 3, 3, ahora);
            QuorumVecinos muchos = quorum(TipoReporte.SERVICIO_RESTABLECIDO, 5, 3, ahora);

            assertThat(resolver(ahora, pocos, muchos).estado()).isEqualTo(EstadoServicio.CON_SERVICIO);
            assertThat(resolver(ahora, muchos, pocos).estado()).isEqualTo(EstadoServicio.CON_SERVICIO);
        }

        @Test
        void unEstadoQueSoloSostienenLosVecinosSeMarcaSinVerificacionRecienteALas6Horas() {
            Instant ultimo = ahora.minus(Duration.ofHours(6));

            EstadoPublicado publicado = resolver(ahora, quorum(TipoReporte.SIN_AGUA, 3, 3, ultimo));

            assertThat(publicado.estado()).isEqualTo(EstadoServicio.SIN_SERVICIO);
            assertThat(publicado.sinVerificacionReciente()).isTrue();
            assertThat(resolver(ahora, quorum(TipoReporte.SIN_AGUA, 3, 3, ultimo.plusSeconds(1)))
                    .sinVerificacionReciente()).isFalse();
        }

        @Test
        void unEstadoDeLosVecinosSinRenovarVuelveASinDatosALas24Horas() {
            Instant ultimo = ahora.minus(Duration.ofHours(24));

            assertThat(resolver(ahora, quorum(TipoReporte.SIN_AGUA, 3, 3, ultimo.plusSeconds(1))).estado())
                    .isEqualTo(EstadoServicio.SIN_SERVICIO);
            assertThat(resolver(ahora, quorum(TipoReporte.SIN_AGUA, 3, 3, ultimo)).estado()).isNull();
        }
    }

    @Nested
    class VecinosContraLaFuenteOficial {

        private final Instant duranteLaVentana = INICIO.plusSeconds(3600);

        /** Un quórum en contra no cambia el color: lo marca en disputa para que el veedor decida. */
        @Test
        void unQuorumQueContradiceAAcuacarDejaElEstadoEnDisputaSinCambiarleElColor() {
            EstadoPublicado publicado = resolver(duranteLaVentana, ventanaDeAcuacar(),
                    quorum(TipoReporte.SERVICIO_RESTABLECIDO, 11, 11, duranteLaVentana));

            assertThat(publicado.estado()).isEqualTo(EstadoServicio.SIN_SERVICIO);
            assertThat(publicado.origen()).isEqualTo(OrigenEstado.ACUACAR);
            assertThat(publicado.enDisputa()).isTrue();
            assertThat(publicado.reportesEnContra()).isEqualTo(11);
        }

        @Test
        void sinQuorumLosReportesSueltosNoAbrenDisputa() {
            EstadoPublicado publicado = resolver(duranteLaVentana, ventanaDeAcuacar(),
                    quorum(TipoReporte.SERVICIO_RESTABLECIDO, 5, 11, duranteLaVentana));

            assertThat(publicado.enDisputa()).isFalse();
            assertThat(publicado.reportesEnContra()).isZero();
        }

        @Test
        void unQuorumViejoNoAbreDisputa() {
            EstadoPublicado publicado = resolver(duranteLaVentana, ventanaDeAcuacar(),
                    quorum(TipoReporte.SERVICIO_RESTABLECIDO, 11, 11, duranteLaVentana.minus(Duration.ofHours(24))));

            assertThat(publicado.enDisputa()).isFalse();
        }

        @Test
        void elVeedorNoEsDisputadoPorLosVecinos() {
            EstadoPublicado publicado = resolver(duranteLaVentana, new CorteVeedor(INICIO, FIN, null, null),
                    quorum(TipoReporte.SERVICIO_RESTABLECIDO, 11, 11, duranteLaVentana));

            assertThat(publicado.estado()).isEqualTo(EstadoServicio.SIN_SERVICIO);
            assertThat(publicado.enDisputa()).isFalse();
        }

        /** Las malas noticias viajan rápido: si el agua se fue antes de la hora anunciada, los vecinos lo adelantan. */
        @Test
        void unQuorumDeSinAguaAdelantaUnCorteQueAunEstabaProgramado() {
            Instant antes = INICIO.minusSeconds(1800);

            EstadoPublicado publicado = resolver(antes, ventanaDeAcuacar(), quorum(TipoReporte.SIN_AGUA, 3, 3, antes));

            assertThat(publicado.estado()).isEqualTo(EstadoServicio.SIN_SERVICIO);
            assertThat(publicado.origen()).isEqualTo(OrigenEstado.VECINOS);
            assertThat(publicado.enDisputa()).isFalse();
        }
    }

    @Nested
    class RestablecimientoPorConfirmar {

        private final Instant pasadaLaPromesa = FIN.plusSeconds(1800);

        private EstadoPublicado conQuorumDeRestablecido(int respaldo, int umbral) {
            return resolver(pasadaLaPromesa, ventanaDeAcuacar(),
                    quorum(TipoReporte.SERVICIO_RESTABLECIDO, respaldo, umbral, pasadaLaPromesa));
        }

        /** Pasada la promesa basta la mitad del umbral (mínimo 2): confirmar que volvió el agua es lo escaso. */
        @Test
        void trasElFinPrometidoBastaLaMitadDelUmbralParaConfirmarElRestablecimiento() {
            EstadoPublicado publicado = conQuorumDeRestablecido(3, 6);

            assertThat(publicado.estado()).isEqualTo(EstadoServicio.CON_SERVICIO);
            assertThat(publicado.origen()).isEqualTo(OrigenEstado.VECINOS);
            assertThat(publicado.restablecimientoPorConfirmar()).isFalse();
        }

        @Test
        void alMenosDosVecinosAunqueElUmbralSeaElMinimo() {
            assertThat(conQuorumDeRestablecido(2, 3).estado()).isEqualTo(EstadoServicio.CON_SERVICIO);
            assertThat(conQuorumDeRestablecido(1, 3).estado()).isEqualTo(EstadoServicio.SIN_SERVICIO);
        }

        @Test
        void conUnUmbralAltoLaMitadSeRedondeaHaciaArriba() {
            assertThat(conQuorumDeRestablecido(8, 15).estado()).isEqualTo(EstadoServicio.CON_SERVICIO);
            assertThat(conQuorumDeRestablecido(7, 15).estado()).isEqualTo(EstadoServicio.SIN_SERVICIO);
        }

        @Test
        void sinElQuorumReducidoSigueSinServicioYPorConfirmar() {
            EstadoPublicado publicado = conQuorumDeRestablecido(2, 6);

            assertThat(publicado.estado()).isEqualTo(EstadoServicio.SIN_SERVICIO);
            assertThat(publicado.restablecimientoPorConfirmar()).isTrue();
        }

        /** El quórum reducido es para confirmar una promesa vencida; en plena ventana rige el umbral completo. */
        @Test
        void duranteLaVentanaNoAplicaElQuorumReducido() {
            Instant durante = INICIO.plusSeconds(3600);

            EstadoPublicado publicado = resolver(durante, ventanaDeAcuacar(),
                    quorum(TipoReporte.SERVICIO_RESTABLECIDO, 3, 6, durante));

            assertThat(publicado.estado()).isEqualTo(EstadoServicio.SIN_SERVICIO);
            assertThat(publicado.enDisputa()).isFalse();
        }

        /** El veedor manda mientras dura su corte, pero una promesa vencida también la confirman los vecinos. */
        @Test
        void unCorteDelVeedorConLaPromesaVencidaTambienLoConfirmanLosVecinos() {
            EstadoPublicado publicado = resolver(pasadaLaPromesa, new CorteVeedor(INICIO, FIN, null, null),
                    quorum(TipoReporte.SERVICIO_RESTABLECIDO, 3, 6, pasadaLaPromesa));

            assertThat(publicado.estado()).isEqualTo(EstadoServicio.CON_SERVICIO);
            assertThat(publicado.origen()).isEqualTo(OrigenEstado.VECINOS);
        }
    }

    @Nested
    class VecinosContradicenUnRestablecimiento {

        private final Instant cierreA = INICIO.plus(Duration.ofHours(5));
        private final Instant ahora = cierreA.plus(Duration.ofHours(2));

        private VentanaOficial cerradaPorElVeedor() {
            return new VentanaOficial(INICIO, FIN, new CierreDeCorte(cierreA, OrigenEstado.VEEDOR, false));
        }

        @Test
        void unQuorumDeSinAguaPosteriorAlCierreReabreElBarrio() {
            EstadoPublicado publicado = resolver(ahora, cerradaPorElVeedor(),
                    quorum(TipoReporte.SIN_AGUA, 15, 15, ahora.minusSeconds(300)));

            assertThat(publicado.estado()).isEqualTo(EstadoServicio.SIN_SERVICIO);
            assertThat(publicado.origen()).isEqualTo(OrigenEstado.VECINOS);
        }

        @Test
        void unQuorumAnteriorAlCierreNoLoContradice() {
            EstadoPublicado publicado = resolver(ahora, cerradaPorElVeedor(),
                    quorum(TipoReporte.SIN_AGUA, 15, 15, cierreA.minusSeconds(300)));

            assertThat(publicado.estado()).isEqualTo(EstadoServicio.CON_SERVICIO);
            assertThat(publicado.origen()).isEqualTo(OrigenEstado.VEEDOR);
        }

        @Test
        void unBoletinDeRestablecimientoTampocoImpideQueLosVecinosLoContradigan() {
            Instant publicado = INICIO.plus(Duration.ofHours(6));

            EstadoPublicado resuelto = resolver(ahora, ventanaDeAcuacar(), new Afirmacion.RestablecimientoOficial(publicado),
                    quorum(TipoReporte.SIN_AGUA, 6, 6, ahora.minusSeconds(300)));

            assertThat(resuelto.estado()).isEqualTo(EstadoServicio.SIN_SERVICIO);
            assertThat(resuelto.origen()).isEqualTo(OrigenEstado.VECINOS);
        }

        @Test
        void unQuorumDePresionBajaPosteriorAlCierreLoDegradaAPresionBaja() {
            EstadoPublicado publicado = resolver(ahora, cerradaPorElVeedor(),
                    quorum(TipoReporte.PRESION_BAJA, 6, 6, ahora.minusSeconds(300)));

            assertThat(publicado.estado()).isEqualTo(EstadoServicio.PRESION_BAJA);
            assertThat(publicado.origen()).isEqualTo(OrigenEstado.VECINOS);
        }
    }
}
