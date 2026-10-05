package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.ClaveEnClaro;
import com.aguavigia.ctg.domain.ClaveHash;
import com.aguavigia.ctg.domain.ContextoDeAccion;
import com.aguavigia.ctg.domain.CorreoElectronico;
import com.aguavigia.ctg.domain.EstadoCuenta;
import com.aguavigia.ctg.domain.PermisosEfectivos;
import com.aguavigia.ctg.domain.RolVeedor;
import com.aguavigia.ctg.domain.port.out.SectorRepository;
import com.aguavigia.ctg.domain.Sector;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.TipoTokenCuenta;
import com.aguavigia.ctg.domain.Usuario;
import com.aguavigia.ctg.domain.UsuarioId;
import com.aguavigia.ctg.domain.port.out.CifradorClavePort;
import com.aguavigia.ctg.domain.port.out.ControlIntentosPort;
import com.aguavigia.ctg.domain.port.out.NotificacionCuentaPort;
import com.aguavigia.ctg.domain.port.out.RevocacionSesionPort;
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
 * Alta abierta e "olvidé mi clave" son los dos endpoints públicos que podrían convertirse en un
 * buscador de cuentas ajenas. Estas pruebas fijan justo esa propiedad: los dos terminan igual
 * exista o no la cuenta, y lo que cambia es a quién le llega el correo, no lo que ve quien pregunta.
 */
class AltaYRecuperacionDeCuentaTest {

    private static final Instant AHORA = Instant.parse("2026-08-09T20:00:00Z");
    private static final ClaveHash HASH =
            new ClaveHash("$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy");
    private static final CorreoElectronico CORREO = new CorreoElectronico("ana@ejemplo.org");
    private static final ClaveEnClaro CLAVE = new ClaveEnClaro("clave-larga-y-variada");
    private static final ContextoDeAccion CONTEXTO = ContextoDeAccion.anonimo("10.0.0.1");

    private UsuarioRepository usuarios;
    private CifradorClavePort cifrador;
    private EmisorDeTokensDeCuenta emisorDeTokens;
    private NotificacionCuentaPort notificaciones;
    private RegistroDeAuditoria auditoria;
    private RevocacionSesionPort revocacion;
    private ControlIntentosPort intentos;
    private SectorRepository sectores;
    private TiempoConstantePort tiempoConstante;

    @BeforeEach
    void montar() {
        usuarios = mock(UsuarioRepository.class);
        cifrador = mock(CifradorClavePort.class);
        emisorDeTokens = mock(EmisorDeTokensDeCuenta.class);
        notificaciones = mock(NotificacionCuentaPort.class);
        auditoria = mock(RegistroDeAuditoria.class);
        revocacion = mock(RevocacionSesionPort.class);
        intentos = mock(ControlIntentosPort.class);
        sectores = mock(SectorRepository.class);
        tiempoConstante = mock(TiempoConstantePort.class);
        doAnswer(invocacion -> {
            ((Runnable) invocacion.getArgument(0)).run();
            return null;
        }).when(tiempoConstante).ejecutar(any());

        given(usuarios.guardar(any())).willAnswer(invocacion -> invocacion.getArgument(0));
        given(cifrador.cifrar(anyString())).willReturn(HASH);
        given(emisorDeTokens.emitir(any(), any())).willReturn("token-en-claro");
    }

    private RegistrarUsuarioService registro() {
        return new RegistrarUsuarioService(
                usuarios, cifrador, emisorDeTokens, notificaciones, auditoria, () -> AHORA, sectores,
                tiempoConstante);
    }

    private RestablecerClaveService restablecimiento() {
        return new RestablecerClaveService(usuarios, emisorDeTokens, cifrador, revocacion,
                intentos, notificaciones, auditoria, () -> AHORA, tiempoConstante);
    }

    private static Usuario cuenta(EstadoCuenta estado) {
        return new Usuario(new UsuarioId("u-1"), CORREO, "Ana", HASH, estado,
                PermisosEfectivos.deRol(RolVeedor.VEEDOR), null, AHORA, AHORA);
    }

