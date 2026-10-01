package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.AccionAuditada;
import com.aguavigia.ctg.domain.ClaveEnClaro;
import com.aguavigia.ctg.domain.ClaveHash;
import com.aguavigia.ctg.domain.ConsentimientosAceptados;
import com.aguavigia.ctg.domain.ContextoDeAccion;
import com.aguavigia.ctg.domain.CorreoElectronico;
import com.aguavigia.ctg.domain.CorreoYaRegistradoException;
import com.aguavigia.ctg.domain.EstadoCuenta;
import com.aguavigia.ctg.domain.PermisosEfectivos;
import com.aguavigia.ctg.domain.RolVeedor;
import com.aguavigia.ctg.domain.Sector;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.TipoConsentimiento;
import com.aguavigia.ctg.domain.TipoTokenCuenta;
import com.aguavigia.ctg.domain.Usuario;
import com.aguavigia.ctg.domain.UsuarioId;
import com.aguavigia.ctg.domain.port.out.CifradorClavePort;
import com.aguavigia.ctg.domain.port.out.NotificacionCuentaPort;
import com.aguavigia.ctg.domain.port.out.SectorRepository;
import com.aguavigia.ctg.domain.port.out.TiempoConstantePort;
import com.aguavigia.ctg.domain.port.out.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * El registro de vecinos es público y cada llamada puede acabar en un correo: debe terminar igual
 * exista o no la cuenta (RNF024), como el registro del panel.
 */
class RegistrarVecinoServiceTest {

    private static final Instant AHORA = Instant.parse("2026-10-01T15:00:00Z");
    private static final ClaveHash HASH =
            new ClaveHash("$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy");
    private static final CorreoElectronico CORREO = new CorreoElectronico("vecina@ejemplo.org");
    private static final ClaveEnClaro CLAVE = new ClaveEnClaro("clave-larga-y-variada");
    private static final SectorId MANGA = new SectorId("manga");
    private static final ConsentimientosAceptados SOLO_PRIVACIDAD = new ConsentimientosAceptados(true, false);
    private static final ContextoDeAccion CONTEXTO = ContextoDeAccion.anonimo("10.0.0.1");

    private UsuarioRepository usuarios;
    private CifradorClavePort cifrador;
    private EmisorDeTokensDeCuenta emisorDeTokens;
    private NotificacionCuentaPort notificaciones;
    private RegistroDeAuditoria auditoria;
    private SectorRepository sectores;
    private TiempoConstantePort tiempoConstante;

    @BeforeEach
    void montar() {
        usuarios = mock(UsuarioRepository.class);
        cifrador = mock(CifradorClavePort.class);
        emisorDeTokens = mock(EmisorDeTokensDeCuenta.class);
        notificaciones = mock(NotificacionCuentaPort.class);
        auditoria = mock(RegistroDeAuditoria.class);
        sectores = mock(SectorRepository.class);
        tiempoConstante = mock(TiempoConstantePort.class);
        doAnswer(invocacion -> {
            ((Runnable) invocacion.getArgument(0)).run();
            return null;
        }).when(tiempoConstante).ejecutar(any());

        given(usuarios.guardar(any())).willAnswer(invocacion -> invocacion.getArgument(0));
        given(cifrador.cifrar(anyString())).willReturn(HASH);
        given(emisorDeTokens.emitir(any(), any())).willReturn("token-en-claro");
        given(sectores.buscarPorId(MANGA)).willReturn(Optional.of(new Sector(MANGA, "Manga", 1000, null)));
        given(usuarios.buscarPorCorreo(any())).willReturn(Optional.empty());
    }

    private RegistrarVecinoService registro() {
        return new RegistrarVecinoService(usuarios, cifrador, emisorDeTokens, notificaciones, auditoria,
                () -> AHORA, sectores, tiempoConstante, "2026-10-v1");
    }

