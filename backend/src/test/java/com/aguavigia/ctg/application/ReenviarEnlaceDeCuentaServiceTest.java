package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.AccionAuditada;
import com.aguavigia.ctg.domain.ClaveHash;
import com.aguavigia.ctg.domain.ContextoDeAccion;
import com.aguavigia.ctg.domain.CorreoElectronico;
import com.aguavigia.ctg.domain.EntidadNoEncontradaException;
import com.aguavigia.ctg.domain.EstadoCuenta;
import com.aguavigia.ctg.domain.PermisosEfectivos;
import com.aguavigia.ctg.domain.RolVeedor;
import com.aguavigia.ctg.domain.TipoTokenCuenta;
import com.aguavigia.ctg.domain.Usuario;
import com.aguavigia.ctg.domain.UsuarioId;
import com.aguavigia.ctg.domain.port.out.ControlIntentosPort;
import com.aguavigia.ctg.domain.port.out.NotificacionCuentaPort;
import com.aguavigia.ctg.domain.port.out.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ReenviarEnlaceDeCuentaServiceTest {

    private static final Instant AHORA = Instant.parse("2026-08-09T20:00:00Z");
    private static final ClaveHash HASH =
            new ClaveHash("$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy");
    private static final CorreoElectronico CORREO = new CorreoElectronico("ana@ejemplo.org");
    private static final UsuarioId ADMIN_ID = new UsuarioId("admin-1");
    private static final ContextoDeAccion CONTEXTO_ADMIN = new ContextoDeAccion(ADMIN_ID, "10.0.0.1");

    private UsuarioRepository usuarios;
    private EmisorDeTokensDeCuenta emisorDeTokens;
    private NotificacionCuentaPort notificaciones;
    private ControlIntentosPort control;
    private RegistroDeAuditoria auditoria;
    private ReenviarEnlaceDeCuentaService servicio;

    @BeforeEach
    void montar() {
        usuarios = mock(UsuarioRepository.class);
        emisorDeTokens = mock(EmisorDeTokensDeCuenta.class);
        notificaciones = mock(NotificacionCuentaPort.class);
        control = mock(ControlIntentosPort.class);
        auditoria = mock(RegistroDeAuditoria.class);
        given(control.consumirPorPrimeraVez(anyString(), any())).willReturn(true);
        given(emisorDeTokens.emitir(any(), any())).willReturn("token-nuevo");
        servicio = new ReenviarEnlaceDeCuentaService(usuarios, emisorDeTokens, notificaciones, control, auditoria);
    }

    private static Usuario cuenta(EstadoCuenta estado) {
        return new Usuario(new UsuarioId("u-1"), CORREO, "Ana", HASH, estado,
                PermisosEfectivos.deRol(RolVeedor.VEEDOR), null, AHORA, AHORA);
    }

    private static Usuario admin() {
        return new Usuario(ADMIN_ID, new CorreoElectronico("admin@ejemplo.org"), "Admin", HASH, EstadoCuenta.ACTIVA,
                PermisosEfectivos.deRol(RolVeedor.ADMIN), null, AHORA, AHORA);
    }

    @Test
    void debeReenviarLaVerificacionConUnEnlaceNuevoAUnaCuentaPendiente() {
        given(usuarios.buscarPorCorreo(any())).willReturn(Optional.of(cuenta(EstadoCuenta.PENDIENTE_VERIFICACION)));

        servicio.reenviarVerificacion(CORREO, ContextoDeAccion.anonimo("1.1.1.1"));

        verify(emisorDeTokens).emitir(new UsuarioId("u-1"), TipoTokenCuenta.VERIFICACION_CORREO);
        verify(notificaciones).enviarVerificacionDeCorreo(any(), eq("token-nuevo"));
    }

    @Test
    void noDebeRevelarSiElCorreoExisteNiHacerNadaSiNoHayCuenta() {
        given(usuarios.buscarPorCorreo(any())).willReturn(Optional.empty());

        servicio.reenviarVerificacion(CORREO, ContextoDeAccion.anonimo("1.1.1.1"));

        verify(notificaciones, never()).enviarVerificacionDeCorreo(any(), anyString());
    }

    @Test
    void noDebeReenviarLaVerificacionASiYaNoLaNecesita() {
        given(usuarios.buscarPorCorreo(any())).willReturn(Optional.of(cuenta(EstadoCuenta.ACTIVA)));

        servicio.reenviarVerificacion(CORREO, ContextoDeAccion.anonimo("1.1.1.1"));

        verify(emisorDeTokens, never()).emitir(any(), any());
    }

    @Test
    void noDebeSerUnaBombaDeCorreoSoloUnReenvioCadaDosMinutosPorCuenta() {
        given(usuarios.buscarPorCorreo(any())).willReturn(Optional.of(cuenta(EstadoCuenta.PENDIENTE_VERIFICACION)));
        given(control.consumirPorPrimeraVez("reenvio-verificacion:u-1", Duration.ofMinutes(2))).willReturn(false);

        servicio.reenviarVerificacion(CORREO, ContextoDeAccion.anonimo("1.1.1.1"));

        verify(emisorDeTokens, never()).emitir(any(), any());
        verify(notificaciones, never()).enviarVerificacionDeCorreo(any(), anyString());
    }

    @Test
    void debeReenviarLaInvitacionYAuditarla() {
        Usuario invitado = Usuario.invitado(new UsuarioId("u-2"), CORREO, "Ana", RolVeedor.VEEDOR, AHORA);
        given(usuarios.buscarPorId(new UsuarioId("u-2"))).willReturn(Optional.of(invitado));
        given(usuarios.buscarPorId(ADMIN_ID)).willReturn(Optional.of(admin()));

        servicio.reenviarInvitacion(new UsuarioId("u-2"), CONTEXTO_ADMIN);

        verify(emisorDeTokens).emitir(new UsuarioId("u-2"), TipoTokenCuenta.INVITACION);
        verify(notificaciones).enviarInvitacion(eq(invitado), any(), eq("token-nuevo"));
        verify(auditoria).registrarConAutor(eq(AccionAuditada.CUENTA_INVITADA), any(), eq(invitado), anyString(), eq(CONTEXTO_ADMIN));
    }

    @Test
    void reenviarLaInvitacionDeUnaCuentaInexistenteDebeSer404() {
        given(usuarios.buscarPorId(new UsuarioId("no-existe"))).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.reenviarInvitacion(new UsuarioId("no-existe"), CONTEXTO_ADMIN))
                .isInstanceOf(EntidadNoEncontradaException.class);
    }

    @Test
    void reenviarLaInvitacionDeUnaCuentaQueYaLaAceptoDebeSerConflicto() {
        given(usuarios.buscarPorId(new UsuarioId("u-1"))).willReturn(Optional.of(cuenta(EstadoCuenta.ACTIVA)));

        assertThatThrownBy(() -> servicio.reenviarInvitacion(new UsuarioId("u-1"), CONTEXTO_ADMIN))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("aún no la aceptó");

        verify(notificaciones, never()).enviarInvitacion(any(), any(), anyString());
    }
}
