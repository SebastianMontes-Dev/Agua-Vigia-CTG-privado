package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.AnaliticaNoDisponibleException;
import com.aguavigia.ctg.domain.CierreDeCorte;
import com.aguavigia.ctg.domain.ConteosAnaliticos;
import com.aguavigia.ctg.domain.CorteAgua;
import com.aguavigia.ctg.domain.CorteId;
import com.aguavigia.ctg.domain.EstadoCorte;
import com.aguavigia.ctg.domain.FilaCorteAnalitica;
import com.aguavigia.ctg.domain.FilaParAnalitica;
import com.aguavigia.ctg.domain.FilaSectorAnalitica;
import com.aguavigia.ctg.domain.OrigenCorte;
import com.aguavigia.ctg.domain.OrigenEstado;
import com.aguavigia.ctg.domain.ResultadoDeCopia;
import com.aguavigia.ctg.domain.Sector;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.port.out.AlmacenAnaliticoPort;
import com.aguavigia.ctg.domain.port.out.CorteAguaRepository;
import com.aguavigia.ctg.domain.port.out.RelojPort;
import com.aguavigia.ctg.domain.port.out.SectorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class CopiarAnaliticaServiceTest {

    private static final Instant AHORA = Instant.parse("2026-10-15T15:00:00Z");
    private static final Instant INICIO = Instant.parse("2026-10-14T10:00:00Z");

    private SectorRepository sectores;
    private CorteAguaRepository cortes;
    private AlmacenAnaliticoPort almacen;
    private CopiarAnaliticaService servicio;

    @BeforeEach
    void preparar() {
        sectores = mock(SectorRepository.class);
        cortes = mock(CorteAguaRepository.class);
        almacen = mock(AlmacenAnaliticoPort.class);
        RelojPort reloj = () -> AHORA;
        servicio = new CopiarAnaliticaService(sectores, cortes, almacen, reloj);
        when(almacen.registrarInicioDeCopia(any())).thenReturn(7L);
    }

    private static Sector barrio(String id, Integer poblacion) {
        return new Sector(new SectorId(id), "Barrio " + id, poblacion, null);
    }

    private static CorteAgua.Builder corte(String id, EstadoCorte estado, String... barrios) {
        return CorteAgua.builder()
                .id(new CorteId(id))
                .sectoresAfectados(java.util.Arrays.stream(barrios).map(SectorId::new).toList())
                .inicio(INICIO)
                .finPrometido(INICIO.plus(2, ChronoUnit.HOURS))
                .causa("Mantenimiento")
                .origen(OrigenCorte.OFICIAL_ACUACAR)
                .estado(estado);
    }

    @SuppressWarnings("unchecked")
    private List<FilaCorteAnalitica> cortesCopiados() {
        ArgumentCaptor<List<FilaCorteAnalitica>> captor = ArgumentCaptor.forClass(List.class);
        verify(almacen).reemplazar(any(), captor.capture(), eq(AHORA));
        return captor.getValue();
    }

    @Test
    void debeCopiarBarriosCortesYParesYCerrarLaCopiaComoOkSiLosConteosCuadran() {
        when(sectores.listarTodos()).thenReturn(List.of(barrio("manga", 1000), barrio("bocagrande", null)));
        CorteAgua restablecido = corte("c1", EstadoCorte.RESTABLECIDO, "manga", "bocagrande")
                .cierres(Map.of(
                        new SectorId("manga"), new CierreDeCorte(INICIO.plus(3, ChronoUnit.HOURS), OrigenEstado.ACUACAR, false),
                        new SectorId("bocagrande"), new CierreDeCorte(INICIO.plus(4, ChronoUnit.HOURS), OrigenEstado.VECINOS, true)))
                .build();
        when(cortes.listarTodos()).thenReturn(List.of(restablecido));
        when(almacen.reemplazar(any(), any(), any())).thenReturn(new ConteosAnaliticos(2, 1, 2));

        ResultadoDeCopia resultado = servicio.copiar();

        assertThat(resultado).isEqualTo(ResultadoDeCopia.OK);
        ArgumentCaptor<List<FilaSectorAnalitica>> barrios = ArgumentCaptor.forClass(List.class);
        verify(almacen).reemplazar(barrios.capture(), any(), eq(AHORA));
        assertThat(barrios.getValue()).containsExactly(new FilaSectorAnalitica("manga", "Barrio manga", 1000),
                new FilaSectorAnalitica("bocagrande", "Barrio bocagrande", null));

        FilaCorteAnalitica fila = cortesCopiados().get(0);
        assertThat(fila.id()).isEqualTo("c1");
        assertThat(fila.origen()).isEqualTo("OFICIAL_ACUACAR");
        assertThat(fila.estado()).isEqualTo("RESTABLECIDO");
        assertThat(fila.pares()).containsExactlyInAnyOrder(
                new FilaParAnalitica("manga", INICIO.plus(3, ChronoUnit.HOURS), "ACUACAR", false),
                new FilaParAnalitica("bocagrande", INICIO.plus(4, ChronoUnit.HOURS), "VECINOS", true));
        verify(almacen).cerrarCopia(eq(7L), eq(AHORA), eq(ResultadoDeCopia.OK), eq(new ConteosAnaliticos(2, 1, 2)),
                eq(new ConteosAnaliticos(2, 1, 2)), isNull());
    }

    @Test
    void debeCopiarUnBarrioSinCierreSinHoraNiFuente() {
        when(sectores.listarTodos()).thenReturn(List.of(barrio("manga", 10), barrio("bocagrande", 10)));
        CorteAgua parcial = corte("c1", EstadoCorte.ANUNCIADO, "manga", "bocagrande")
                .cierres(Map.of(new SectorId("manga"), new CierreDeCorte(INICIO.plus(1, ChronoUnit.HOURS), OrigenEstado.VEEDOR, false)))
                .build();
        when(cortes.listarTodos()).thenReturn(List.of(parcial));
        when(almacen.reemplazar(any(), any(), any())).thenReturn(new ConteosAnaliticos(2, 1, 2));

        servicio.copiar();

        assertThat(cortesCopiados().get(0).pares()).containsExactlyInAnyOrder(
                new FilaParAnalitica("manga", INICIO.plus(1, ChronoUnit.HOURS), "VEEDOR", false),
                new FilaParAnalitica("bocagrande", null, null, false));
    }

    @Test
    void debeCopiarUnCorteAntiguoQueSoloTraiaFinRealComoUnCierreEnCadaBarrio() {
        when(sectores.listarTodos()).thenReturn(List.of(barrio("manga", 10), barrio("bocagrande", 10)));
        Instant finReal = INICIO.plus(5, ChronoUnit.HOURS);
        CorteAgua antiguo = corte("viejo", EstadoCorte.RESTABLECIDO, "manga", "bocagrande").finReal(finReal).build();
        when(cortes.listarTodos()).thenReturn(List.of(antiguo));
        when(almacen.reemplazar(any(), any(), any())).thenReturn(new ConteosAnaliticos(2, 1, 2));

        servicio.copiar();

        assertThat(cortesCopiados().get(0).pares()).containsExactlyInAnyOrder(
                new FilaParAnalitica("manga", finReal, "VEEDOR", false),
                new FilaParAnalitica("bocagrande", finReal, "VEEDOR", false));
    }

    @Test
    void debeMarcarConDiferenciasSiLosConteosDeLasDosBasesNoCuadran() {
        when(sectores.listarTodos()).thenReturn(List.of(barrio("manga", 10)));
        when(cortes.listarTodos()).thenReturn(List.of(corte("c1", EstadoCorte.ANUNCIADO, "manga").build()));
        when(almacen.reemplazar(any(), any(), any())).thenReturn(new ConteosAnaliticos(1, 0, 0));

        ResultadoDeCopia resultado = servicio.copiar();

        assertThat(resultado).isEqualTo(ResultadoDeCopia.CON_DIFERENCIAS);
        ArgumentCaptor<String> detalle = ArgumentCaptor.forClass(String.class);
        verify(almacen).cerrarCopia(eq(7L), eq(AHORA), eq(ResultadoDeCopia.CON_DIFERENCIAS), eq(new ConteosAnaliticos(1, 1, 1)),
                eq(new ConteosAnaliticos(1, 0, 0)), detalle.capture());
        assertThat(detalle.getValue()).contains("cortes 1 vs 0").contains("pares corte-barrio 1 vs 0").doesNotContain("barrios");
    }

    @Test
    void noDebeCopiarUnParQueApuntaAUnBarrioInexistenteYDebeDecirloComoDiferencia() {
        when(sectores.listarTodos()).thenReturn(List.of(barrio("manga", 10)));
        when(cortes.listarTodos()).thenReturn(List.of(corte("c1", EstadoCorte.ANUNCIADO, "manga", "fantasma").build()));
        when(almacen.reemplazar(any(), any(), any())).thenReturn(new ConteosAnaliticos(1, 1, 1));

        ResultadoDeCopia resultado = servicio.copiar();

        assertThat(resultado).isEqualTo(ResultadoDeCopia.CON_DIFERENCIAS);
        assertThat(cortesCopiados().get(0).pares()).extracting(FilaParAnalitica::sectorId).containsExactly("manga");
        ArgumentCaptor<String> detalle = ArgumentCaptor.forClass(String.class);
        verify(almacen).cerrarCopia(eq(7L), any(), eq(ResultadoDeCopia.CON_DIFERENCIAS), any(), any(), detalle.capture());
        assertThat(detalle.getValue()).contains("1 par(es) apuntan a un barrio que no existe").contains("c1 -> fantasma");
    }

    @Test
    void debeMarcarFallidaYNoPropagarSiElAlmacenFallaAlCopiar() {
        when(sectores.listarTodos()).thenReturn(List.of(barrio("manga", 10)));
        when(cortes.listarTodos()).thenReturn(List.of());
        when(almacen.reemplazar(any(), any(), any())).thenThrow(new AnaliticaNoDisponibleException("se cayó"));

        ResultadoDeCopia resultado = servicio.copiar();

        assertThat(resultado).isEqualTo(ResultadoDeCopia.FALLIDA);
        ArgumentCaptor<String> detalle = ArgumentCaptor.forClass(String.class);
        verify(almacen).cerrarCopia(eq(7L), eq(AHORA), eq(ResultadoDeCopia.FALLIDA), eq(new ConteosAnaliticos(1, 0, 0)),
                isNull(), detalle.capture());
        assertThat(detalle.getValue()).contains("se cayó");
    }

    @Test
    void debeMarcarFallidaSiMongoFallaAlLeer() {
        when(sectores.listarTodos()).thenThrow(new IllegalStateException("Mongo caído"));

        ResultadoDeCopia resultado = servicio.copiar();

        assertThat(resultado).isEqualTo(ResultadoDeCopia.FALLIDA);
        verify(almacen, never()).reemplazar(any(), any(), any());
        verify(almacen).cerrarCopia(eq(7L), eq(AHORA), eq(ResultadoDeCopia.FALLIDA), isNull(), isNull(), any());
    }

    @Test
    void noDebePropagarSiTampocoSePuedeAnotarElFallo() {
        when(sectores.listarTodos()).thenReturn(List.of());
        when(cortes.listarTodos()).thenReturn(List.of());
        when(almacen.reemplazar(any(), any(), any())).thenThrow(new AnaliticaNoDisponibleException("se cayó"));
        doThrow(new AnaliticaNoDisponibleException("sigue caída"))
                .when(almacen).cerrarCopia(anyLong(), any(), eq(ResultadoDeCopia.FALLIDA), any(), any(), any());

        assertThat(servicio.copiar()).isEqualTo(ResultadoDeCopia.FALLIDA);
    }

    @Test
    void debeOmitirLaCopiaSiNiSiquieraSePuedeRegistrarElInicio() {
        when(almacen.registrarInicioDeCopia(any())).thenThrow(new AnaliticaNoDisponibleException("sin PostgreSQL"));

        ResultadoDeCopia resultado = servicio.copiar();

        assertThat(resultado).isEqualTo(ResultadoDeCopia.FALLIDA);
        verifyNoMoreInteractions(sectores, cortes);
        verify(almacen, never()).reemplazar(any(), any(), any());
        verify(almacen, never()).cerrarCopia(anyLong(), any(), any(), any(), any(), any());
    }
}
