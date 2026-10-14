package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.AccionAuditada;
import com.aguavigia.ctg.domain.ClaveHash;
import com.aguavigia.ctg.domain.Consentimiento;
import com.aguavigia.ctg.domain.ContextoDeAccion;
import com.aguavigia.ctg.domain.Coordenada;
import com.aguavigia.ctg.domain.CorreoElectronico;
import com.aguavigia.ctg.domain.EstadoCuenta;
import com.aguavigia.ctg.domain.LimiteDePeticionesExcedidoException;
import com.aguavigia.ctg.domain.PermisosEfectivos;
import com.aguavigia.ctg.domain.ResultadoDeCupo;
import com.aguavigia.ctg.domain.RolVeedor;
import com.aguavigia.ctg.domain.Sector;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.SesionSinCuentaException;
import com.aguavigia.ctg.domain.TipoConsentimiento;
import com.aguavigia.ctg.domain.UbicacionFueraDelBarrioException;
import com.aguavigia.ctg.domain.UbicacionImprecisaException;
import com.aguavigia.ctg.domain.Usuario;
import com.aguavigia.ctg.domain.UsuarioId;
import com.aguavigia.ctg.domain.port.out.CupoPorCuentaPort;
import com.aguavigia.ctg.domain.port.out.SectorRepository;
import com.aguavigia.ctg.domain.port.out.UsuarioRepository;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * La coordenada viaja solo en la llamada: se compara con el polígono y se descarta. Estas pruebas
 * fijan lo que sí queda (el barrio verificado y su fecha) y lo que nunca debe quedar.
 */
class VerificarBarrioVecinoServiceTest {

    private static final Instant ANTES = Instant.parse("2026-10-01T15:00:00Z");
    private static final Instant AHORA = Instant.parse("2026-10-02T09:00:00Z");
    private static final ClaveHash HASH =
            new ClaveHash("$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy");
    private static final SectorId MANGA = new SectorId("manga");
    private static final SectorId CRESPO = new SectorId("crespo");
    private static final UsuarioId ID = new UsuarioId("v-1");
    private static final ContextoDeAccion CONTEXTO = new ContextoDeAccion(ID, "10.0.0.1");
    private static final Coordenada EN_MANGA = new Coordenada(10.41234567, -75.54321098);

    private UsuarioRepository usuarios;
    private SectorRepository sectores;
    private CupoPorCuentaPort cupo;
    private RegistroDeAuditoria auditoria;
    private VerificarBarrioVecinoService servicio;

    @BeforeEach
    void montar() {
        usuarios = mock(UsuarioRepository.class);
        sectores = mock(SectorRepository.class);
        cupo = mock(CupoPorCuentaPort.class);
        auditoria = mock(RegistroDeAuditoria.class);
        given(usuarios.guardarSiNoCambio(any(), any())).willAnswer(invocacion -> invocacion.getArgument(0));
        given(cupo.consumir(anyString(), org.mockito.ArgumentMatchers.anyInt(), any())).willReturn(ResultadoDeCupo.conCupo());
        given(sectores.buscarPorCoordenada(EN_MANGA))
                .willReturn(Optional.of(new Sector(MANGA, "Manga", 10_000, null)));
        servicio = new VerificarBarrioVecinoService(usuarios, sectores, cupo, auditoria, () -> AHORA, 3, 200.0);
    }

    private static Usuario vecino(boolean yaVerificado) {
        Usuario base = Usuario.registradoComoVecino(ID, new CorreoElectronico("vecina@ejemplo.org"), "Vecina",
                MANGA, List.of(new Consentimiento(TipoConsentimiento.PRIVACIDAD, "v1", ANTES)), ANTES)
                .aceptarInvitacion(HASH, ANTES);
        return yaVerificado ? base.verificarBarrio(ANTES) : base;
    }

    private void existe(Usuario usuario) {
        given(usuarios.buscarPorId(ID)).willReturn(Optional.of(usuario));
    }

