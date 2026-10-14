package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.Coordenada;
import com.aguavigia.ctg.domain.EstadoServicio;
import com.aguavigia.ctg.domain.HuellaDispositivo;
import com.aguavigia.ctg.domain.NivelDeVerificacion;
import com.aguavigia.ctg.domain.ReporteCiudadano;
import com.aguavigia.ctg.domain.Reportante;
import com.aguavigia.ctg.domain.ResultadoConsenso;
import com.aguavigia.ctg.domain.Sector;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.TipoReporte;
import com.aguavigia.ctg.domain.UsuarioId;
import com.aguavigia.ctg.domain.port.in.EvaluarConsensoUseCase;
import com.aguavigia.ctg.domain.port.out.ContadorReportesPort;
import com.aguavigia.ctg.domain.port.out.HashDeRedPort;
import com.aguavigia.ctg.domain.port.out.ReporteCiudadanoRepository;
import com.aguavigia.ctg.domain.port.out.SectorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * D16: cuánto respalda un reporte que quien lo envía está en el barrio, y de qué red salió. La identidad ya
 * viene resuelta por el servidor (ver IdentificarReportanteService); aquí se decide el nivel de verificación.
 */
class RegistrarReporteServiceIdentidadTest {

    // 2026-10-02 03:00 UTC son las 22:00 del 1 de octubre en Cartagena (UTC-5): el día de la sal es el de allí.
    private static final Instant AHORA = Instant.parse("2026-10-02T03:00:00Z");
    private static final SectorId MANGA = new SectorId("manga");
    private static final SectorId CRESPO = new SectorId("crespo");
    private static final Coordenada EN_MANGA = new Coordenada(10.41234567, -75.54321098);
    private static final Reportante ANONIMO = Reportante.anonimo(new HuellaDispositivo("disp-1"));

    private SectorRepository sectores;
    private ReporteCiudadanoRepository reportes;
    private ContadorReportesPort contadorReportes;
    private HashDeRedPort hashDeRed;
    private RegistrarReporteService servicio;

    @BeforeEach
    void montar() {
        sectores = mock(SectorRepository.class);
        reportes = mock(ReporteCiudadanoRepository.class);
        contadorReportes = mock(ContadorReportesPort.class);
        hashDeRed = mock(HashDeRedPort.class);
        EvaluarConsensoUseCase evaluarConsenso = mock(EvaluarConsensoUseCase.class);
        servicio = new RegistrarReporteService(sectores, reportes, contadorReportes, evaluarConsenso, () -> AHORA,
                hashDeRed, new LimitesDeReporte(3, 30, 5, java.time.Duration.ofMinutes(30)), 200.0);

        given(reportes.guardar(any(ReporteCiudadano.class))).willAnswer(i -> i.getArgument(0));
        given(reportes.contarRecientesPorSectorYDispositivo(any(), any(), any())).willReturn(0L);
        given(contadorReportes.intentarReservarCupo(any(), any(), anyInt(), any())).willReturn(true);
        given(evaluarConsenso.evaluar(any())).willReturn(new ResultadoConsenso(MANGA, false, null, List.of()));
        given(sectores.buscarPorId(MANGA)).willReturn(Optional.of(new Sector(MANGA, "Manga", 10_000, null)));
        given(sectores.buscarPorId(CRESPO)).willReturn(Optional.of(new Sector(CRESPO, "Crespo", 5_000, null)));
        given(sectores.buscarPorCoordenada(EN_MANGA)).willReturn(Optional.of(new Sector(MANGA, "Manga", 10_000, EstadoServicio.SIN_SERVICIO)));
        given(hashDeRed.hashear(anyString(), any())).willReturn("red-hash");
    }

    private ReporteCiudadano registrar(SectorId sector, Coordenada coordenada, Double precision, Reportante reportante,
                                       String ip) {
        return servicio.registrar(sector, TipoReporte.SIN_AGUA, coordenada, precision, reportante, ip, false);
    }

    // --- nivel de verificación ---

    @Test
    void unVecinoConElBarrioVerificadoQueReportaEseBarrioDebeQuedarConCuentaVerificada() {
        Reportante vecino = new Reportante(HuellaDispositivo.deCuenta(new UsuarioId("v-1")), new UsuarioId("v-1"), MANGA);

        assertThat(registrar(MANGA, null, null, vecino, "1.1.1.1").verificacion())
                .isEqualTo(NivelDeVerificacion.CUENTA_VERIFICADA);
    }

