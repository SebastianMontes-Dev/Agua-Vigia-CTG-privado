package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.AccionAuditada;
import com.aguavigia.ctg.domain.AlcanceSesion;
import com.aguavigia.ctg.domain.ClaveHash;
import com.aguavigia.ctg.domain.ContextoDeAccion;
import com.aguavigia.ctg.domain.CorreoElectronico;
import com.aguavigia.ctg.domain.CuentaNoHabilitadaException;
import com.aguavigia.ctg.domain.EstadoCuenta;
import com.aguavigia.ctg.domain.PermisosEfectivos;
import com.aguavigia.ctg.domain.RolVeedor;
import com.aguavigia.ctg.domain.SesionEmitida;
import com.aguavigia.ctg.domain.Usuario;
import com.aguavigia.ctg.domain.UsuarioId;
import com.aguavigia.ctg.domain.port.out.EmisorDeSesionPort;
import com.aguavigia.ctg.domain.port.out.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class IniciarSesionDeAdminDeSimulacionServiceTest {

    private static final Instant AHORA = Instant.parse("2026-11-02T13:00:00Z");
    private static final ClaveHash HASH = new ClaveHash("$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy");
    private static final ContextoDeAccion CONTEXTO = ContextoDeAccion.anonimo("172.18.0.9");

    private UsuarioRepository usuarios;
    private EmisorDeSesionPort emisor;
    private RegistroDeAuditoria auditoria;
    private IniciarSesionDeAdminDeSimulacionService servicio;

    @BeforeEach
    void montar() {
        usuarios = mock(UsuarioRepository.class);
        emisor = mock(EmisorDeSesionPort.class);
        auditoria = mock(RegistroDeAuditoria.class);
        servicio = new IniciarSesionDeAdminDeSimulacionService(usuarios, emisor, auditoria);
        given(emisor.emitir(any(), any())).willReturn("token-de-admin");
    }

    private static Usuario admin(EstadoCuenta estado) {
        return new Usuario(new UsuarioId("a-1"), new CorreoElectronico("admin@aguavigia.local"), "Admin", HASH, estado,
                PermisosEfectivos.deRol(RolVeedor.ADMIN), null, AHORA, AHORA);
    }

    /** Sin segundo factor: en la simulación no hay quien lea un QR, y esta ruta solo existe allí. */
    @Test
    void debeDarSesionCompletaDelAdminInicialAunqueNoTengaSegundoFactor() {
        given(usuarios.buscarPrimeroPorRol(RolVeedor.ADMIN)).willReturn(Optional.of(admin(EstadoCuenta.ACTIVA)));

        SesionEmitida sesion = servicio.iniciar(CONTEXTO);

        assertThat(sesion.token()).isEqualTo("token-de-admin");
        assertThat(sesion.alcance()).isEqualTo(AlcanceSesion.COMPLETO);
        assertThat(sesion.rol()).isEqualTo(RolVeedor.ADMIN);
        verify(emisor).emitir(any(), eq(AlcanceSesion.COMPLETO));
    }

    @Test
    void debeAuditarQueLaSesionSeAbrioPorLaViaDeSimulacion() {
        Usuario admin = admin(EstadoCuenta.ACTIVA);
        given(usuarios.buscarPrimeroPorRol(RolVeedor.ADMIN)).willReturn(Optional.of(admin));

        servicio.iniciar(CONTEXTO);

        verify(auditoria).registrarConAutor(eq(AccionAuditada.SESION_INICIADA), eq(admin), eq(admin), contains("simulación"), eq(CONTEXTO));
    }

    @Test
    void sinAdminNoHaySesion() {
        given(usuarios.buscarPrimeroPorRol(RolVeedor.ADMIN)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.iniciar(CONTEXTO)).isInstanceOf(IllegalStateException.class);
        verify(emisor, never()).emitir(any(), any());
    }

    @Test
    void unAdminSuspendidoNoEntra() {
        given(usuarios.buscarPrimeroPorRol(RolVeedor.ADMIN)).willReturn(Optional.of(admin(EstadoCuenta.SUSPENDIDA)));

        assertThatThrownBy(() -> servicio.iniciar(CONTEXTO)).isInstanceOf(CuentaNoHabilitadaException.class);
        verify(emisor, never()).emitir(any(), any());
    }
}
