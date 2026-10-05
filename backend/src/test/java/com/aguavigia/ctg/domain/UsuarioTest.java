package com.aguavigia.ctg.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class UsuarioTest {

    private static final Instant AHORA = Instant.parse("2026-08-09T20:00:00Z");
    private static final Instant DESPUES = AHORA.plusSeconds(3600);
    private static final ClaveHash HASH =
            new ClaveHash("$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy");

    private static Usuario registrado() {
        return Usuario.registrado(new UsuarioId("u-1"), new CorreoElectronico("ana@ejemplo.org"),
                "Ana", HASH, AHORA);
    }

    private static Usuario activo() {
        return registrado().verificarCorreo(AHORA)
                .aprobar(PermisosEfectivos.deRol(RolVeedor.VEEDOR), AHORA);
    }

    @Test
    void quienSeRegistraNaceSinPoderEntrarYSinPermisosUtiles() {
        Usuario nuevo = registrado();

        assertThat(nuevo.estado()).isEqualTo(EstadoCuenta.PENDIENTE_VERIFICACION);
        assertThat(nuevo.estado().permiteIniciarSesion()).isFalse();
        assertThat(nuevo.permisosEfectivos()).doesNotContain(Permiso.MODERAR_REPORTES);
    }

    @Test
    void elBarrioEsOpcionalYSobreviveATodasLasTransiciones() {
        SectorId manga = new SectorId("manga");
        Usuario vecina = Usuario.registrado(new UsuarioId("u-2"), new CorreoElectronico("v@ejemplo.org"),
                "Vecina", HASH, manga, AHORA);
        assertThat(vecina.barrio()).isEqualTo(manga);
        assertThat(registrado().barrio()).isNull();

        Usuario aprobada = vecina.verificarCorreo(DESPUES)
                .aprobar(PermisosEfectivos.deRol(RolVeedor.OBSERVADOR), DESPUES.plusSeconds(1));
        assertThat(aprobada.barrio()).isEqualTo(manga);
        assertThat(aprobada.suspender(DESPUES.plusSeconds(2)).barrio()).isEqualTo(manga);
    }

    @Test
    void unaInvitacionPuedeLlevarBarrio() {
        Usuario invitado = Usuario.invitado(new UsuarioId("u-3"), new CorreoElectronico("i@ejemplo.org"),
                "Invitada", RolVeedor.VEEDOR, new SectorId("crespo"), AHORA);
        assertThat(invitado.barrio()).isEqualTo(new SectorId("crespo"));
    }

    /** El rol VECINO nace del registro abierto; desde el panel no se puede invitar, aprobar ni asignar. */
    @Test
    void debeRechazarInvitarComoVecino() {
        assertThatIllegalArgumentException().isThrownBy(() -> Usuario.invitado(
                new UsuarioId("u-4"), new CorreoElectronico("i@ejemplo.org"), "Invitada",
                RolVeedor.VECINO, AHORA));
    }

    @Test
    void debeRechazarAprobarUnaCuentaDelPanelComoVecino() {
        Usuario pendiente = registrado().verificarCorreo(AHORA);

        assertThatIllegalArgumentException().isThrownBy(
                () -> pendiente.aprobar(PermisosEfectivos.deRol(RolVeedor.VECINO), DESPUES));
    }

    @Test
    void debeRechazarConvertirUnaCuentaDelPanelEnVecino() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> activo().cambiarPermisos(PermisosEfectivos.deRol(RolVeedor.VECINO), DESPUES));
    }

    @Test
    void debeRechazarAscenderUnVecinoAUnRolDePanel() {
        Usuario vecino = vecinoActivo();

        assertThatIllegalArgumentException().isThrownBy(
                () -> vecino.cambiarPermisos(PermisosEfectivos.deRol(RolVeedor.VEEDOR), DESPUES));
    }

    private static final SectorId MANGA = new SectorId("manga");

    /** Lo que llega del formulario de registro: todavía sin clave, que se fija desde el enlace del correo. */
    private static Usuario vecino() {
        return Usuario.registradoComoVecino(new UsuarioId("v-1"), new CorreoElectronico("vecina@ejemplo.org"),
                "Vecina", MANGA,
                java.util.List.of(new Consentimiento(TipoConsentimiento.PRIVACIDAD, "v1", AHORA)), AHORA);
    }

    private static Usuario vecinoActivo() {
        return vecino().aceptarInvitacion(HASH, AHORA);
    }

    /**
     * Quien rellena el formulario no elige la clave: si la eligiera, alguien podría registrar el correo de otra
     * persona con una clave suya y quedarse con la cuenta cuando ella confirmara el enlace.
     */
    @Test
    void unVecinoDebeNacerSinClaveEnEsperaDeFijarlaYConSuBarrioSinVerificar() {
        Usuario nuevo = vecino();

        assertThat(nuevo.permisos().rol()).isEqualTo(RolVeedor.VECINO);
        assertThat(nuevo.estado()).isEqualTo(EstadoCuenta.INVITADA);
        assertThat(nuevo.claveHash()).isNull();
        assertThat(nuevo.barrio()).isEqualTo(MANGA);
        assertThat(nuevo.barrioVerificado()).isFalse();
        assertThat(nuevo.barrioVerificadoEn()).isNull();
        assertThat(nuevo.consentimientos()).hasSize(1);
        assertThat(nuevo.recibeAvisos()).isFalse();
    }

    @Test
    void debeExigirBarrioAlRegistrarUnVecino() {
        assertThatIllegalArgumentException().isThrownBy(() -> Usuario.registradoComoVecino(
                new UsuarioId("v-1"), new CorreoElectronico("vecina@ejemplo.org"), "Vecina", null,
                java.util.List.of(new Consentimiento(TipoConsentimiento.PRIVACIDAD, "v1", AHORA)), AHORA));
    }

    @Test
    void debeExigirElConsentimientoDePrivacidadAlRegistrarUnVecino() {
        assertThatIllegalArgumentException().isThrownBy(() -> Usuario.registradoComoVecino(
                new UsuarioId("v-1"), new CorreoElectronico("vecina@ejemplo.org"), "Vecina", MANGA,
                java.util.List.of(new Consentimiento(TipoConsentimiento.AVISOS, "v1", AHORA)), AHORA));
    }

    /** El panel necesita aprobación humana; un vecino no pide nada que la requiera: fijar su clave basta. */
    @Test
    void fijarLaClaveDeUnVecinoDebeActivarloSinAprobacionDeAdmin() {
        Usuario activo = vecino().aceptarInvitacion(HASH, DESPUES);

        assertThat(activo.estado()).isEqualTo(EstadoCuenta.ACTIVA);
        assertThat(activo.claveHash()).isEqualTo(HASH);
        assertThat(activo.estado().permiteIniciarSesion()).isTrue();
        assertThat(activo.permisosEfectivos()).containsExactly(Permiso.GESTIONAR_PERFIL_PROPIO);
    }

    /** Lo que se acepta vale desde que la persona del correo actúa, no desde que alguien rellenó el formulario. */
    @Test
    void alFijarLaClaveLosConsentimientosDebenTomarLaFechaDeLaActivacion() {
        Usuario nuevo = Usuario.registradoComoVecino(new UsuarioId("v-1"),
                new CorreoElectronico("vecina@ejemplo.org"), "Vecina", MANGA,
                java.util.List.of(new Consentimiento(TipoConsentimiento.PRIVACIDAD, "v1", AHORA),
                        new Consentimiento(TipoConsentimiento.AVISOS, "v1", AHORA)), AHORA);

        Usuario activo = nuevo.aceptarInvitacion(HASH, DESPUES);

        assertThat(activo.consentimientos()).extracting(Consentimiento::fecha).containsOnly(DESPUES);
        assertThat(activo.consentimientos()).extracting(Consentimiento::version).containsOnly("v1");
        assertThat(activo.recibeAvisos()).isTrue();
    }

    @Test
    void unVecinoNoDebePasarPorLaVerificacionDeCorreoDelPanel() {
        assertThatIllegalStateException().isThrownBy(() -> vecino().verificarCorreo(DESPUES));
    }

    @Test
    void verificarElCorreoDeUnaCuentaDelPanelDebeSeguirEsperandoAprobacion() {
        assertThat(registrado().verificarCorreo(AHORA).estado())
                .isEqualTo(EstadoCuenta.PENDIENTE_APROBACION);
    }

    @Test
    void verificarElBarrioDebeMarcarloConSuFecha() {
        Usuario verificado = vecinoActivo().verificarBarrio(DESPUES);

        assertThat(verificado.barrioVerificado()).isTrue();
        assertThat(verificado.barrioVerificadoEn()).isEqualTo(DESPUES);
    }

    @Test
    void debeRechazarVerificarElBarrioDeUnaCuentaQueNoEsDeVecino() {
        assertThatIllegalStateException().isThrownBy(() -> activo().verificarBarrio(DESPUES));
    }

    /** Verificar es probar que vive en ese barrio: al mudarse a otro, la prueba ya no vale. */
    @Test
    void mudarseDeBarrioDebeQuitarLaVerificacion() {
        Usuario mudado = vecinoActivo().verificarBarrio(AHORA).mudarDeBarrio(new SectorId("crespo"), DESPUES);

        assertThat(mudado.barrio()).isEqualTo(new SectorId("crespo"));
        assertThat(mudado.barrioVerificado()).isFalse();
        assertThat(mudado.barrioVerificadoEn()).isNull();
    }

    @Test
    void declararElMismoBarrioNoDebeQuitarLaVerificacion() {
        Usuario igual = vecinoActivo().verificarBarrio(AHORA).mudarDeBarrio(MANGA, DESPUES);

        assertThat(igual.barrioVerificado()).isTrue();
    }

    @Test
    void renombrarDebeConservarLaVerificacionDelBarrio() {
        Usuario renombrado = vecinoActivo().verificarBarrio(AHORA).renombrar("  Ana María  ", DESPUES);

        assertThat(renombrado.nombre()).isEqualTo("Ana María");
        assertThat(renombrado.barrioVerificado()).isTrue();
    }

    @Test
    void consentirAvisosDebeRegistrarLaVersionYLaFecha() {
        Usuario conAvisos = vecinoActivo().consentirAvisos("v2", DESPUES);

        assertThat(conAvisos.recibeAvisos()).isTrue();
        assertThat(conAvisos.consentimientos()).contains(
                new Consentimiento(TipoConsentimiento.AVISOS, "v2", DESPUES));
    }

    @Test
    void consentirAvisosDosVecesNoDebeDuplicarElConsentimiento() {
        Usuario conAvisos = vecinoActivo().consentirAvisos("v2", DESPUES).consentirAvisos("v3", DESPUES.plusSeconds(5));

        assertThat(conAvisos.consentimientos().stream()
                .filter(c -> c.tipo() == TipoConsentimiento.AVISOS)).hasSize(1);
        assertThat(conAvisos.consentimientos()).contains(
                new Consentimiento(TipoConsentimiento.AVISOS, "v3", DESPUES.plusSeconds(5)));
    }

    @Test
    void retirarElConsentimientoDeAvisosDebeDejarElDePrivacidad() {
        Usuario sinAvisos = vecinoActivo().consentirAvisos("v2", DESPUES)
                .retirarConsentimientoDeAvisos(DESPUES.plusSeconds(5));

        assertThat(sinAvisos.recibeAvisos()).isFalse();
        assertThat(sinAvisos.consentimientos()).extracting(Consentimiento::tipo)
                .containsExactly(TipoConsentimiento.PRIVACIDAD);
    }

    @Test
    void debeRechazarUnBarrioVerificadoSinSuFecha() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Usuario(new UsuarioId("v-9"),
                new CorreoElectronico("v@ejemplo.org"), "Vecina", HASH, EstadoCuenta.ACTIVA,
                PermisosEfectivos.deRol(RolVeedor.VECINO), null, AHORA, AHORA, MANGA,
                java.util.List.of(), true, null));
    }

    @Test
    void debeRechazarUnRegistroSinClave() {
        assertThatIllegalArgumentException().isThrownBy(() -> Usuario.registrado(
                new UsuarioId("u-1"), new CorreoElectronico("ana@ejemplo.org"), "Ana", null, AHORA));
    }

    /** El paso que hace que el registro abierto no sea una puerta: verificar no da acceso. */
    @Test
    void verificarElCorreoNoDebeActivarLaCuenta() {
        Usuario verificado = registrado().verificarCorreo(AHORA);

        assertThat(verificado.estado()).isEqualTo(EstadoCuenta.PENDIENTE_APROBACION);
        assertThat(verificado.estado().permiteIniciarSesion()).isFalse();
    }

    @Test
    void debeRechazarVerificarDosVeces() {
        Usuario verificado = registrado().verificarCorreo(AHORA);

        assertThatIllegalStateException().isThrownBy(() -> verificado.verificarCorreo(DESPUES));
    }

    @Test
    void aprobarDebeActivarLaCuentaConLosPermisosQueSeLeAsignan() {
        Usuario aprobado = registrado().verificarCorreo(AHORA)
                .aprobar(PermisosEfectivos.deRol(RolVeedor.VEEDOR), DESPUES);

        assertThat(aprobado.estado()).isEqualTo(EstadoCuenta.ACTIVA);
        assertThat(aprobado.permisosEfectivos()).contains(Permiso.MODERAR_REPORTES);
        assertThat(aprobado.actualizadoEn()).isEqualTo(DESPUES);
    }

    @Test
    void debeRechazarAprobarUnaCuentaQueNoVerificoSuCorreo() {
        Usuario sinVerificar = registrado();

        assertThatIllegalStateException().isThrownBy(
                () -> sinVerificar.aprobar(PermisosEfectivos.deRol(RolVeedor.VEEDOR), AHORA));
    }

    @Test
    void unaCuentaInvitadaNaceSinClaveYConSuRolYaDecidido() {
        Usuario invitado = Usuario.invitado(new UsuarioId("u-2"),
                new CorreoElectronico("beto@ejemplo.org"), "Beto", RolVeedor.OBSERVADOR, AHORA);

        assertThat(invitado.estado()).isEqualTo(EstadoCuenta.INVITADA);
        assertThat(invitado.claveHash()).isNull();
        assertThat(invitado.permisos().rol()).isEqualTo(RolVeedor.OBSERVADOR);
    }

    @Test
    void aceptarLaInvitacionDebeDejarLaCuentaActivaSinOtraAprobacion() {
        Usuario invitado = Usuario.invitado(new UsuarioId("u-2"),
                new CorreoElectronico("beto@ejemplo.org"), "Beto", RolVeedor.VEEDOR, AHORA);

        Usuario activo = invitado.aceptarInvitacion(HASH, DESPUES);

        assertThat(activo.estado()).isEqualTo(EstadoCuenta.ACTIVA);
        assertThat(activo.claveHash()).isEqualTo(HASH);
    }

    /** La invariante que sostiene todo lo demás: ninguna cuenta llega a ACTIVA sin clave. */
    @Test
    void debeRechazarUnaCuentaActivaSinClave() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Usuario(
                new UsuarioId("u-1"), new CorreoElectronico("ana@ejemplo.org"), "Ana", null,
                EstadoCuenta.ACTIVA, PermisosEfectivos.deRol(RolVeedor.VEEDOR), null, AHORA, AHORA));
    }

    /** Sin barrio no hay nada que verificar ni a quién avisar: un documento así no debe poder reconstruirse. */
    @Test
    void debeRechazarUnVecinoSinBarrioAunConstruidoPorElConstructorCanonico() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Usuario(
                new UsuarioId("v-1"), new CorreoElectronico("vecina@ejemplo.org"), "Vecina", HASH,
                EstadoCuenta.ACTIVA, PermisosEfectivos.deRol(RolVeedor.VECINO), null, AHORA, AHORA, null));
    }

    @Test
    void suspenderYReactivarDebenSerReversibles() {
        Usuario suspendido = activo().suspender(DESPUES);
        assertThat(suspendido.estado()).isEqualTo(EstadoCuenta.SUSPENDIDA);
        assertThat(suspendido.estado().permiteIniciarSesion()).isFalse();

        assertThat(suspendido.reactivar(DESPUES).estado()).isEqualTo(EstadoCuenta.ACTIVA);
    }

    @Test
    void debeRechazarSuspenderUnaCuentaQueNoEstaActiva() {
        assertThatIllegalStateException().isThrownBy(() -> registrado().suspender(AHORA));
    }

    /** Rechazar es para solicitudes; una cuenta que ya entró en servicio se suspende. */
    @Test
    void debeRechazarQueSeRechaceUnaCuentaYaActiva() {
        assertThatIllegalStateException().isThrownBy(() -> activo().rechazar(DESPUES));
    }

    @Test
    void rechazarDebeSerTerminal() {
        Usuario rechazado = registrado().rechazar(DESPUES);

        assertThat(rechazado.estado()).isEqualTo(EstadoCuenta.RECHAZADA);
        assertThatIllegalStateException().isThrownBy(() -> rechazado.rechazar(DESPUES));
        assertThatIllegalStateException().isThrownBy(() -> rechazado.cambiarClave(HASH, DESPUES));
    }

    @Test
    void elSegundoFactorNoDebeExigirseHastaQueSeConfirma() {
        Usuario conAltaEnCurso = activo()
                .iniciarSegundoFactor(new SecretoTotp("GEZDGNBVGY3TQOJQ"), DESPUES);

        assertThat(conAltaEnCurso.tieneSegundoFactorConfirmado()).isFalse();
        assertThat(conAltaEnCurso.confirmarSegundoFactor(DESPUES).tieneSegundoFactorConfirmado()).isTrue();
    }

    @Test
    void debeRechazarConfirmarUnSegundoFactorQueNoSeInicio() {
        assertThatIllegalStateException().isThrownBy(() -> activo().confirmarSegundoFactor(DESPUES));
    }

    /** El ADMIN entra con alcance restringido hasta que da de alta su TOTP; no se le cierra la puerta. */
    @Test
    void unAdminSinSegundoFactorDebeTenerQueCompletarSuAlta() {
        Usuario admin = activo().cambiarPermisos(PermisosEfectivos.deRol(RolVeedor.ADMIN), DESPUES);

        assertThat(admin.debeCompletarAltaDeSegundoFactor()).isTrue();

        Usuario conTotp = admin.iniciarSegundoFactor(new SecretoTotp("GEZDGNBVGY3TQOJQ"), DESPUES)
                .confirmarSegundoFactor(DESPUES);
        assertThat(conTotp.debeCompletarAltaDeSegundoFactor()).isFalse();
    }

    @Test
    void unAdminNoDebePoderDesactivarSuSegundoFactor() {
        Usuario admin = activo()
                .cambiarPermisos(PermisosEfectivos.deRol(RolVeedor.ADMIN), DESPUES)
                .iniciarSegundoFactor(new SecretoTotp("GEZDGNBVGY3TQOJQ"), DESPUES)
                .confirmarSegundoFactor(DESPUES);

        assertThatIllegalStateException().isThrownBy(() -> admin.desactivarSegundoFactor(DESPUES));
    }

    @Test
    void unVeedorSiDebePoderDesactivarSuSegundoFactor() {
        Usuario veedor = activo()
                .iniciarSegundoFactor(new SecretoTotp("GEZDGNBVGY3TQOJQ"), DESPUES)
                .confirmarSegundoFactor(DESPUES);

        assertThat(veedor.desactivarSegundoFactor(DESPUES).segundoFactor()).isNull();
    }

    @Test
    void cambiarPermisosDebeRespetarLosAjustesPorPersona() {
        Usuario recortado = activo().cambiarPermisos(new PermisosEfectivos(
                RolVeedor.VEEDOR, Set.of(), Set.of(Permiso.GESTIONAR_CORTES)), DESPUES);

        assertThat(recortado.permisosEfectivos())
                .contains(Permiso.MODERAR_REPORTES)
                .doesNotContain(Permiso.GESTIONAR_CORTES);
    }

    @Test
    void todoCambioDebeExigirUnInstante() {
        assertThatIllegalArgumentException().isThrownBy(() -> activo().suspender(null));
    }
}