    /** Su verificación vale para su barrio: reportar otro no se la hereda. */
    @Test
    void unVecinoQueReportaOtroBarrioNoDebeHeredarSuVerificacion() {
        Reportante vecino = new Reportante(HuellaDispositivo.deCuenta(new UsuarioId("v-1")), new UsuarioId("v-1"), MANGA);

        assertThat(registrar(CRESPO, null, null, vecino, "1.1.1.1").verificacion())
                .isEqualTo(NivelDeVerificacion.NINGUNA);
    }

    @Test
    void unVecinoSinBarrioVerificadoSinUbicacionDebeQuedarSinVerificacion() {
        Reportante vecino = new Reportante(HuellaDispositivo.deCuenta(new UsuarioId("v-1")), new UsuarioId("v-1"), null);

        assertThat(registrar(MANGA, null, null, vecino, "1.1.1.1").verificacion())
                .isEqualTo(NivelDeVerificacion.NINGUNA);
    }

    @Test
    void unAnonimoConUbicacionPrecisaDentroDelBarrioDeclaradoDebeQuedarConUbicacionVerificada() {
        assertThat(registrar(MANGA, EN_MANGA, 25.0, ANONIMO, "1.1.1.1").verificacion())
                .isEqualTo(NivelDeVerificacion.UBICACION_VERIFICADA);
    }

    @Test
    void unaUbicacionPrecisaFueraDelBarrioDeclaradoNoDebeVerificar() {
        given(sectores.buscarPorCoordenada(EN_MANGA))
                .willReturn(Optional.of(new Sector(CRESPO, "Crespo", 5_000, null)));

        assertThat(registrar(MANGA, EN_MANGA, 25.0, ANONIMO, "1.1.1.1").verificacion())
                .isEqualTo(NivelDeVerificacion.NINGUNA);
    }

    @Test
    void unaUbicacionImprecisaNoDebeVerificarNiConsultarLaGeometria() {
        assertThat(registrar(MANGA, EN_MANGA, 200.1, ANONIMO, "1.1.1.1").verificacion())
                .isEqualTo(NivelDeVerificacion.NINGUNA);

        verify(sectores, never()).buscarPorCoordenada(any());
    }

    @Test
    void unaUbicacionConExactamenteLaPrecisionMaximaSiVerifica() {
        assertThat(registrar(MANGA, EN_MANGA, 200.0, ANONIMO, "1.1.1.1").verificacion())
                .isEqualTo(NivelDeVerificacion.UBICACION_VERIFICADA);
    }

    @Test
    void unaUbicacionSinPrecisionNoDebeVerificar() {
        assertThat(registrar(MANGA, EN_MANGA, null, ANONIMO, "1.1.1.1").verificacion())
                .isEqualTo(NivelDeVerificacion.NINGUNA);
    }

    @Test
    void unaPrecisionNegativaNoDebeVerificar() {
        assertThat(registrar(MANGA, EN_MANGA, -5.0, ANONIMO, "1.1.1.1").verificacion())
                .isEqualTo(NivelDeVerificacion.NINGUNA);
    }

    @Test
    void sinUbicacionNiCuentaUnAnonimoNoTieneVerificacion() {
        assertThat(registrar(MANGA, null, null, ANONIMO, "1.1.1.1").verificacion())
                .isEqualTo(NivelDeVerificacion.NINGUNA);
    }

    /** Con solo la coordenada el barrio se infiere de ella: ya se consultó, no se vuelve a consultar. */
    @Test
    void unSectorInferidoDeUnaUbicacionPrecisaDebeVerificarConUnaSolaConsultaGeografica() {
        ReporteCiudadano reporte = registrar(null, EN_MANGA, 25.0, ANONIMO, "1.1.1.1");

        assertThat(reporte.sectorId()).isEqualTo(MANGA);
        assertThat(reporte.verificacion()).isEqualTo(NivelDeVerificacion.UBICACION_VERIFICADA);
        verify(sectores, times(1)).buscarPorCoordenada(EN_MANGA);
    }

    @Test
    void unSectorInferidoDeUnaUbicacionImpreciseSeUsaPeroNoVerifica() {
        ReporteCiudadano reporte = registrar(null, EN_MANGA, 900.0, ANONIMO, "1.1.1.1");

        assertThat(reporte.sectorId()).isEqualTo(MANGA);
        assertThat(reporte.verificacion()).isEqualTo(NivelDeVerificacion.NINGUNA);
    }

    @Test
    void laCuentaVerificadaDebeGanarSobreLaUbicacion() {
        Reportante vecino = new Reportante(HuellaDispositivo.deCuenta(new UsuarioId("v-1")), new UsuarioId("v-1"), MANGA);

        assertThat(registrar(MANGA, EN_MANGA, 25.0, vecino, "1.1.1.1").verificacion())
                .isEqualTo(NivelDeVerificacion.CUENTA_VERIFICADA);
    }

