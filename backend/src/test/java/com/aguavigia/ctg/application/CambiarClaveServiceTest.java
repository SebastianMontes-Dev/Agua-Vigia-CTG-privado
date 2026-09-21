package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.AccionAuditada;
import com.aguavigia.ctg.domain.ClaveEnClaro;
import com.aguavigia.ctg.domain.ClaveHash;
import com.aguavigia.ctg.domain.ContextoDeAccion;
import com.aguavigia.ctg.domain.CorreoElectronico;
import com.aguavigia.ctg.domain.CuentaBloqueadaException;
import com.aguavigia.ctg.domain.EstadoCuenta;
import com.aguavigia.ctg.domain.PermisosEfectivos;
import com.aguavigia.ctg.domain.RolVeedor;
import com.aguavigia.ctg.domain.Usuario;
import com.aguavigia.ctg.domain.UsuarioId;
import com.aguavigia.ctg.domain.port.out.CifradorClavePort;
import com.aguavigia.ctg.domain.port.out.ControlIntentosPort;
import com.aguavigia.ctg.domain.port.out.NotificacionCuentaPort;
import com.aguavigia.ctg.domain.port.out.RevocacionSesionPort;
import com.aguavigia.ctg.domain.port.out.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Cambiar la propia clave con la sesión iniciada: exige la actual y cierra todas las sesiones. */
class CambiarClaveServiceTest {

    private static final Instant AHORA = Instant.parse("2026-08-09T20:00:00Z");
    private static final ClaveHash HASH_ACTUAL =
            new ClaveHash("$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy");
    private static final ClaveHash HASH_NUEVO =
            new ClaveHash("$2a$10$abcdefghijklmnopqrstuuABCDEFGHIJKLMNOPQRSTUVWXYZ0123456");
    private static final CorreoElectronico CORREO = new CorreoElectronico("ana@ejemplo.org");
    private static final UsuarioId ID = new UsuarioId("u-1");
    private static final ContextoDeAccion CONTEXTO = new ContextoDeAccion(ID, "10.0.0.1");
    private static final String CLAVE_ACTUAL = "la-clave-de-hoy-123";
    private static final ClaveEnClaro CLAVE_NUEVA = new ClaveEnClaro("la-clave-nueva-456");

    private UsuarioRepository usuarios;
    private CifradorClavePort cifrador;
    private RevocacionSesionPort revocacion;
    private ControlIntentosPort intentos;
    private NotificacionCuentaPort notificaciones;
    private RegistroDeAuditoria auditoria;
    private CambiarClaveService servicio;

    @BeforeEach
    void montar() {
        usuarios = mock(UsuarioRepository.class);
        cifrador = mock(CifradorClavePort.class);
        revocacion = mock(RevocacionSesionPort.class);
        intentos = mock(ControlIntentosPort.class);
        notificaciones = mock(NotificacionCuentaPort.class);
        auditoria = mock(RegistroDeAuditoria.class);

        given(usuarios.buscarPorId(ID)).willReturn(Optional.of(cuenta()));
        given(usuarios.guardar(any())).willAnswer(invocacion -> invocacion.getArgument(0));
        given(intentos.bloqueoVigente(anyString())).willReturn(Optional.empty());
        given(cifrador.coincide(CLAVE_ACTUAL, HASH_ACTUAL)).willReturn(true);
        given(cifrador.cifrar(CLAVE_NUEVA.valor())).willReturn(HASH_NUEVO);

        servicio = new CambiarClaveService(usuarios, cifrador, revocacion, intentos, notificaciones,
                auditoria, () -> AHORA, 5, 15, 15);
    }

    private static Usuario cuenta() {
        return new Usuario(ID, CORREO, "Ana", HASH_ACTUAL, EstadoCuenta.ACTIVA,
                PermisosEfectivos.deRol(RolVeedor.VEEDOR), null, AHORA, AHORA);
    }

    @Test
    void debeGuardarElHashDeLaClaveNuevaYRevocarTodasLasSesiones() {
        servicio.cambiar(ID, CLAVE_ACTUAL, CLAVE_NUEVA, CONTEXTO);

        ArgumentCaptor<Usuario> guardado = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios).guardar(guardado.capture());
        assertThat(guardado.getValue().claveHash()).isEqualTo(HASH_NUEVO);
        verify(revocacion).revocarSesionesAnterioresA(ID, AHORA);
    }

    @Test
    void debeLevantarElBloqueoAvisarPorCorreoYAuditar() {
        servicio.cambiar(ID, CLAVE_ACTUAL, CLAVE_NUEVA, CONTEXTO);

        verify(intentos).limpiarIntentos("ana@ejemplo.org");
        verify(notificaciones).avisarCambioDeAcceso(any(), anyString(), anyString());
        verify(auditoria).registrarConAutor(eq(AccionAuditada.CLAVE_CAMBIADA), any(), any(), anyString(), eq(CONTEXTO));
    }

    @Test
    void unaClaveActualIncorrectaNoDebeCambiarNadaYDebeContarComoFallo() {
        given(cifrador.coincide("otra-clave-cualquiera", HASH_ACTUAL)).willReturn(false);

        assertThatThrownBy(() -> servicio.cambiar(ID, "otra-clave-cualquiera", CLAVE_NUEVA, CONTEXTO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("clave actual");

        verify(intentos).registrarFallo("ana@ejemplo.org", Duration.ofMinutes(15), 5, Duration.ofMinutes(15));
        verify(usuarios, never()).guardar(any());
        verify(revocacion, never()).revocarSesionesAnterioresA(any(), any());
    }

    @Test
    void conLaCuentaBloqueadaNoDebeNiSiquieraComprobarLaClave() {
        given(intentos.bloqueoVigente("ana@ejemplo.org")).willReturn(Optional.of(Duration.ofMinutes(9)));

        assertThatThrownBy(() -> servicio.cambiar(ID, CLAVE_ACTUAL, CLAVE_NUEVA, CONTEXTO))
                .isInstanceOf(CuentaBloqueadaException.class);

        // Sin esto, un token robado serviría para adivinar la clave actual sin freno.
        verify(cifrador, never()).coincide(anyString(), any());
        verify(usuarios, never()).guardar(any());
    }

    @Test
    void laClaveNuevaIgualALaActualDebeRechazarseSinContarComoFallo() {
        assertThatThrownBy(() -> servicio.cambiar(ID, CLAVE_ACTUAL, new ClaveEnClaro(CLAVE_ACTUAL), CONTEXTO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("distinta");

        verify(intentos, never()).registrarFallo(anyString(), any(), org.mockito.ArgumentMatchers.anyInt(), any());
        verify(usuarios, never()).guardar(any());
    }

    @Test
    void siLaSesionYaNoCorrespondeAUnaCuentaDebeFallarComoErrorDeEstado() {
        given(usuarios.buscarPorId(ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.cambiar(ID, CLAVE_ACTUAL, CLAVE_NUEVA, CONTEXTO))
                .isInstanceOf(IllegalStateException.class);
    }
}
