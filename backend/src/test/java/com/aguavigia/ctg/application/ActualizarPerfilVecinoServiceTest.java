package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.AccionAuditada;
import com.aguavigia.ctg.domain.CambiosDePerfil;
import com.aguavigia.ctg.domain.ClaveHash;
import com.aguavigia.ctg.domain.Consentimiento;
import com.aguavigia.ctg.domain.ContextoDeAccion;
import com.aguavigia.ctg.domain.CorreoElectronico;
import com.aguavigia.ctg.domain.EstadoCuenta;
import com.aguavigia.ctg.domain.PermisosEfectivos;
import com.aguavigia.ctg.domain.RolVeedor;
import com.aguavigia.ctg.domain.Sector;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.SesionSinCuentaException;
import com.aguavigia.ctg.domain.TipoConsentimiento;
import com.aguavigia.ctg.domain.Usuario;
import com.aguavigia.ctg.domain.UsuarioId;
import com.aguavigia.ctg.domain.port.out.SectorRepository;
import com.aguavigia.ctg.domain.port.out.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
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

class ActualizarPerfilVecinoServiceTest {

    private static final Instant ANTES = Instant.parse("2026-10-01T15:00:00Z");
    private static final Instant AHORA = Instant.parse("2026-10-02T09:00:00Z");
    private static final ClaveHash HASH =
            new ClaveHash("$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy");
    private static final SectorId MANGA = new SectorId("manga");
    private static final SectorId CRESPO = new SectorId("crespo");
    private static final UsuarioId ID = new UsuarioId("v-1");
    private static final ContextoDeAccion CONTEXTO = new ContextoDeAccion(ID, "10.0.0.1");

    private UsuarioRepository usuarios;
    private SectorRepository sectores;
    private RegistroDeAuditoria auditoria;
    private ActualizarPerfilVecinoService servicio;

    @BeforeEach
    void montar() {
        usuarios = mock(UsuarioRepository.class);
        sectores = mock(SectorRepository.class);
        auditoria = mock(RegistroDeAuditoria.class);
        given(usuarios.guardarSiNoCambio(any(), any())).willAnswer(invocacion -> invocacion.getArgument(0));
        given(sectores.buscarPorId(CRESPO)).willReturn(Optional.of(new Sector(CRESPO, "Crespo", 5000, null)));
        servicio = new ActualizarPerfilVecinoService(usuarios, auditoria, () -> AHORA, sectores, "2026-10-v2");
    }

    private Usuario vecino(boolean conAvisos, boolean barrioVerificado) {
        Usuario base = Usuario.registradoComoVecino(ID, new CorreoElectronico("vecina@ejemplo.org"), "Vecina",
                MANGA, List.of(new Consentimiento(TipoConsentimiento.PRIVACIDAD, "2026-10-v1", ANTES)), ANTES)
                .aceptarInvitacion(HASH, ANTES);
        if (conAvisos) {
            base = base.consentirAvisos("2026-10-v1", ANTES);
        }
        return barrioVerificado ? base.verificarBarrio(ANTES) : base;
    }

    private void existe(Usuario usuario) {
        given(usuarios.buscarPorId(ID)).willReturn(Optional.of(usuario));
    }

