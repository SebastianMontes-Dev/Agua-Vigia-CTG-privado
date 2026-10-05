package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.AccionAuditada;
import com.aguavigia.ctg.domain.ClaveHash;
import com.aguavigia.ctg.domain.EstadoCuenta;
import com.aguavigia.ctg.domain.EstadoServicio;
import com.aguavigia.ctg.domain.Sector;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.Usuario;
import com.aguavigia.ctg.domain.port.out.SectorRepository;
import com.aguavigia.ctg.domain.port.out.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Las cuentas sintéticas (D20, D36): lo único inventado del sistema, y se dice. Las crea el sistema con las reglas de alta de
 * un vecino (barrio del catálogo, correo único, esquema), sin enviar correo, sin consentimiento ni verificación de barrio.
 */
class ImportarVecinosSinteticosServiceTest {

    private static final Instant AHORA = Instant.parse("2026-10-02T08:00:00Z");
    private static final ClaveHash CLAVE = new ClaveHash("$2a$10$hash-compartido");

    private UsuarioRepository usuarios;
    private SectorRepository sectores;
    private RegistroDeAuditoria auditoria;
    private ImportarVecinosSinteticosService servicio;
    private final List<Usuario> insertados = new ArrayList<>();

    @BeforeEach
    void montar() {
        usuarios = mock(UsuarioRepository.class);
        sectores = mock(SectorRepository.class);
        auditoria = mock(RegistroDeAuditoria.class);
        servicio = new ImportarVecinosSinteticosService(usuarios, sectores, auditoria, () -> AHORA, 100);

        given(sectores.listarTodos()).willReturn(List.of(
                new Sector(new SectorId("grande"), "Grande", 90_000, EstadoServicio.CON_SERVICIO),
                new Sector(new SectorId("pequeno"), "Pequeño", 10_000, EstadoServicio.CON_SERVICIO),
                new Sector(new SectorId("sin-censo"), "Sin censo", null, EstadoServicio.CON_SERVICIO)));
        given(usuarios.contarSinteticas()).willReturn(0L);
        given(usuarios.insertarSinteticasSiNoExisten(any())).willAnswer(invocacion -> {
            List<Usuario> lote = invocacion.getArgument(0);
            insertados.addAll(lote);
            return lote.size();
        });
    }

    @Test
    void creaLasCuentasQueFaltanHastaElObjetivo() {
        int creadas = servicio.importar(250, CLAVE);

        assertThat(creadas).isEqualTo(250);
        assertThat(insertados).hasSize(250);
    }

    @Test
    void cadaCuentaNaceActivaConLaClaveCompartidaYSinFingirNada() {
        servicio.importar(10, CLAVE);

        assertThat(insertados).allSatisfy(u -> {
            assertThat(u.estado()).isEqualTo(EstadoCuenta.ACTIVA);
            assertThat(u.esVecino()).isTrue();
            assertThat(u.datosDeDemostracion()).isTrue();
            assertThat(u.claveHash()).isEqualTo(CLAVE);
            assertThat(u.consentimientos()).isEmpty();
            assertThat(u.barrioVerificado()).isFalse();
            assertThat(u.creadoEn()).isEqualTo(AHORA);
            // Dominio reservado: nada que se parezca a un correo real, ni entregable.
            assertThat(u.correo().valor()).endsWith(".invalid");
        });
    }

    /** Dos pasadas no duplican nada: la segunda solo completa lo que falta. */
    @Test
    void esIdempotenteYCompletaDesdeDondeQuedo() {
        given(usuarios.contarSinteticas()).willReturn(240L);
        siSoloEntranLasMayoresDe(240);

        int creadas = servicio.importar(250, CLAVE);

        assertThat(creadas).isEqualTo(10);
        // Las que faltan son la 241 a la 250: mismos correos que las de siempre, no unos nuevos.
        assertThat(insertados.get(0).correo().valor()).contains("000241");
        assertThat(insertados.get(9).correo().valor()).contains("000250");
    }

    /**
     * Una pasada que quedó a medias puede dejar huecos en la numeración (un lote desordenado cortado por un fallo): empezar después del
     * último conteo los dejaría para siempre. Se recorre desde la primera y la base ignora las que ya existen, que son las mismas.
     */
    @Test
    void sePoneAlDiaAunqueQuedaranHuecosEnLaNumeracion() {
        given(usuarios.contarSinteticas()).willReturn(240L);
        // Existen 240, pero la 7 y la 100 nunca llegaron a entrar.
        doAnswer(invocacion -> {
            List<Usuario> lote = invocacion.getArgument(0);
            List<Usuario> nuevas = lote.stream().filter(u -> {
                int numero = Integer.parseInt(u.correo().valor().replaceAll("\\D+", "").substring(0, 6));
                return numero > 240 || numero == 7 || numero == 100;
            }).toList();
            insertados.addAll(nuevas);
            return nuevas.size();
        }).when(usuarios).insertarSinteticasSiNoExisten(any());

        assertThat(servicio.importar(250, CLAVE)).isEqualTo(12);
        assertThat(insertados.stream().map(u -> u.correo().valor())).anyMatch(c -> c.contains("000007")).anyMatch(c -> c.contains("000100"));
    }