    private Usuario registrarYCapturar(ConsentimientosAceptados consentimientos) {
        registro().registrar(CORREO, "Vecina", CLAVE, MANGA, consentimientos, CONTEXTO);
        ArgumentCaptor<Usuario> guardado = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios).guardar(guardado.capture());
        return guardado.getValue();
    }

    private static Usuario cuentaExistente() {
        return new Usuario(new UsuarioId("u-1"), CORREO, "Otra", HASH, EstadoCuenta.ACTIVA,
                PermisosEfectivos.deRol(RolVeedor.VEEDOR), null, AHORA, AHORA);
    }

    @Test
    void debeCrearLaCuentaDeVecinoPendienteDeVerificacionConSuBarrio() {
        Usuario guardado = registrarYCapturar(SOLO_PRIVACIDAD);

        assertThat(guardado.permisos().rol()).isEqualTo(RolVeedor.VECINO);
        assertThat(guardado.estado()).isEqualTo(EstadoCuenta.PENDIENTE_VERIFICACION);
        assertThat(guardado.barrio()).isEqualTo(MANGA);
        assertThat(guardado.barrioVerificado()).isFalse();
    }

    @Test
    void debeGuardarElConsentimientoDePrivacidadConLaVersionVigenteYLaFecha() {
        Usuario guardado = registrarYCapturar(SOLO_PRIVACIDAD);

        assertThat(guardado.consentimientos()).singleElement().satisfies(c -> {
            assertThat(c.tipo()).isEqualTo(TipoConsentimiento.PRIVACIDAD);
            assertThat(c.version()).isEqualTo("2026-10-v1");
            assertThat(c.fecha()).isEqualTo(AHORA);
        });
        assertThat(guardado.recibeAvisos()).isFalse();
    }

    /** La casilla de avisos es aparte: sin marcarla, la cuenta no recibe correos de cortes. */
    @Test
    void debeGuardarElConsentimientoDeAvisosSoloSiSeMarco() {
        Usuario guardado = registrarYCapturar(new ConsentimientosAceptados(true, true));

        assertThat(guardado.recibeAvisos()).isTrue();
    }

    @Test
    void debeMandarElEnlaceDeVerificacionYAuditarElRegistro() {
        registro().registrar(CORREO, "Vecina", CLAVE, MANGA, SOLO_PRIVACIDAD, CONTEXTO);

        verify(emisorDeTokens).emitir(any(), eq(TipoTokenCuenta.VERIFICACION_CORREO));
        verify(notificaciones).enviarVerificacionDeCorreo(any(), eq("token-en-claro"));
        verify(auditoria).registrar(eq(AccionAuditada.CUENTA_REGISTRADA), any(), anyString(), eq(CONTEXTO));
    }

    @Test
    void debeGuardarElCorreoNormalizado() {
        registro().registrar(new CorreoElectronico("Vecina@Ejemplo.ORG"), "Vecina", CLAVE, MANGA,
                SOLO_PRIVACIDAD, CONTEXTO);

        ArgumentCaptor<Usuario> guardado = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios).guardar(guardado.capture());
        assertThat(guardado.getValue().correo().valor()).isEqualTo("vecina@ejemplo.org");
    }

    /** Sin aceptar el aviso de privacidad no hay cuenta: se rechaza antes de mirar el correo (RNF024). */
    @Test
    void debeRechazarElRegistroSinAceptarLaPrivacidad() {
        assertThatThrownBy(() -> registro().registrar(CORREO, "Vecina", CLAVE, MANGA,
                new ConsentimientosAceptados(false, true), CONTEXTO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("privacidad");
        verify(usuarios, never()).buscarPorCorreo(any());
        verify(usuarios, never()).guardar(any());
    }

    @Test
    void debeRechazarElRegistroSinBarrio() {
        assertThatThrownBy(() -> registro().registrar(CORREO, "Vecina", CLAVE, null, SOLO_PRIVACIDAD, CONTEXTO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("barrio");
        verify(usuarios, never()).buscarPorCorreo(any());
    }

    /** RNF024: el 400 por barrio inexistente sale antes de mirar el correo, así que no delata cuentas. */
    @Test
    void debeRechazarUnBarrioInexistenteIgualExistaONoElCorreo() {
        given(sectores.buscarPorId(any())).willReturn(Optional.empty());
        given(usuarios.buscarPorCorreo(any())).willReturn(Optional.of(cuentaExistente()));

        assertThatThrownBy(() -> registro().registrar(CORREO, "Vecina", CLAVE, new SectorId("no-existe"),
                SOLO_PRIVACIDAD, CONTEXTO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No existe el barrio");
        verify(usuarios, never()).buscarPorCorreo(any());
        verify(notificaciones, never()).avisarCambioDeAcceso(any(), anyString(), anyString());
    }

    /** Se avisa al titular de la dirección, no a quien rellenó el formulario: el formulario no revela cuentas. */
    @Test
    void conUnCorreoYaRegistradoNoDebeCrearNadaNiFallar() {
        given(usuarios.buscarPorCorreo(any())).willReturn(Optional.of(cuentaExistente()));

        registro().registrar(CORREO, "Vecina", CLAVE, MANGA, SOLO_PRIVACIDAD, CONTEXTO);

        verify(usuarios, never()).guardar(any());
        verify(notificaciones, never()).enviarVerificacionDeCorreo(any(), anyString());
        verify(notificaciones).avisarCambioDeAcceso(any(), anyString(), anyString());
    }

    @Test
    void conUnCorreoYaRegistradoDebeGastarElMismoTiempoQueUnAltaNueva() {
        given(usuarios.buscarPorCorreo(any())).willReturn(Optional.of(cuentaExistente()));

        registro().registrar(CORREO, "Vecina", CLAVE, MANGA, SOLO_PRIVACIDAD, CONTEXTO);

        verify(cifrador).gastarTiempoEquivalente();
    }

    @Test
    void conUnCorreoNuevoNoDebeGastarTiempoDeMas() {
        registro().registrar(CORREO, "Vecina", CLAVE, MANGA, SOLO_PRIVACIDAD, CONTEXTO);

        verify(cifrador, never()).gastarTiempoEquivalente();
    }

    @Test
    void debeIgualarLaDuracionExistaONoElCorreo() {
        registro().registrar(CORREO, "Vecina", CLAVE, MANGA, SOLO_PRIVACIDAD, CONTEXTO);
        given(usuarios.buscarPorCorreo(any())).willReturn(Optional.of(cuentaExistente()));
        registro().registrar(CORREO, "Vecina", CLAVE, MANGA, SOLO_PRIVACIDAD, CONTEXTO);

        verify(tiempoConstante, times(2)).ejecutar(any());
    }

    /** Otro registro con el mismo correo ganó la carrera: la respuesta sigue siendo la uniforme. */
    @Test
    void unaAltaConcurrenteConElMismoCorreoNoDebeFallarNiMandarElEnlace() {
        given(usuarios.guardar(any())).willThrow(new CorreoYaRegistradoException("vecina@ejemplo.org"));

        registro().registrar(CORREO, "Vecina", CLAVE, MANGA, SOLO_PRIVACIDAD, CONTEXTO);

        verify(notificaciones, never()).enviarVerificacionDeCorreo(any(), anyString());
    }
}
