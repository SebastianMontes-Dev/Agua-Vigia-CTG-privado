package com.aguavigia.ctg.infrastructure.config;

import com.aguavigia.ctg.domain.ClaveHash;
import com.aguavigia.ctg.domain.EstadoServicio;
import com.aguavigia.ctg.domain.RolVeedor;
import com.aguavigia.ctg.domain.Sector;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.port.in.ImportarVecinosSinteticosUseCase;
import com.aguavigia.ctg.domain.port.out.CifradorClavePort;
import com.aguavigia.ctg.domain.port.out.SectorRepository;
import com.aguavigia.ctg.domain.port.out.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Hay un orden que respetar al arrancar (plan D36): el ADMIN inicial solo se crea si no existe ninguna cuenta, así que el
 * importador espera a que exista; y necesita los sectores, que siembra otro proceso. Si faltan, espera; no inventa ni se adelanta.
 */
class ImportadorDeVecinosSinteticosTest {

    private ImportarVecinosSinteticosUseCase importar;
    private UsuarioRepository usuarios;
    private SectorRepository sectores;
    private CifradorClavePort cifrador;
    private final List<Long> esperas = new ArrayList<>();

    @BeforeEach
    void montar() {
        importar = mock(ImportarVecinosSinteticosUseCase.class);
        usuarios = mock(UsuarioRepository.class);
        sectores = mock(SectorRepository.class);
        cifrador = mock(CifradorClavePort.class);
        given(cifrador.cifrar(any())).willReturn(new ClaveHash("$2a$10$compartido"));
        given(importar.importar(anyInt(), any())).willReturn(5);
    }

    private ImportadorDeVecinosSinteticos importador(int objetivo, int intentosMaximos) {
        return new ImportadorDeVecinosSinteticos(importar, usuarios, sectores, cifrador, objetivo, 211, intentosMaximos,
                esperas::add);
    }

    private static List<Sector> sectores(int cuantos) {
        return IntStream.range(0, cuantos)
                .mapToObj(i -> new Sector(new SectorId("b" + i), "B" + i, 100, EstadoServicio.CON_SERVICIO)).toList();
    }

    @Test
    void conTodoListoImportaDeInmediato() {
        given(sectores.listarTodos()).willReturn(sectores(211));
        given(usuarios.contarActivosPorRol(RolVeedor.ADMIN)).willReturn(1L);

        importador(30_000, 5).importarCuandoEsteListo();

        verify(importar).importar(eq(30_000), any());
        assertThat(esperas).isEmpty();
    }

    @Test
    void sinObjetivoNoHaceNada() {
        importador(0, 5).importarCuandoEsteListo();

        verify(importar, never()).importar(anyInt(), any());
    }

    /** Si corriera antes que el ADMIN inicial, ese ADMIN nunca se crearía (solo nace en un sistema sin cuentas). */
    @Test
    void esperaAQueExistaElAdminInicialYLosSectores() {
        given(sectores.listarTodos()).willReturn(sectores(0), sectores(100), sectores(211));
        given(usuarios.contarActivosPorRol(RolVeedor.ADMIN)).willReturn(0L, 0L, 1L);

        importador(30_000, 10).importarCuandoEsteListo();

        verify(importar).importar(eq(30_000), any());
        assertThat(esperas).hasSize(2);
    }

    @Test
    void siFaltanLosSectoresDespuesDeEsperarNoImporta() {
        given(sectores.listarTodos()).willReturn(sectores(10));
        given(usuarios.contarActivosPorRol(RolVeedor.ADMIN)).willReturn(1L);

        importador(30_000, 3).importarCuandoEsteListo();

        verify(importar, never()).importar(anyInt(), any());
        assertThat(esperas).hasSize(3);
    }

    /** Sin ADMIN configurado el panel queda sin acceso, pero eso no debe dejar al sistema sin sus cuentas de volumen. */
    @Test
    void siNuncaHayAdminImportaIgualDespuesDeEsperar() {
        given(sectores.listarTodos()).willReturn(sectores(211));
        given(usuarios.contarActivosPorRol(RolVeedor.ADMIN)).willReturn(0L);

        importador(30_000, 2).importarCuandoEsteListo();

        verify(importar).importar(eq(30_000), any());
        assertThat(esperas).hasSize(2);
    }

    @Test
    void unaBaseCaidaNoTumbaElArranque() {
        given(sectores.listarTodos()).willThrow(new DataAccessResourceFailureException("sin Mongo"));

        importador(30_000, 2).importarCuandoEsteListo();

        verify(importar, never()).importar(anyInt(), any());
    }

    /** La clave compartida se calcula una vez (un solo BCrypt) y nadie conoce su contraseña: ninguna cuenta se puede abrir. */
    @Test
    void calculaUnaSolaClaveCompartida() {
        given(sectores.listarTodos()).willReturn(sectores(211));
        given(usuarios.contarActivosPorRol(RolVeedor.ADMIN)).willReturn(1L);

        importador(30_000, 5).importarCuandoEsteListo();

        verify(cifrador, org.mockito.Mockito.times(1)).cifrar(any());
    }
}
