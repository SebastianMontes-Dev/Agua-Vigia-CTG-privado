package com.aguavigia.ctg.domain;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CorteAguaTest {

    private final Instant inicio = Instant.parse("2026-08-07T10:00:00Z");

    @Test
    void debeConstruirCorteValido() {
        CorteAgua corte = CorteAgua.builder()
                .id(new CorteId("corte-1"))
                .sectoresAfectados(List.of(new SectorId("manga")))
                .inicio(inicio)
                .finPrometido(inicio.plus(6, ChronoUnit.HOURS))
                .causa("Mantenimiento planta El Bosque")
                .origen(OrigenCorte.OFICIAL_ACUACAR)
                .build();

        assertThat(corte.estado()).isEqualTo(EstadoCorte.ANUNCIADO);
        assertThat(corte.ventana().estaCerrada()).isFalse();
    }

    @Test
    void debeRechazarCorteConFinAnteriorAlInicio() {
        var builder = CorteAgua.builder()
                .id(new CorteId("corte-2"))
                .sectoresAfectados(List.of(new SectorId("manga")))
                .inicio(inicio)
                .finPrometido(inicio.minus(1, ChronoUnit.HOURS))
                .causa("Mantenimiento")
                .origen(OrigenCorte.OFICIAL_ACUACAR);

        assertThatThrownBy(builder::build).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void debeRechazarCorteSinSectoresAfectados() {
        var builder = CorteAgua.builder()
                .id(new CorteId("corte-3"))
                .inicio(inicio)
                .finPrometido(inicio.plus(1, ChronoUnit.HOURS))
                .causa("Mantenimiento")
                .origen(OrigenCorte.OFICIAL_ACUACAR);

        assertThatThrownBy(builder::build).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void debeRechazarUnCorteRestablecidoSinFinReal() {
        var builder = CorteAgua.builder()
                .id(new CorteId("corte-4"))
                .sectoresAfectados(List.of(new SectorId("manga")))
                .inicio(inicio)
                .finPrometido(inicio.plus(6, ChronoUnit.HOURS))
                .causa("Mantenimiento")
                .origen(OrigenCorte.OFICIAL_ACUACAR)
                .estado(EstadoCorte.RESTABLECIDO);

        assertThatThrownBy(builder::build).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void debeRechazarUnCorteAnunciadoConFinRealYaPuesto() {
        var builder = CorteAgua.builder()
                .id(new CorteId("corte-5"))
                .sectoresAfectados(List.of(new SectorId("manga")))
                .inicio(inicio)
                .finPrometido(inicio.plus(6, ChronoUnit.HOURS))
                .finReal(inicio.plus(5, ChronoUnit.HOURS))
                .causa("Mantenimiento")
                .origen(OrigenCorte.OFICIAL_ACUACAR)
                .estado(EstadoCorte.ANUNCIADO);

        assertThatThrownBy(builder::build).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void debeCerrarUnCorteAbiertoYQuedarCoherente() {
        CorteAgua corte = CorteAgua.builder()
                .id(new CorteId("corte-6"))
                .sectoresAfectados(List.of(new SectorId("manga")))
                .inicio(inicio)
                .finPrometido(inicio.plus(6, ChronoUnit.HOURS))
                .causa("Mantenimiento")
                .origen(OrigenCorte.OFICIAL_ACUACAR)
                .build();

        Instant finReal = inicio.plus(5, ChronoUnit.HOURS);
        CorteAgua cerrado = corte.cerrar(finReal);

        assertThat(cerrado.estado()).isEqualTo(EstadoCorte.RESTABLECIDO);
        assertThat(cerrado.ventana().finReal()).isEqualTo(finReal);
        assertThat(cerrado.ventana().estaCerrada()).isTrue();
    }

    @Test
    void debeRechazarCerrarUnCorteYaCerrado() {
        CorteAgua corte = CorteAgua.builder()
                .id(new CorteId("corte-7"))
                .sectoresAfectados(List.of(new SectorId("manga")))
                .inicio(inicio)
                .finPrometido(inicio.plus(6, ChronoUnit.HOURS))
                .finReal(inicio.plus(5, ChronoUnit.HOURS))
                .causa("Mantenimiento")
                .origen(OrigenCorte.OFICIAL_ACUACAR)
                .estado(EstadoCorte.RESTABLECIDO)
                .build();

        assertThatThrownBy(() -> corte.cerrar(inicio.plus(6, ChronoUnit.HOURS)))
                .isInstanceOf(IllegalStateException.class);
    }


    private static final SectorId MANGA = new SectorId("manga");
    private static final SectorId BOCAGRANDE = new SectorId("bocagrande");

    private CorteAgua corteAbierto(OrigenCorte origen) {
        return corteAbierto(origen, MANGA);
    }

    private CorteAgua corteAbierto(OrigenCorte origen, SectorId... sectores) {
        return CorteAgua.builder()
                .id(new CorteId("corte-1"))
                .sectoresAfectados(List.of(sectores))
                .inicio(inicio)
                .finPrometido(inicio.plus(6, ChronoUnit.HOURS))
                .causa("Mantenimiento")
                .origen(origen)
                .build();
    }

    private Instant a(long horasTrasElInicio) {
        return inicio.plus(horasTrasElInicio, ChronoUnit.HOURS);
    }

    private static CierreDeCorte cierre(Instant hora, OrigenEstado fuente, boolean provisional) {
        return new CierreDeCorte(hora, fuente, provisional);
    }

    @Test
    void unCorteDeIngestaDejaDeSostenerElEstadoAlVencerSuVentana() {
        CorteAgua deIngesta = corteAbierto(OrigenCorte.INGESTA_IA);

        assertThat(deIngesta.sostieneElEstadoEn(MANGA, a(5))).isTrue();
        assertThat(deIngesta.sostieneElEstadoEn(MANGA, a(6).plusSeconds(1))).isFalse();
    }

    /** El corte del veedor es la señal autorizada: sigue abierto hasta que alguien lo cierre. */
    @Test
    void unCorteDelVeedorSostieneElEstadoAunqueSuVentanaHayaVencido() {
        CorteAgua delVeedor = corteAbierto(OrigenCorte.VEEDOR);

        assertThat(delVeedor.sostieneElEstadoEn(MANGA, a(30))).isTrue();
    }

    @Test
    void unCorteCerradoNuncaSostieneElEstado() {
        CorteAgua cerrado = corteAbierto(OrigenCorte.VEEDOR).cerrar(a(4));

        assertThat(cerrado.sostieneElEstadoEn(MANGA, a(5))).isFalse();
    }

    @Nested
    class CierrePorSector {

        private final CorteAgua dosSectores = corteAbierto(OrigenCorte.VEEDOR, MANGA, BOCAGRANDE);

        @Test
        void cerrarUnSectorDejaElCorteAbiertoParaLosDemas() {
            CorteAgua parcial = dosSectores.cerrarSector(MANGA, cierre(a(3), OrigenEstado.VEEDOR, false));

            assertThat(parcial.estado()).isEqualTo(EstadoCorte.ANUNCIADO);
            assertThat(parcial.ventana().estaCerrada()).isFalse();
            assertThat(parcial.cierreDe(MANGA)).contains(cierre(a(3), OrigenEstado.VEEDOR, false));
            assertThat(parcial.cierreDe(BOCAGRANDE)).isEmpty();
            assertThat(parcial.sostieneElEstadoEn(MANGA, a(4))).isFalse();
            assertThat(parcial.sostieneElEstadoEn(BOCAGRANDE, a(4))).isTrue();
        }

        @Test
        void alCerrarElUltimoSectorElCorteQuedaRestablecidoConLaHoraDelUltimoCierre() {
            CorteAgua cerrado = dosSectores
                    .cerrarSector(MANGA, cierre(a(3), OrigenEstado.VEEDOR, false))
                    .cerrarSector(BOCAGRANDE, cierre(a(5), OrigenEstado.VECINOS, true));

            assertThat(cerrado.estado()).isEqualTo(EstadoCorte.RESTABLECIDO);
            assertThat(cerrado.ventana().finReal()).isEqualTo(a(5));
        }

        @Test
        void unCierreNoPuedeSerAnteriorAlInicioDelCorte() {
            assertThatThrownBy(() -> dosSectores.cerrarSector(MANGA, cierre(inicio.minusSeconds(1), OrigenEstado.VEEDOR, false)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void nosePuedeCerrarUnSectorQueElCorteNoAfecta() {
            assertThatThrownBy(() -> dosSectores.cerrarSector(new SectorId("crespo"), cierre(a(3), OrigenEstado.VEEDOR, false)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void nosePuedeCerrarDosVecesElMismoSector() {
            CorteAgua parcial = dosSectores.cerrarSector(MANGA, cierre(a(3), OrigenEstado.VEEDOR, false));

            assertThatThrownBy(() -> parcial.cerrarSector(MANGA, cierre(a(4), OrigenEstado.VEEDOR, false)))
                    .isInstanceOf(IllegalStateException.class);
        }

        /** El atajo del veedor («cerrar todo») respeta lo que ya estaba cerrado y cierra solo los pendientes. */
        @Test
        void cerrarTodoCierraSoloLosSectoresPendientes() {
            CorteAgua parcial = dosSectores.cerrarSector(MANGA, cierre(a(3), OrigenEstado.VECINOS, true));

            CorteAgua cerrado = parcial.cerrar(a(5));

            assertThat(cerrado.estado()).isEqualTo(EstadoCorte.RESTABLECIDO);
            assertThat(cerrado.cierreDe(MANGA)).contains(cierre(a(3), OrigenEstado.VECINOS, true));
            assertThat(cerrado.cierreDe(BOCAGRANDE)).contains(cierre(a(5), OrigenEstado.VEEDOR, false));
            assertThat(cerrado.ventana().finReal()).isEqualTo(a(5));
        }
    }

    @Nested
    class ConfirmacionDeCierres {

        private final CorteAgua cerradoPorLosVecinos = corteAbierto(OrigenCorte.VEEDOR, MANGA, BOCAGRANDE)
                .cerrarSector(MANGA, cierre(a(3), OrigenEstado.VECINOS, true));

        /** El veedor confirma o corrige la hora de un cierre que solo sostenían los vecinos. */
        @Test
        void elVeedorPuedeConfirmarYCorregirUnCierreProvisional() {
            CorteAgua confirmado = cerradoPorLosVecinos.confirmarCierre(MANGA, cierre(a(2), OrigenEstado.VEEDOR, false));

            assertThat(confirmado.cierreDe(MANGA)).contains(cierre(a(2), OrigenEstado.VEEDOR, false));
            assertThat(confirmado.estado()).isEqualTo(EstadoCorte.ANUNCIADO);
        }

        @Test
        void confirmarElUltimoCierreProvisionalCambiaLaHoraRealDelCorte() {
            CorteAgua cerrado = cerradoPorLosVecinos.cerrarSector(BOCAGRANDE, cierre(a(5), OrigenEstado.VECINOS, true));

            CorteAgua confirmado = cerrado.confirmarCierre(BOCAGRANDE, cierre(a(4), OrigenEstado.VEEDOR, false));

            assertThat(confirmado.ventana().finReal()).isEqualTo(a(4));
        }

        @Test
        void unCierreYaConfirmadoNoSeVuelveAConfirmar() {
            CorteAgua confirmado = cerradoPorLosVecinos.confirmarCierre(MANGA, cierre(a(2), OrigenEstado.VEEDOR, false));

            assertThatThrownBy(() -> confirmado.confirmarCierre(MANGA, cierre(a(1), OrigenEstado.VEEDOR, false)))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void nosePuedeConfirmarUnSectorSinCierre() {
            assertThatThrownBy(() -> cerradoPorLosVecinos.confirmarCierre(BOCAGRANDE, cierre(a(2), OrigenEstado.VEEDOR, false)))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void laConfirmacionNoPuedeSerProvisionalNiAnteriorAlInicio() {
            assertThatThrownBy(() -> cerradoPorLosVecinos.confirmarCierre(MANGA, cierre(a(2), OrigenEstado.VECINOS, true)))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> cerradoPorLosVecinos.confirmarCierre(MANGA,
                    cierre(inicio.minusSeconds(1), OrigenEstado.VEEDOR, false)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /** Un restablecimiento que solo sostenían los vecinos puede no ser efectivo: el mismo corte se reabre. */
    @Nested
    class Reapertura {

        private final CorteAgua cerradoPorLosVecinos = corteAbierto(OrigenCorte.VEEDOR, MANGA)
                .cerrarSector(MANGA, cierre(a(3), OrigenEstado.VECINOS, true));

        @Test
        void unCierreProvisionalSePuedeReabrirYElCorteVuelveAEstarAbierto() {
            assertThat(cerradoPorLosVecinos.estado()).isEqualTo(EstadoCorte.RESTABLECIDO);

            CorteAgua reabierto = cerradoPorLosVecinos.reabrirSector(MANGA);

            assertThat(reabierto.estado()).isEqualTo(EstadoCorte.ANUNCIADO);
            assertThat(reabierto.cierreDe(MANGA)).isEmpty();
            assertThat(reabierto.ventana().estaCerrada()).isFalse();
            assertThat(reabierto.sostieneElEstadoEn(MANGA, a(4))).isTrue();
        }

        @Test
        void reabrirUnBarrioNoAfectaALosDemasCierres() {
            CorteAgua dosCerrados = corteAbierto(OrigenCorte.VEEDOR, MANGA, BOCAGRANDE)
                    .cerrarSector(MANGA, cierre(a(3), OrigenEstado.VECINOS, true))
                    .cerrarSector(BOCAGRANDE, cierre(a(4), OrigenEstado.VEEDOR, false));

            CorteAgua reabierto = dosCerrados.reabrirSector(MANGA);

            assertThat(reabierto.cierreDe(BOCAGRANDE)).contains(cierre(a(4), OrigenEstado.VEEDOR, false));
            assertThat(reabierto.estado()).isEqualTo(EstadoCorte.ANUNCIADO);
        }

        /** Lo que el veedor o un boletín confirmaron es definitivo: los vecinos no lo reabren. */
        @Test
        void unCierreConfirmadoNoSeReabre() {
            CorteAgua confirmado = cerradoPorLosVecinos.confirmarCierre(MANGA, cierre(a(3), OrigenEstado.VEEDOR, false));

            assertThatThrownBy(() -> confirmado.reabrirSector(MANGA)).isInstanceOf(IllegalStateException.class);
        }

        @Test
        void nosePuedeReabrirUnBarrioSinCierreNiUnCorteAnuladoOExpirado() {
            assertThatThrownBy(() -> corteAbierto(OrigenCorte.VEEDOR).reabrirSector(MANGA))
                    .isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> cerradoPorLosVecinos.anular("Por error").reabrirSector(MANGA))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    class Expiracion {

        @Test
        void unCorteAbiertoPuedeExpirarYDejaDeSostenerElEstadoSinTenerHoraReal() {
            CorteAgua expirado = corteAbierto(OrigenCorte.VEEDOR).expirar();

            assertThat(expirado.estado()).isEqualTo(EstadoCorte.EXPIRADO);
            assertThat(expirado.ventana().estaCerrada()).isFalse();
            assertThat(expirado.sostieneElEstadoEn(MANGA, a(1))).isFalse();
        }

        @Test
        void alExpirarConservaLosCierresQueYaTenia() {
            CorteAgua expirado = corteAbierto(OrigenCorte.VEEDOR, MANGA, BOCAGRANDE)
                    .cerrarSector(MANGA, cierre(a(3), OrigenEstado.VEEDOR, false))
                    .expirar();

            assertThat(expirado.cierreDe(MANGA)).isPresent();
            assertThat(expirado.cierreDe(BOCAGRANDE)).isEmpty();
        }

        @Test
        void nosePuedeExpirarUnCorteYaCerrado() {
            CorteAgua cerrado = corteAbierto(OrigenCorte.VEEDOR).cerrar(a(4));

            assertThatThrownBy(cerrado::expirar).isInstanceOf(IllegalStateException.class);
        }

        @Test
        void unCorteExpiradoYaNoSePuedeCerrar() {
            CorteAgua expirado = corteAbierto(OrigenCorte.VEEDOR).expirar();

            assertThatThrownBy(() -> expirado.cerrar(a(4))).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> expirado.cerrarSector(MANGA, cierre(a(4), OrigenEstado.VEEDOR, false)))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    class Anulacion {

        @Test
        void anularExigeUnMotivoYLoConserva() {
            CorteAgua anulado = corteAbierto(OrigenCorte.INGESTA_IA).anular("El boletín hablaba de otro barrio");

            assertThat(anulado.estado()).isEqualTo(EstadoCorte.ANULADO);
            assertThat(anulado.motivoAnulacion()).isEqualTo("El boletín hablaba de otro barrio");
            assertThat(anulado.sostieneElEstadoEn(MANGA, a(1))).isFalse();
        }

        @Test
        void anularSinMotivoSeRechaza() {
            assertThatThrownBy(() -> corteAbierto(OrigenCorte.INGESTA_IA).anular(" "))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        /** Un corte anulado no tiene duración real que entre al Índice, aunque ya estuviera cerrado. */
        @Test
        void anularUnCorteCerradoConservaSusCierresPeroYaNoTieneHoraReal() {
            CorteAgua anulado = corteAbierto(OrigenCorte.VEEDOR).cerrar(a(4)).anular("Registrado por error");

            assertThat(anulado.estado()).isEqualTo(EstadoCorte.ANULADO);
            assertThat(anulado.cierreDe(MANGA)).isPresent();
            assertThat(anulado.ventana().estaCerrada()).isFalse();
        }

        @Test
        void unCorteSoloSeAnulaUnaVez() {
            CorteAgua anulado = corteAbierto(OrigenCorte.VEEDOR).anular("Por error");

            assertThatThrownBy(() -> anulado.anular("Otra vez")).isInstanceOf(IllegalStateException.class);
        }

        @Test
        void unCorteAnuladoNoSePuedeCerrarNiExpirar() {
            CorteAgua anulado = corteAbierto(OrigenCorte.VEEDOR).anular("Por error");

            assertThatThrownBy(() -> anulado.cerrar(a(4))).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(anulado::expirar).isInstanceOf(IllegalStateException.class);
        }
    }

    /** El corte del veedor es el override: puede traer una hora tras la cual deja de afirmar nada. */
    @Nested
    class Caducidad {

        private CorteAgua.Builder base() {
            return CorteAgua.builder()
                    .id(new CorteId("corte-c"))
                    .sectoresAfectados(List.of(MANGA, BOCAGRANDE))
                    .inicio(inicio)
                    .finPrometido(a(6))
                    .causa("Mantenimiento")
                    .origen(OrigenCorte.VEEDOR);
        }

        @Test
        void sinCaducidadDeclaradaEsNula() {
            assertThat(base().build().caducaEn()).isNull();
        }

        @Test
        void conservaLaCaducidadDeclarada() {
            assertThat(base().caducaEn(a(4)).build().caducaEn()).isEqualTo(a(4));
        }

        @Test
        void unaCaducidadAnteriorAlInicioSeRechaza() {
            var builder = base().caducaEn(inicio.minusSeconds(1));

            assertThatThrownBy(builder::build).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void laCaducidadSobreviveACerrarExpirarYAnular() {
            CorteAgua corte = base().caducaEn(a(4)).build();

            assertThat(corte.cerrarSector(MANGA, new CierreDeCorte(a(2), OrigenEstado.VEEDOR, false)).caducaEn()).isEqualTo(a(4));
            assertThat(corte.expirar().caducaEn()).isEqualTo(a(4));
            assertThat(corte.anular("Por error").caducaEn()).isEqualTo(a(4));
        }
    }

    @Nested
    class CoherenciaAlConstruir {

        private CorteAgua.Builder base() {
            return CorteAgua.builder()
                    .id(new CorteId("corte-9"))
                    .sectoresAfectados(List.of(MANGA, BOCAGRANDE))
                    .inicio(inicio)
                    .finPrometido(a(6))
                    .causa("Mantenimiento")
                    .origen(OrigenCorte.VEEDOR);
        }

        /** Los cortes guardados con la versión anterior traen solo finReal: cuentan como cerrados en todos sus barrios. */
        @Test
        void unFinRealSinCierresCierraTodosLosBarriosConLaHoraDelVeedor() {
            CorteAgua corte = base().finReal(a(5)).estado(EstadoCorte.RESTABLECIDO).build();

            assertThat(corte.cierreDe(MANGA)).contains(cierre(a(5), OrigenEstado.VEEDOR, false));
            assertThat(corte.cierreDe(BOCAGRANDE)).contains(cierre(a(5), OrigenEstado.VEEDOR, false));
        }

        @Test
        void unCorteRestablecidoExigeQueTodosLosBarriosEstenCerrados() {
            var builder = base()
                    .cierres(Map.of(MANGA, cierre(a(3), OrigenEstado.VEEDOR, false)))
                    .estado(EstadoCorte.RESTABLECIDO);

            assertThatThrownBy(builder::build).isInstanceOf(IllegalStateException.class);
        }

        @Test
        void unCorteConTodosLosBarriosCerradosNoPuedeSeguirAbierto() {
            var builder = base()
                    .cierres(Map.of(
                            MANGA, cierre(a(3), OrigenEstado.VEEDOR, false),
                            BOCAGRANDE, cierre(a(4), OrigenEstado.VEEDOR, false)))
                    .estado(EstadoCorte.ANUNCIADO);

            assertThatThrownBy(builder::build).isInstanceOf(IllegalStateException.class);
        }

        @Test
        void unCierreDeUnBarrioAjenoSeRechaza() {
            var builder = base().cierres(Map.of(new SectorId("crespo"), cierre(a(3), OrigenEstado.VEEDOR, false)));

            assertThatThrownBy(builder::build).isInstanceOf(IllegalStateException.class);
        }

        @Test
        void elMotivoDeAnulacionSoloVaConElEstadoAnulado() {
            var sinAnular = base().motivoAnulacion("Sin estar anulado");
            var anuladoSinMotivo = base().estado(EstadoCorte.ANULADO);

            assertThatThrownBy(sinAnular::build).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(anuladoSinMotivo::build).isInstanceOf(IllegalStateException.class);
        }
    }
}