    @Test
    void debeVerificarElBarrioCuandoLaCoordenadaCaeEnElBarrioDeclarado() {
        existe(vecino(false));

        Usuario verificado = servicio.verificar(ID, EN_MANGA, 25.0, CONTEXTO);

        assertThat(verificado.barrioVerificado()).isTrue();
        assertThat(verificado.barrioVerificadoEn()).isEqualTo(AHORA);
        ArgumentCaptor<Usuario> guardado = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios).guardarSiNoCambio(guardado.capture(), eq(ANTES));
        assertThat(guardado.getValue().barrioVerificado()).isTrue();
    }

    /** Una suspensión (o un cambio de barrio) escrito entre la lectura y el guardado no se pisa. */
    @Test
    void siLaCuentaCambioMientrasSeGuardabaNoDebeTragarseElConflicto() {
        existe(vecino(false));
        given(usuarios.guardarSiNoCambio(any(), any())).willThrow(new IllegalStateException("La cuenta cambió"));

        assertThatThrownBy(() -> servicio.verificar(ID, EN_MANGA, 25.0, CONTEXTO))
                .isInstanceOf(IllegalStateException.class);
        verify(auditoria, never()).registrarConAutor(any(), any(), any(), anyString(), any());
    }

    @Test
    void unaCoordenadaFueraDeTodoBarrioNoDebeVerificar() {
        existe(vecino(false));
        Coordenada fuera = new Coordenada(4.60, -74.08);
        given(sectores.buscarPorCoordenada(fuera)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.verificar(ID, fuera, 25.0, CONTEXTO))
                .isInstanceOf(UbicacionFueraDelBarrioException.class);
        verify(usuarios, never()).guardarSiNoCambio(any(), any());
    }

    @Test
    void unaCoordenadaEnOtroBarrioNoDebeVerificar() {
        existe(vecino(false));
        Coordenada enCrespo = new Coordenada(10.45, -75.51);
        given(sectores.buscarPorCoordenada(enCrespo))
                .willReturn(Optional.of(new Sector(CRESPO, "Crespo", 5_000, null)));

        assertThatThrownBy(() -> servicio.verificar(ID, enCrespo, 25.0, CONTEXTO))
                .isInstanceOf(UbicacionFueraDelBarrioException.class);
        verify(usuarios, never()).guardarSiNoCambio(any(), any());
    }

    /** Una ubicación aproximada por red no prueba nada: se rechaza sin consultar el barrio ni gastar un intento. */
    @Test
    void unaPrecisionPeorQueElLimiteNoDebeVerificarNiGastarUnIntento() {
        existe(vecino(false));

        assertThatThrownBy(() -> servicio.verificar(ID, EN_MANGA, 200.1, CONTEXTO))
                .isInstanceOf(UbicacionImprecisaException.class);

        verifyNoInteractions(cupo, sectores);
        verify(usuarios, never()).guardarSiNoCambio(any(), any());
    }

    @Test
    void unaPrecisionExactamenteEnElLimiteSiDebeVerificar() {
        existe(vecino(false));

        assertThat(servicio.verificar(ID, EN_MANGA, 200.0, CONTEXTO).barrioVerificado()).isTrue();
    }

    @Test
    void unaPrecisionNegativaONoNumericaDebeRechazarseComoPeticionInvalida() {
        existe(vecino(false));

        assertThatThrownBy(() -> servicio.verificar(ID, EN_MANGA, -1.0, CONTEXTO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> servicio.verificar(ID, EN_MANGA, Double.NaN, CONTEXTO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void debeConsumirUnIntentoDeLaCuentaPorDiaAntesDeMirarLaUbicacion() {
        existe(vecino(false));

        servicio.verificar(ID, EN_MANGA, 25.0, CONTEXTO);

        verify(cupo).consumir(contains(ID.valor()), eq(3), eq(Duration.ofDays(1)));
    }

    @Test
    void unIntentoFallidoTambienDebeGastarElCupo() {
        existe(vecino(false));
        Coordenada fuera = new Coordenada(4.60, -74.08);
        given(sectores.buscarPorCoordenada(fuera)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.verificar(ID, fuera, 25.0, CONTEXTO))
                .isInstanceOf(UbicacionFueraDelBarrioException.class);

        verify(cupo).consumir(anyString(), eq(3), eq(Duration.ofDays(1)));
    }

    @Test
    void conElCupoAgotadoDebeRechazarConElTiempoParaReintentarSinMirarLaUbicacion() {
        existe(vecino(false));
        given(cupo.consumir(anyString(), org.mockito.ArgumentMatchers.anyInt(), any()))
                .willReturn(ResultadoDeCupo.agotado(Duration.ofHours(5)));

        assertThatThrownBy(() -> servicio.verificar(ID, EN_MANGA, 25.0, CONTEXTO))
                .isInstanceOfSatisfying(LimiteDePeticionesExcedidoException.class,
                        e -> assertThat(e.segundosParaReintentar()).isEqualTo(5 * 3600));

        verifyNoInteractions(sectores);
        verify(usuarios, never()).guardarSiNoCambio(any(), any());
    }

    @Test
    void unBarrioYaVerificadoNoDebeGastarCupoNiMirarLaUbicacion() {
        existe(vecino(true));

        Usuario devuelto = servicio.verificar(ID, EN_MANGA, 25.0, CONTEXTO);

        assertThat(devuelto.barrioVerificado()).isTrue();
        verifyNoInteractions(cupo, sectores);
        verify(usuarios, never()).guardarSiNoCambio(any(), any());
    }

    /** La auditoría dice que se verificó, nunca desde dónde: la coordenada no se guarda en ningún sitio. */
    @Test
    void laAuditoriaNoDebeLlevarLaCoordenada() {
        existe(vecino(false));

        servicio.verificar(ID, EN_MANGA, 25.0, CONTEXTO);

        ArgumentCaptor<String> detalle = ArgumentCaptor.forClass(String.class);
        verify(auditoria).registrarConAutor(eq(AccionAuditada.BARRIO_VERIFICADO), any(), any(),
                detalle.capture(), eq(CONTEXTO));
        assertThat(detalle.getValue()).doesNotContain("10.41").doesNotContain("75.54");
    }

    @Test
    void debeFallarSiLaSesionYaNoCorrespondeAUnaCuenta() {
        given(usuarios.buscarPorId(ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.verificar(ID, EN_MANGA, 25.0, CONTEXTO))
                .isInstanceOf(SesionSinCuentaException.class);
    }

    @Test
    void debeRechazarLaVerificacionDeUnaCuentaQueNoEsDeVecino() {
        existe(new Usuario(ID, new CorreoElectronico("v@ejemplo.org"), "Veedora", HASH, EstadoCuenta.ACTIVA,
                PermisosEfectivos.deRol(RolVeedor.VEEDOR), null, ANTES, ANTES));

        assertThatThrownBy(() -> servicio.verificar(ID, EN_MANGA, 25.0, CONTEXTO))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(cupo, sectores);
    }

    private static String contains(String texto) {
        return org.mockito.ArgumentMatchers.contains(texto);
    }
}
