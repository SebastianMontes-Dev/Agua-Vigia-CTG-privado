package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.CorreoElectronico;
import com.aguavigia.ctg.domain.EnlaceDeRestablecimiento;
import com.aguavigia.ctg.domain.EnlaceDeRestablecimientoInvalidoException;
import com.aguavigia.ctg.domain.EstadoSuscripcion;
import com.aguavigia.ctg.domain.HuellaDispositivo;
import com.aguavigia.ctg.domain.ReporteCiudadano;
import com.aguavigia.ctg.domain.ReporteId;
import com.aguavigia.ctg.domain.Reportante;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.Suscripcion;
import com.aguavigia.ctg.domain.SuscripcionId;
import com.aguavigia.ctg.domain.TipoReporte;
import com.aguavigia.ctg.domain.port.in.RegistrarReporteUseCase;
import com.aguavigia.ctg.domain.port.out.FirmaDeEnlacesPort;
import com.aguavigia.ctg.domain.port.out.SuscripcionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * El enlace de un toque «¿ya volvió el agua?» (D18): lo que más cuesta conseguir es que alguien confirme que el agua
 * volvió, porque se reporta el problema, no la solución. Un toque desde el correo lo vuelve un voto de restablecimiento.
 */
class ConfirmarRestablecimientoPorEnlaceServiceTest {

    private static final Instant AHORA = Instant.parse("2026-08-21T20:00:00Z");
    private static final SectorId MANGA = new SectorId("manga");
    private static final SuscripcionId SUSCRIPCION = new SuscripcionId("s-1");
    private static final String TOKEN = "token-firmado";

    private FirmaDeEnlacesPort firma;
    private SuscripcionRepository suscripciones;
    private RegistrarReporteUseCase registrarReporte;
    private ConfirmarRestablecimientoPorEnlaceService servicio;

    @BeforeEach
    void montar() {
        firma = mock(FirmaDeEnlacesPort.class);
        suscripciones = mock(SuscripcionRepository.class);
        registrarReporte = mock(RegistrarReporteUseCase.class);
        servicio = new ConfirmarRestablecimientoPorEnlaceService(firma, suscripciones, registrarReporte, () -> AHORA);

        given(firma.verificar(TOKEN)).willReturn(Optional.of(
                new EnlaceDeRestablecimiento(MANGA, SUSCRIPCION, AHORA.plus(Duration.ofHours(5)))));
        given(suscripciones.buscarPorId(SUSCRIPCION)).willReturn(Optional.of(suscripcion(EstadoSuscripcion.CONFIRMADA, MANGA)));
        given(registrarReporte.registrar(any(), any(), any(), any(), any(), any(), anyBoolean())).willReturn(
                new ReporteCiudadano(new ReporteId("r-1"), MANGA, TipoReporte.SERVICIO_RESTABLECIDO, null,
                        HuellaDispositivo.deSuscripcion(SUSCRIPCION), AHORA));
    }

    private static Suscripcion suscripcion(EstadoSuscripcion estado, SectorId... sectores) {
        return new Suscripcion(SUSCRIPCION, new CorreoElectronico("vecino@correo.com"), List.of(sectores), estado,
                "token-de-baja", AHORA.minus(Duration.ofDays(3)));
    }

    @Test
    void unToqueDebeRegistrarUnReporteDeServicioRestablecidoDeEsaSuscripcion() {
        ReporteCiudadano reporte = servicio.confirmar(MANGA, TOKEN, "10.0.0.7");

        assertThat(reporte.tipo()).isEqualTo(TipoReporte.SERVICIO_RESTABLECIDO);
        ArgumentCaptor<Reportante> reportante = ArgumentCaptor.forClass(Reportante.class);
        verify(registrarReporte).registrar(eq(MANGA), eq(TipoReporte.SERVICIO_RESTABLECIDO), isNull(), isNull(),
                reportante.capture(), eq("10.0.0.7"), eq(false));
        // Una suscripción es un votante: el voto es suyo y no de «un dispositivo cualquiera».
        assertThat(reportante.getValue().huella()).isEqualTo(HuellaDispositivo.deSuscripcion(SUSCRIPCION));
        assertThat(reportante.getValue().cuentaId()).isNull();
    }

