package com.aguavigia.ctg.infrastructure.config;

import com.aguavigia.ctg.application.RegistroDeAuditoria;
import com.aguavigia.ctg.domain.AccionAuditada;
import com.aguavigia.ctg.domain.EstadoCuenta;
import com.aguavigia.ctg.domain.Pagina;
import com.aguavigia.ctg.domain.RolVeedor;
import com.aguavigia.ctg.domain.Usuario;
import com.aguavigia.ctg.domain.port.out.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class SembradorAdminInicialTest {

    private static final Instant AHORA = Instant.parse("2026-09-29T12:00:00Z");
    private static final String HASH = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private UsuarioRepository usuarios;
    private RegistroDeAuditoria auditoria;

    @BeforeEach
    void montar() {
        usuarios = mock(UsuarioRepository.class);
        auditoria = mock(RegistroDeAuditoria.class);
        given(usuarios.guardar(any())).willAnswer(invocacion -> invocacion.getArgument(0));
    }

    private SembradorAdminInicial sembrador(String correo, String hash) {
        return new SembradorAdminInicial(usuarios, auditoria, () -> AHORA, correo, hash);
    }

    private void sistemaConCuentas(long total) {
        given(usuarios.listar(isNull(), isNull(), eq(0), eq(1))).willReturn(new Pagina<>(List.of(), 0, 1, total));
    }

    @Test
    void sinCorreoOSinHashNoDebeSembrarNiTocarLaBase() {
        sembrador("", HASH).sembrarSiNoHayNadie();
        sembrador("admin@aguavigia.local", "").sembrarSiNoHayNadie();

        verifyNoInteractions(usuarios, auditoria);
    }

    @Test
    void conUnaCuentaExistenteDeCualquierEstadoNoDebeSembrarOtraVez() {
        sistemaConCuentas(1);

        sembrador("admin@aguavigia.local", HASH).sembrarSiNoHayNadie();

        verify(usuarios, never()).guardar(any());
        verifyNoInteractions(auditoria);
    }

    @Test
    void enUnSistemaSinCuentasDebeCrearElAdminActivoYAuditarlo() {
        sistemaConCuentas(0);

        sembrador("Admin@AguaVigia.Local", HASH).sembrarSiNoHayNadie();

        ArgumentCaptor<Usuario> guardado = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios).guardar(guardado.capture());
        Usuario admin = guardado.getValue();
        assertThat(admin.correo().valor()).isEqualTo("admin@aguavigia.local");
        assertThat(admin.estado()).isEqualTo(EstadoCuenta.ACTIVA);
        assertThat(admin.permisos().rol()).isEqualTo(RolVeedor.ADMIN);
        assertThat(admin.claveHash().valor()).isEqualTo(HASH);
        assertThat(admin.creadoEn()).isEqualTo(AHORA);
        verify(auditoria).registrarConAutor(eq(AccionAuditada.CUENTA_APROBADA), isNull(), eq(admin), any(), any());
    }

    /** El ADMIN exige segundo factor: hasta darlo de alta, su sesión solo sirve para eso. */
    @Test
    void elAdminSembradoDebeNacerObligadoADarDeAltaSuSegundoFactor() {
        sistemaConCuentas(0);

        sembrador("admin@aguavigia.local", HASH).sembrarSiNoHayNadie();

        ArgumentCaptor<Usuario> guardado = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios).guardar(guardado.capture());
        assertThat(guardado.getValue().tieneSegundoFactorConfirmado()).isFalse();
        assertThat(guardado.getValue().permisos().rol().exigeSegundoFactor()).isTrue();
    }

    @Test
    void sinMongoNoDebeTumbarElArranque() {
        given(usuarios.listar(any(), any(), any(Integer.class), any(Integer.class)))
                .willThrow(new DataAccessResourceFailureException("Mongo caído"));

        assertThatCode(() -> sembrador("admin@aguavigia.local", HASH).sembrarSiNoHayNadie()).doesNotThrowAnyException();
        verify(usuarios, never()).guardar(any());
    }

    @Test
    void unCorreoInvalidoNoDebeTumbarElArranque() {
        sistemaConCuentas(0);

        assertThatCode(() -> sembrador("esto-no-es-un-correo", HASH).sembrarSiNoHayNadie()).doesNotThrowAnyException();
        verify(usuarios, never()).guardar(any());
    }
}