    /** Un lote en el que no entró nada no es una activación: no se anota. */
    @Test
    void noAuditaUnLoteEnElQueNoSeInsertoNada() {
        given(usuarios.contarSinteticas()).willReturn(10L);
        siSoloEntranLasMayoresDe(10_000);

        assertThat(servicio.importar(250, CLAVE)).isZero();
        verify(auditoria, never()).registrar(any(), any(), any(), any());
    }

    private void siSoloEntranLasMayoresDe(int existentes) {
        // doAnswer y no given(...).willAnswer: re-stubbear con given() invoca la respuesta anterior con un argumento nulo.
        doAnswer(invocacion -> {
            List<Usuario> lote = invocacion.getArgument(0);
            List<Usuario> nuevas = lote.stream()
                    .filter(u -> Integer.parseInt(u.correo().valor().replaceAll("\\D+", "").substring(0, 6)) > existentes).toList();
            insertados.addAll(nuevas);
            return nuevas.size();
        }).when(usuarios).insertarSinteticasSiNoExisten(any());
    }

    @Test
    void siYaHayLasSuficientesNoHaceNada() {
        given(usuarios.contarSinteticas()).willReturn(30_000L);

        assertThat(servicio.importar(30_000, CLAVE)).isZero();
        verify(usuarios, never()).insertarSinteticasSiNoExisten(any());
    }

    @Test
    void sinSectoresNoPuedeAsignarBarrioYNoCreaNada() {
        given(sectores.listarTodos()).willReturn(List.of());

        assertThat(servicio.importar(10, CLAVE)).isZero();
        verify(usuarios, never()).insertarSinteticasSiNoExisten(any());
    }

    @Test
    void unObjetivoNoPositivoNoHaceNada() {
        assertThat(servicio.importar(0, CLAVE)).isZero();
        assertThat(servicio.importar(-5, CLAVE)).isZero();
    }

    /** «Barrio repartido por población»: el barrio de 90 000 recibe nueve veces más cuentas que el de 10 000. */
    @Test
    void repartePorPoblacion() {
        servicio.importar(3000, CLAVE);

        Map<String, Long> porBarrio = insertados.stream()
                .collect(Collectors.groupingBy(u -> u.barrio().valor(), Collectors.counting()));
        assertThat(porBarrio.get("grande")).isBetween(2400L, 2800L);
        assertThat(porBarrio.get("pequeno")).isBetween(200L, 400L);
        // Sin dato censal pesa lo mínimo, pero existe: no se queda sin cuentas ni se rompe.
        assertThat(porBarrio.getOrDefault("sin-censo", 0L)).isLessThan(50L);
    }

    /** Mismo resultado en cada arranque: el reparto no debe cambiar si se vuelve a levantar. */
    @Test
    void elRepartoEsDeterminista() {
        servicio.importar(200, CLAVE);
        List<String> primera = insertados.stream().map(u -> u.correo().valor() + "@" + u.barrio().valor()).toList();
        insertados.clear();

        servicio.importar(200, CLAVE);

        assertThat(insertados.stream().map(u -> u.correo().valor() + "@" + u.barrio().valor()).toList())
                .isEqualTo(primera);
    }

    @Test
    void losCorreosYLosIdsNoSeRepiten() {
        servicio.importar(500, CLAVE);

        assertThat(insertados.stream().map(u -> u.correo().valor()).distinct()).hasSize(500);
        assertThat(insertados.stream().map(u -> u.id().valor()).distinct()).hasSize(500);
    }

    /** La auditoría de cada activación por sistema: un evento por lote, no 30 000 (cada evento sería una lectura más). */
    @Test
    void auditaLaActivacionPorSistemaUnaVezPorLote() {
        servicio.importar(250, CLAVE);

        ArgumentCaptor<String> detalle = ArgumentCaptor.forClass(String.class);
        verify(auditoria, times(3)).registrar(eq(AccionAuditada.CUENTA_SINTETICA_ACTIVADA), isNull(), detalle.capture(), any());
        assertThat(detalle.getAllValues().get(0)).contains("100").contains("000001").contains("000100");
        assertThat(detalle.getAllValues().get(2)).contains("50");
    }

    /** Si algunas ya existían (otra instancia, una pasada a medias), se cuentan solo las que se crearon de verdad. */
    @Test
    void cuentaSoloLasQueSeInsertaronDeVerdad() {
        org.mockito.Mockito.doReturn(40).when(usuarios).insertarSinteticasSiNoExisten(any());

        assertThat(servicio.importar(100, CLAVE)).isEqualTo(40);
    }
}
