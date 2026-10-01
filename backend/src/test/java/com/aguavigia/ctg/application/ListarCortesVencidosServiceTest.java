package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.CierreDeCorte;
import com.aguavigia.ctg.domain.CorteAgua;
import com.aguavigia.ctg.domain.CorteId;
import com.aguavigia.ctg.domain.OrigenCorte;
import com.aguavigia.ctg.domain.OrigenEstado;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.port.out.CorteAguaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * La cola de «vencidos» del veedor: lo que le toca revisar porque la promesa ya pasó sin cierre, o porque el
 * cierre lo pusieron los vecinos o los sensores y nadie lo confirmó. Un corte cuya promesa sigue vigente no es
 * trabajo del veedor todavía.
 */
class ListarCortesVencidosServiceTest {

    private static final SectorId MANGA = new SectorId("manga");
    private static final SectorId BOCAGRANDE = new SectorId("bocagrande");
    private static final Instant INICIO = Instant.parse("2026-08-21T14:00:00Z");
    private static final Instant FIN = Instant.parse("2026-08-21T23:00:00Z");

    private CorteAguaRepository cortes;
    private Instant ahora;
    private ListarCortesVencidosService servicio;

    @BeforeEach
    void montar() {
        cortes = mock(CorteAguaRepository.class);
        ahora = FIN.plusSeconds(60);
        given(cortes.listarAbiertos()).willReturn(List.of());
        given(cortes.listarConCierresProvisionales()).willReturn(List.of());
        servicio = new ListarCortesVencidosService(cortes, () -> ahora);
    }

    private static CorteAgua corte(String id, Instant fin, SectorId... sectores) {
        return CorteAgua.builder().id(new CorteId(id)).sectoresAfectados(List.of(sectores))
                .inicio(INICIO).finPrometido(fin).causa("x").origen(OrigenCorte.INGESTA_IA).build();
    }

    @Test
    void incluyeElCorteAbiertoCuyaPromesaYaVencio() {
        given(cortes.listarAbiertos()).willReturn(List.of(corte("vencido", FIN, MANGA)));

        assertThat(servicio.listar()).extracting(c -> c.id().valor()).containsExactly("vencido");
    }

    @Test
    void noIncluyeElCorteAbiertoCuyaPromesaSigueVigente() {
        given(cortes.listarAbiertos()).willReturn(List.of(corte("vigente", FIN.plusSeconds(3600), MANGA)));

        assertThat(servicio.listar()).isEmpty();
    }

    @Test
    void incluyeElCorteConUnCierreProvisionalAunqueSuPromesaSigaVigente() {
        CorteAgua conProvisional = corte("provisional", FIN.plusSeconds(3600), MANGA)
                .cerrarSector(MANGA, new CierreDeCorte(INICIO.plusSeconds(3600), OrigenEstado.VECINOS, true));
        given(cortes.listarConCierresProvisionales()).willReturn(List.of(conProvisional));

        assertThat(servicio.listar()).extracting(c -> c.id().valor()).containsExactly("provisional");
    }

    @Test
    void unCorteQueEstaEnAmbasListasApareceUnaSolaVez() {
        CorteAgua parcial = corte("ambos", FIN, MANGA, BOCAGRANDE)
                .cerrarSector(MANGA, new CierreDeCorte(INICIO.plusSeconds(3600), OrigenEstado.VECINOS, true));
        given(cortes.listarAbiertos()).willReturn(List.of(parcial));
        given(cortes.listarConCierresProvisionales()).willReturn(List.of(parcial));

        assertThat(servicio.listar()).hasSize(1);
    }

    @Test
    void losOrdenaDelMasAntiguoAlMasReciente() {
        given(cortes.listarAbiertos()).willReturn(List.of(
                corte("reciente", FIN.minusSeconds(60), MANGA), corte("antiguo", INICIO.plusSeconds(60), BOCAGRANDE)));

        assertThat(servicio.listar()).extracting(c -> c.id().valor()).containsExactly("antiguo", "reciente");
    }
}