    @Test
    void unaSuscripcionYUnDispositivoNoSonElMismoVotante() {
        assertThat(HuellaDispositivo.deSuscripcion(new SuscripcionId("abc")))
                .isNotEqualTo(HuellaDispositivo.deCuenta(new com.aguavigia.ctg.domain.UsuarioId("abc")))
                .isNotEqualTo(HuellaDispositivo.deDispositivo(new com.aguavigia.ctg.domain.DispositivoId("abc")));
    }

    @Test
    void unTokenQueNoFirmoEsteServidorNoSirve() {
        given(firma.verificar("inventado")).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.confirmar(MANGA, "inventado", "10.0.0.7"))
                .isInstanceOf(EnlaceDeRestablecimientoInvalidoException.class);
        verify(registrarReporte, never()).registrar(any(), any(), any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void unTokenVencidoNoSirve() {
        given(firma.verificar(TOKEN)).willReturn(Optional.of(
                new EnlaceDeRestablecimiento(MANGA, SUSCRIPCION, AHORA.minusSeconds(1))));

        assertThatThrownBy(() -> servicio.confirmar(MANGA, TOKEN, "10.0.0.7"))
                .isInstanceOf(EnlaceDeRestablecimientoInvalidoException.class);
    }

    /** El enlace es de un barrio: usarlo en la ruta de otro no debe votar allí. */
    @Test
    void unTokenDeOtroBarrioNoSirve() {
        assertThatThrownBy(() -> servicio.confirmar(new SectorId("bocagrande"), TOKEN, "10.0.0.7"))
                .isInstanceOf(EnlaceDeRestablecimientoInvalidoException.class);
        verify(registrarReporte, never()).registrar(any(), any(), any(), any(), any(), any(), anyBoolean());
    }

    /** Quien se dio de baja no debe seguir pudiendo votar con un correo viejo (RF015). */
    @Test
    void unaSuscripcionCanceladaNoPuedeVotar() {
        given(suscripciones.buscarPorId(SUSCRIPCION)).willReturn(Optional.of(suscripcion(EstadoSuscripcion.CANCELADA, MANGA)));

        assertThatThrownBy(() -> servicio.confirmar(MANGA, TOKEN, "10.0.0.7"))
                .isInstanceOf(EnlaceDeRestablecimientoInvalidoException.class);
    }

    @Test
    void unaSuscripcionSinConfirmarNoPuedeVotar() {
        given(suscripciones.buscarPorId(SUSCRIPCION))
                .willReturn(Optional.of(suscripcion(EstadoSuscripcion.PENDIENTE_CONFIRMACION, MANGA)));

        assertThatThrownBy(() -> servicio.confirmar(MANGA, TOKEN, "10.0.0.7"))
                .isInstanceOf(EnlaceDeRestablecimientoInvalidoException.class);
    }

    @Test
    void unaSuscripcionQueNoSigueEseBarrioNoPuedeVotarEnEl() {
        given(suscripciones.buscarPorId(SUSCRIPCION))
                .willReturn(Optional.of(suscripcion(EstadoSuscripcion.CONFIRMADA, new SectorId("bocagrande"))));

        assertThatThrownBy(() -> servicio.confirmar(MANGA, TOKEN, "10.0.0.7"))
                .isInstanceOf(EnlaceDeRestablecimientoInvalidoException.class);
    }

    @Test
    void unaSuscripcionQueYaNoExisteNoPuedeVotar() {
        given(suscripciones.buscarPorId(SUSCRIPCION)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.confirmar(MANGA, TOKEN, "10.0.0.7"))
                .isInstanceOf(EnlaceDeRestablecimientoInvalidoException.class);
    }
}
