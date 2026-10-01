package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.ClaveHash;
import com.aguavigia.ctg.domain.Consentimiento;
import com.aguavigia.ctg.domain.CorreoElectronico;
import com.aguavigia.ctg.domain.Dispositivo;
import com.aguavigia.ctg.domain.DispositivoId;
import com.aguavigia.ctg.domain.DispositivoInvalidoException;
import com.aguavigia.ctg.domain.HuellaDispositivo;
import com.aguavigia.ctg.domain.PermisosEfectivos;
import com.aguavigia.ctg.domain.Reportante;
import com.aguavigia.ctg.domain.RolVeedor;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.TipoConsentimiento;
import com.aguavigia.ctg.domain.Usuario;
import com.aguavigia.ctg.domain.UsuarioId;
import com.aguavigia.ctg.domain.EstadoCuenta;
import com.aguavigia.ctg.domain.port.out.DispositivoRepository;
import com.aguavigia.ctg.domain.port.out.FirmaDeDispositivosPort;
import com.aguavigia.ctg.domain.port.out.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class IdentificarReportanteServiceTest {

    private static final Instant ANTES = Instant.parse("2026-10-01T15:00:00Z");
    private static final Instant AHORA = Instant.parse("2026-10-02T09:00:00Z");
    private static final ClaveHash HASH =
            new ClaveHash("$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy");
    private static final DispositivoId DISPOSITIVO = new DispositivoId("d-1");
    private static final UsuarioId CUENTA = new UsuarioId("v-1");
    private static final SectorId MANGA = new SectorId("manga");

    private DispositivoRepository dispositivos;
    private FirmaDeDispositivosPort firma;
    private UsuarioRepository usuarios;
    private IdentificarReportanteService servicio;

    @BeforeEach
    void montar() {
        dispositivos = mock(DispositivoRepository.class);
        firma = mock(FirmaDeDispositivosPort.class);
        usuarios = mock(UsuarioRepository.class);
        servicio = new IdentificarReportanteService(dispositivos, firma, usuarios, () -> AHORA);
    }

    private void tokenValido() {
        given(firma.verificar("token-bueno")).willReturn(Optional.of(DISPOSITIVO));
        given(dispositivos.buscarPorId(DISPOSITIVO)).willReturn(Optional.of(new Dispositivo(DISPOSITIVO, ANTES, ANTES)));
    }

    private static Usuario vecino(boolean barrioVerificado) {
        Usuario base = Usuario.registradoComoVecino(CUENTA, new CorreoElectronico("vecina@ejemplo.org"), "Vecina", HASH,
                MANGA, List.of(new Consentimiento(TipoConsentimiento.PRIVACIDAD, "v1", ANTES)), ANTES)
                .verificarCorreo(ANTES);
        return barrioVerificado ? base.verificarBarrio(ANTES) : base;
    }

    // --- por token de dispositivo ---

    @Test
    void unTokenValidoDebeIdentificarAlDispositivoPorSuHuella() {
        tokenValido();

        Reportante reportante = servicio.identificar("token-bueno", null);

        assertThat(reportante.huella()).isEqualTo(HuellaDispositivo.deDispositivo(DISPOSITIVO));
        assertThat(reportante.cuentaId()).isNull();
        assertThat(reportante.barrioVerificado()).isNull();
    }

    @Test
    void debeRegistrarElUsoDelDispositivo() {
        tokenValido();

        servicio.identificar("token-bueno", null);

        verify(dispositivos).registrarVisto(DISPOSITIVO, AHORA);
    }

    @Test
    void sinTokenNiCuentaDebeRechazarse() {
        assertThatThrownBy(() -> servicio.identificar(null, null)).isInstanceOf(DispositivoInvalidoException.class);
        assertThatThrownBy(() -> servicio.identificar("  ", null)).isInstanceOf(DispositivoInvalidoException.class);
    }

    @Test
    void unTokenQueNoFirmoEsteServidorDebeRechazarseSinTocarLaBase() {
        given(firma.verificar("falso")).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.identificar("falso", null)).isInstanceOf(DispositivoInvalidoException.class);

        verifyNoInteractions(dispositivos);
    }

    /** Un dispositivo que venció tras 12 meses sin uso ya no existe: su token firmado no basta. */
    @Test
    void unTokenBienFirmadoDeUnDispositivoQueYaNoExisteDebeRechazarse() {
        given(firma.verificar("token-bueno")).willReturn(Optional.of(DISPOSITIVO));
        given(dispositivos.buscarPorId(DISPOSITIVO)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.identificar("token-bueno", null))
                .isInstanceOf(DispositivoInvalidoException.class);
        verify(dispositivos, never()).registrarVisto(any(), any());
    }

    // --- por cuenta de vecino ---

    @Test
    void unVecinoActivoDebeIdentificarseSinTokenYConSuBarrioVerificado() {
        given(usuarios.buscarPorId(CUENTA)).willReturn(Optional.of(vecino(true)));

        Reportante reportante = servicio.identificar(null, CUENTA);

        assertThat(reportante.huella()).isEqualTo(HuellaDispositivo.deCuenta(CUENTA));
        assertThat(reportante.cuentaId()).isEqualTo(CUENTA);
        assertThat(reportante.barrioVerificado()).isEqualTo(MANGA);
        verifyNoInteractions(dispositivos, firma);
    }

    @Test
    void unVecinoSinBarrioVerificadoNoDebeLlevarBarrioVerificado() {
        given(usuarios.buscarPorId(CUENTA)).willReturn(Optional.of(vecino(false)));

        Reportante reportante = servicio.identificar(null, CUENTA);

        assertThat(reportante.cuentaId()).isEqualTo(CUENTA);
        assertThat(reportante.barrioVerificado()).isNull();
    }

    /** Con sesión y token a la vez, vota como la cuenta: si no, un vecino podría votar dos veces con la misma identidad. */
    @Test
    void laCuentaDebeMandarSobreElToken() {
        tokenValido();
        given(usuarios.buscarPorId(CUENTA)).willReturn(Optional.of(vecino(false)));

        Reportante reportante = servicio.identificar("token-bueno", CUENTA);

        assertThat(reportante.huella()).isEqualTo(HuellaDispositivo.deCuenta(CUENTA));
    }

    @Test
    void unaCuentaSuspendidaNoDebeReportarComoVecinoYCaeAlToken() {
        tokenValido();
        given(usuarios.buscarPorId(CUENTA)).willReturn(Optional.of(vecino(true).suspender(AHORA)));

        Reportante reportante = servicio.identificar("token-bueno", CUENTA);

        assertThat(reportante.huella()).isEqualTo(HuellaDispositivo.deDispositivo(DISPOSITIVO));
        assertThat(reportante.cuentaId()).isNull();
    }

    @Test
    void unaCuentaSuspendidaSinTokenDebeRechazarse() {
        given(usuarios.buscarPorId(CUENTA)).willReturn(Optional.of(vecino(true).suspender(AHORA)));

        assertThatThrownBy(() -> servicio.identificar(null, CUENTA)).isInstanceOf(DispositivoInvalidoException.class);
    }

    @Test
    void unaCuentaQueNoEsDeVecinoNoDebeReportarComoVecino() {
        given(usuarios.buscarPorId(CUENTA)).willReturn(Optional.of(new Usuario(CUENTA,
                new CorreoElectronico("admin@ejemplo.org"), "Admin", HASH, EstadoCuenta.ACTIVA,
                PermisosEfectivos.deRol(RolVeedor.ADMIN), null, ANTES, ANTES)));

        assertThatThrownBy(() -> servicio.identificar(null, CUENTA)).isInstanceOf(DispositivoInvalidoException.class);
    }

    @Test
    void unaCuentaInexistenteSinTokenDebeRechazarse() {
        given(usuarios.buscarPorId(CUENTA)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.identificar(null, CUENTA)).isInstanceOf(DispositivoInvalidoException.class);
    }
}