    private Usuario guardado() {
        ArgumentCaptor<Usuario> guardado = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios).guardarSiNoCambio(guardado.capture(), eq(ANTES));
        return guardado.getValue();
    }

    /** Si otro escribió la cuenta mientras tanto (una suspensión, por ejemplo), no se pisa: el 409 sube al cliente. */
    @Test
    void siLaCuentaCambioMientrasSeGuardabaNoDebeTragarseElConflicto() {
        existe(vecino(false, false));
        given(usuarios.guardarSiNoCambio(any(), any())).willThrow(new IllegalStateException("La cuenta cambió"));

        assertThatThrownBy(() -> servicio.actualizar(ID, new CambiosDePerfil("Ana María", null, null), CONTEXTO))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void debeCambiarElNombre() {
        existe(vecino(false, false));

        servicio.actualizar(ID, new CambiosDePerfil("Ana María", null, null), CONTEXTO);

        assertThat(guardado().nombre()).isEqualTo("Ana María");
    }

    @Test
    void debeCambiarElBarrioYQuitarLaVerificacion() {
        existe(vecino(false, true));

        servicio.actualizar(ID, new CambiosDePerfil(null, CRESPO, null), CONTEXTO);

        assertThat(guardado().barrio()).isEqualTo(CRESPO);
        assertThat(guardado().barrioVerificado()).isFalse();
    }

    @Test
    void debeRechazarUnBarrioInexistente() {
        existe(vecino(false, false));

        assertThatThrownBy(() -> servicio.actualizar(ID,
                new CambiosDePerfil(null, new SectorId("no-existe"), null), CONTEXTO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No existe el barrio");
        verify(usuarios, never()).guardarSiNoCambio(any(), any());
    }

    @Test
    void marcarLosAvisosDebeRegistrarElConsentimientoConLaVersionVigente() {
        existe(vecino(false, false));

        servicio.actualizar(ID, new CambiosDePerfil(null, null, true), CONTEXTO);

        assertThat(guardado().consentimientos()).contains(
                new Consentimiento(TipoConsentimiento.AVISOS, "2026-10-v2", AHORA));
    }

    @Test
    void desmarcarLosAvisosDebeRetirarElConsentimientoYConservarElDePrivacidad() {
        existe(vecino(true, false));

        servicio.actualizar(ID, new CambiosDePerfil(null, null, false), CONTEXTO);

        assertThat(guardado().recibeAvisos()).isFalse();
        assertThat(guardado().consentimientos()).extracting(Consentimiento::tipo)
                .containsExactly(TipoConsentimiento.PRIVACIDAD);
    }

    /** Marcar lo que ya estaba marcado no debe cambiar la fecha ni la versión que la persona aceptó. */
    @Test
    void marcarLosAvisosYaMarcadosNoDebeRehacerElConsentimiento() {
        existe(vecino(true, false));

        Usuario devuelto = servicio.actualizar(ID, new CambiosDePerfil(null, null, true), CONTEXTO);

        assertThat(devuelto.consentimientos()).contains(
                new Consentimiento(TipoConsentimiento.AVISOS, "2026-10-v1", ANTES));
        verify(usuarios, never()).guardarSiNoCambio(any(), any());
    }

    @Test
    void unaPeticionSinCambiosNoDebeGuardarNiAuditar() {
        existe(vecino(false, false));

        Usuario devuelto = servicio.actualizar(ID, new CambiosDePerfil(null, null, null), CONTEXTO);

        assertThat(devuelto.nombre()).isEqualTo("Vecina");
        verify(usuarios, never()).guardarSiNoCambio(any(), any());
        verify(auditoria, never()).registrarConAutor(any(), any(), any(), anyString(), any());
    }

    @Test
    void debeAuditarQueCambioSinGuardarElNombreNuevo() {
        existe(vecino(false, true));

        servicio.actualizar(ID, new CambiosDePerfil("Nombre Privado Nuevo", CRESPO, true), CONTEXTO);

        ArgumentCaptor<String> detalle = ArgumentCaptor.forClass(String.class);
        verify(auditoria).registrarConAutor(eq(AccionAuditada.PERFIL_ACTUALIZADO), any(), any(),
                detalle.capture(), eq(CONTEXTO));
        assertThat(detalle.getValue()).contains("nombre", "barrio", "avisos")
                .doesNotContain("Nombre Privado Nuevo");
    }

    @Test
    void debeFallarSiLaSesionYaNoCorrespondeAUnaCuenta() {
        given(usuarios.buscarPorId(ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.actualizar(ID, new CambiosDePerfil("X", null, null), CONTEXTO))
                .isInstanceOf(SesionSinCuentaException.class);
    }

    @Test
    void debeRechazarActualizarElPerfilDeUnaCuentaQueNoEsDeVecino() {
        existe(new Usuario(ID, new CorreoElectronico("v@ejemplo.org"), "Veedora", HASH, EstadoCuenta.ACTIVA,
                PermisosEfectivos.deRol(RolVeedor.VEEDOR), null, ANTES, ANTES));

        assertThatThrownBy(() -> servicio.actualizar(ID, new CambiosDePerfil("X", null, null), CONTEXTO))
                .isInstanceOf(IllegalStateException.class);
        verify(usuarios, never()).guardarSiNoCambio(any(), any());
    }
}