    @Test
    void registrarseDebeCrearLaCuentaPendienteDeVerificacionYMandarElEnlace() {
        given(usuarios.buscarPorCorreo(any())).willReturn(Optional.empty());

        registro().registrar(CORREO, "Ana", CLAVE, CONTEXTO);

        ArgumentCaptor<Usuario> guardado = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios).guardar(guardado.capture());
        assertThat(guardado.getValue().estado()).isEqualTo(EstadoCuenta.PENDIENTE_VERIFICACION);
        verify(notificaciones).enviarVerificacionDeCorreo(any(), eq("token-en-claro"));
    }

    @Test
    void debeGuardarElCorreoNormalizado() {
        given(usuarios.buscarPorCorreo(any())).willReturn(Optional.empty());

        registro().registrar(new CorreoElectronico("Ana@Ejemplo.ORG"), "Ana", CLAVE, CONTEXTO);

        ArgumentCaptor<Usuario> guardado = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios).guardar(guardado.capture());
        assertThat(guardado.getValue().correo().valor()).isEqualTo("ana@ejemplo.org");
    }

    /**
     * Con un correo ya registrado no se avisa a quien rellenó el formulario, se avisa al dueño de
     * la dirección. El formulario no sirve para averiguar qué correos tienen cuenta, y de paso el
     * titular se entera del intento.
     */
    @Test
    void registrarseConUnCorreoYaRegistradoNoDebeCrearNadaNiFallar() {
        given(usuarios.buscarPorCorreo(any())).willReturn(Optional.of(cuenta(EstadoCuenta.ACTIVA)));

        registro().registrar(CORREO, "Ana", CLAVE, CONTEXTO);

        verify(usuarios, never()).guardar(any());
        verify(notificaciones, never()).enviarVerificacionDeCorreo(any(), anyString());
        verify(notificaciones).avisarCambioDeAcceso(any(), anyString(), anyString());
    }

    /** RNF024: la rama de un correo existente no cifra nada, y sin esto responde mucho más rápido que un alta nueva. */
    @Test
    void registrarseConUnCorreoYaRegistradoDebeGastarElMismoTiempoQueUnAltaNueva() {
        given(usuarios.buscarPorCorreo(any())).willReturn(Optional.of(cuenta(EstadoCuenta.ACTIVA)));

        registro().registrar(CORREO, "Ana", CLAVE, CONTEXTO);

        verify(cifrador).gastarTiempoEquivalente();
    }

    /** RNF024: el cifrado no iguala guardar, emitir el token y auditar; la espera mínima sí. */
    @Test
    void registrarseDebeIgualarLaDuracionExistaONoElCorreo() {
        given(usuarios.buscarPorCorreo(any())).willReturn(Optional.empty());
        registro().registrar(CORREO, "Ana", CLAVE, CONTEXTO);

        given(usuarios.buscarPorCorreo(any())).willReturn(Optional.of(cuenta(EstadoCuenta.ACTIVA)));
        registro().registrar(CORREO, "Ana", CLAVE, CONTEXTO);

        verify(tiempoConstante, times(2)).ejecutar(any());
    }

    @Test
    void registrarseConUnBarrioQueExisteDebeGuardarloEnLaCuenta() {
        SectorId manga = new SectorId("manga");
        given(sectores.buscarPorId(manga)).willReturn(Optional.of(new Sector(manga, "Manga", 1000, null)));
        given(usuarios.buscarPorCorreo(any())).willReturn(Optional.empty());

        registro().registrar(CORREO, "Ana", CLAVE, manga, CONTEXTO);

        ArgumentCaptor<Usuario> guardado = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios).guardar(guardado.capture());
        assertThat(guardado.getValue().barrio()).isEqualTo(manga);
    }

    /** RNF024: el 400 por barrio inexistente sale antes de mirar el correo, así que no delata cuentas. */
    @Test
    void registrarseConUnBarrioInexistenteDebeRechazarseIgualExistaONoElCorreo() {
        given(sectores.buscarPorId(any())).willReturn(Optional.empty());
        given(usuarios.buscarPorCorreo(any())).willReturn(Optional.of(cuenta(EstadoCuenta.ACTIVA)));

        assertThatThrownBy(() -> registro().registrar(CORREO, "Ana", CLAVE, new SectorId("no-existe"), CONTEXTO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No existe el barrio");
        verify(usuarios, never()).buscarPorCorreo(any());
        verify(notificaciones, never()).avisarCambioDeAcceso(any(), anyString(), anyString());
    }

    @Test
    void registrarseConUnCorreoNuevoNoDebeGastarTiempoDeMas() {
        given(usuarios.buscarPorCorreo(any())).willReturn(Optional.empty());

        registro().registrar(CORREO, "Ana", CLAVE, CONTEXTO);

        verify(cifrador, never()).gastarTiempoEquivalente();
    }

    /** RNF024: con o sin cuenta la petición pasa por la espera mínima, así que dura lo mismo. */
    @Test
    void pedirRestablecimientoDebeIgualarLaDuracionExistaONoLaCuenta() {
        given(usuarios.buscarPorCorreo(any())).willReturn(Optional.empty());
        restablecimiento().solicitar(CORREO, CONTEXTO);

        given(usuarios.buscarPorCorreo(any())).willReturn(Optional.of(cuenta(EstadoCuenta.ACTIVA)));
        restablecimiento().solicitar(CORREO, CONTEXTO);

        verify(tiempoConstante, times(2)).ejecutar(any());
    }

    @Test
    void pedirRestablecimientoDeUnCorreoInexistenteNoDebeFallarNiMandarNada() {
        given(usuarios.buscarPorCorreo(any())).willReturn(Optional.empty());

        restablecimiento().solicitar(CORREO, CONTEXTO);

        verify(notificaciones, never()).enviarEnlaceDeRestablecimiento(any(), anyString());
    }

    @Test
    void pedirRestablecimientoDeUnaCuentaRealDebeMandarElEnlace() {
        given(usuarios.buscarPorCorreo(any())).willReturn(Optional.of(cuenta(EstadoCuenta.ACTIVA)));

        restablecimiento().solicitar(CORREO, CONTEXTO);

        verify(notificaciones).enviarEnlaceDeRestablecimiento(any(), eq("token-en-claro"));
    }

    /** Mandárselo la dejaría activarse saltándose la aprobación que su estado exige. */
    @Test
    void noDebeMandarEnlaceDeRestablecimientoAUnaCuentaInvitada() {
        given(usuarios.buscarPorCorreo(any())).willReturn(Optional.of(Usuario.invitado(
                new UsuarioId("u-2"), CORREO, "Beto", RolVeedor.VEEDOR, AHORA)));

        restablecimiento().solicitar(CORREO, CONTEXTO);

        verify(notificaciones, never()).enviarEnlaceDeRestablecimiento(any(), anyString());
    }

    @Test
    void noDebeMandarEnlaceDeRestablecimientoAUnaCuentaRechazada() {
        given(usuarios.buscarPorCorreo(any())).willReturn(Optional.of(cuenta(EstadoCuenta.RECHAZADA)));

        restablecimiento().solicitar(CORREO, CONTEXTO);

        verify(notificaciones, never()).enviarEnlaceDeRestablecimiento(any(), anyString());
    }

    /**
     * Quien pide cambiar su clave a menudo sospecha que otro la tiene. Dejarle la sesión abierta al
     * intruso vaciaría de sentido el cambio.
     */
    @Test
    void restablecerLaClaveDebeRevocarTodasLasSesiones() {
        given(emisorDeTokens.consumir("token-en-claro", TipoTokenCuenta.RESTABLECER_CLAVE))
                .willReturn(cuenta(EstadoCuenta.ACTIVA));

        restablecimiento().restablecer("token-en-claro", CLAVE, CONTEXTO);

        verify(revocacion).revocarSesionesAnterioresA(new UsuarioId("u-1"), AHORA);
    }

    /** Quien acaba de probar que controla el correo no debe seguir pagando el bloqueo del atacante. */
    @Test
    void restablecerLaClaveDebeLevantarElBloqueoPorIntentosFallidos() {
        given(emisorDeTokens.consumir("token-en-claro", TipoTokenCuenta.RESTABLECER_CLAVE))
                .willReturn(cuenta(EstadoCuenta.ACTIVA));

        restablecimiento().restablecer("token-en-claro", CLAVE, CONTEXTO);

        verify(intentos).limpiarIntentos("ana@ejemplo.org");
    }

    @Test
    void restablecerLaClaveDebeAvisarPorCorreoDelCambio() {
        given(emisorDeTokens.consumir("token-en-claro", TipoTokenCuenta.RESTABLECER_CLAVE))
                .willReturn(cuenta(EstadoCuenta.ACTIVA));

        restablecimiento().restablecer("token-en-claro", CLAVE, CONTEXTO);

        verify(notificaciones).avisarCambioDeAcceso(any(), anyString(), anyString());
    }

    @Test
    void verificarElCorreoDebeDejarLaCuentaEsperandoAprobacion() {
        given(emisorDeTokens.consumir("token-en-claro", TipoTokenCuenta.VERIFICACION_CORREO))
                .willReturn(cuenta(EstadoCuenta.PENDIENTE_VERIFICACION));

        Usuario verificado = new VerificarCorreoService(usuarios, emisorDeTokens, auditoria, () -> AHORA)
                .verificar("token-en-claro", CONTEXTO);

        assertThat(verificado.estado()).isEqualTo(EstadoCuenta.PENDIENTE_APROBACION);
    }

    private static Usuario vecinoSinClave() {
        return Usuario.registradoComoVecino(new UsuarioId("v-1"), CORREO, "Vecina", new SectorId("manga"),
                java.util.List.of(new com.aguavigia.ctg.domain.Consentimiento(
                        com.aguavigia.ctg.domain.TipoConsentimiento.PRIVACIDAD, "v1", AHORA)), AHORA);
    }

    /** Un vecino no espera a un admin: fijar su clave desde el enlace basta, y la auditoría no debe decir lo contrario. */
    @Test
    void aceptarElEnlaceDeUnVecinoDebeDejarlaActivaConLaClaveQueEligioYAuditarlo() {
        given(emisorDeTokens.consumir("token-en-claro", TipoTokenCuenta.INVITACION)).willReturn(vecinoSinClave());

        Usuario activo = new AceptarInvitacionService(usuarios, emisorDeTokens, cifrador, auditoria, () -> AHORA)
                .aceptar("token-en-claro", CLAVE, CONTEXTO);

        assertThat(activo.estado()).isEqualTo(EstadoCuenta.ACTIVA);
        assertThat(activo.claveHash()).isEqualTo(HASH);
        ArgumentCaptor<String> detalle = ArgumentCaptor.forClass(String.class);
        verify(auditoria).registrarConAutor(eq(com.aguavigia.ctg.domain.AccionAuditada.INVITACION_ACEPTADA),
                any(), any(), detalle.capture(), any());
        assertThat(detalle.getValue()).doesNotContain("aprobación");
    }

    @Test
    void aceptarLaInvitacionDebeDejarLaCuentaActiva() {
        given(emisorDeTokens.consumir("token-en-claro", TipoTokenCuenta.INVITACION))
                .willReturn(Usuario.invitado(new UsuarioId("u-2"), CORREO, "Beto", RolVeedor.VEEDOR, AHORA));

        Usuario activo = new AceptarInvitacionService(
                usuarios, emisorDeTokens, cifrador, auditoria, () -> AHORA)
                .aceptar("token-en-claro", CLAVE, CONTEXTO);

        assertThat(activo.estado()).isEqualTo(EstadoCuenta.ACTIVA);
        assertThat(activo.claveHash()).isEqualTo(HASH);
    }
}