    // --- red ---

    @Test
    void debeGuardarElResumenDeLaRedConElDiaDeCartagena() {
        given(hashDeRed.hashear("190.20.30.40", LocalDate.of(2026, 10, 1))).willReturn("red-del-1-de-octubre");

        ReporteCiudadano reporte = registrar(MANGA, null, null, ANONIMO, "190.20.30.40");

        assertThat(reporte.redHash()).isEqualTo("red-del-1-de-octubre");
    }

    @Test
    void sinIpNoDebeGuardarRed() {
        ReporteCiudadano reporte = registrar(MANGA, null, null, ANONIMO, null);

        assertThat(reporte.redHash()).isNull();
        verify(hashDeRed, never()).hashear(any(), any());
    }

    // --- coordenada ---

    /** D8: la coordenada exacta se usa para verificar y se descarta; lo guardado es una aproximación de ~110 m. */
    @Test
    void debeGuardarLaCoordenadaRedondeadaATresDecimalesYVerificarConLaExacta() {
        ReporteCiudadano reporte = registrar(MANGA, EN_MANGA, 25.0, ANONIMO, "1.1.1.1");

        assertThat(reporte.coordenada()).isEqualTo(new Coordenada(10.412, -75.543));
        verify(sectores).buscarPorCoordenada(EN_MANGA);
    }

    @Test
    void unReporteDeSensorDebeConservarSuCoordenadaTalCual() {
        servicio.registrar(MANGA, TipoReporte.PRESION_BAJA, EN_MANGA, null,
                Reportante.anonimo(HuellaDispositivo.deSensor("s-1")), null, true);

        ArgumentCaptorHelper.guardado(reportes, reporte -> {
            assertThat(reporte.coordenada()).isEqualTo(EN_MANGA);
            assertThat(reporte.redHash()).isNull();
        });
    }

    // --- cupo e identidad ---

    @Test
    void debeUsarLaHuellaDelReportanteParaElCupoYElContador() {
        registrar(MANGA, null, null, ANONIMO, "1.1.1.1");

        verify(contadorReportes).intentarReservarCupo(eq(MANGA), eq(ANONIMO.huella()), eq(3), any(Duration.class));
        verify(contadorReportes).registrar(MANGA, ANONIMO.huella());
    }

    /** Plan §2: un vecino registrado reporta hasta 5 veces por sector en la ventana; un anónimo, 3. */
    @Test
    void unVecinoDebeTenerUnCupoDeCinco() {
        Reportante vecino = new Reportante(HuellaDispositivo.deCuenta(new UsuarioId("v-1")), new UsuarioId("v-1"), null);

        registrar(MANGA, null, null, vecino, "1.1.1.1");

        verify(contadorReportes).intentarReservarCupo(eq(MANGA), eq(vecino.huella()), eq(5), any(Duration.class));
    }

    @Test
    void unVecinoConCuatroReportesYaHechosTodaviaPuedeReportar() {
        Reportante vecino = new Reportante(HuellaDispositivo.deCuenta(new UsuarioId("v-1")), new UsuarioId("v-1"), null);
        given(reportes.contarRecientesPorSectorYDispositivo(any(), any(), eq(vecino.huella()))).willReturn(4L);

        assertThat(registrar(MANGA, null, null, vecino, "1.1.1.1")).isNotNull();
    }

    @Test
    void unVecinoConCincoReportesYaHechosDebeRechazarse() {
        Reportante vecino = new Reportante(HuellaDispositivo.deCuenta(new UsuarioId("v-1")), new UsuarioId("v-1"), null);
        given(reportes.contarRecientesPorSectorYDispositivo(any(), any(), eq(vecino.huella()))).willReturn(5L);

        assertThatThrownBy(() -> registrar(MANGA, null, null, vecino, "1.1.1.1"))
                .isInstanceOf(com.aguavigia.ctg.domain.LimiteReportesExcedidoException.class);
    }

    /** Una clase auxiliar mínima para inspeccionar lo guardado sin repetir el ArgumentCaptor en cada prueba. */
    private static final class ArgumentCaptorHelper {
        static void guardado(ReporteCiudadanoRepository reportes, java.util.function.Consumer<ReporteCiudadano> comprobacion) {
            org.mockito.ArgumentCaptor<ReporteCiudadano> captor = org.mockito.ArgumentCaptor.forClass(ReporteCiudadano.class);
            verify(reportes).guardar(captor.capture());
            comprobacion.accept(captor.getValue());
        }
    }
}
