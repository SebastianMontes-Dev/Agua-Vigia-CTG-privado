package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.EstadoPublicado;
import com.aguavigia.ctg.domain.EstadoServicio;
import com.aguavigia.ctg.domain.ResultadoDeRecalculo;
import com.aguavigia.ctg.domain.Sector;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.port.in.RecalcularSectorUseCase;
import com.aguavigia.ctg.domain.port.out.SectorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * El consenso solo se evalúa cuando llega un reporte: sin esta pasada, un reporte insertado en Mongo o un
 * Redis vaciado nunca movería el mapa, y tras un reinicio nadie revisaría lo que ya estaba guardado (D29).
 */
class PonerAlDiaSectoresServiceTest {

    private static final SectorId MANGA = new SectorId("manga");
    private static final SectorId BOCAGRANDE = new SectorId("bocagrande");
    private static final SectorId CRESPO = new SectorId("crespo");

    private SectorRepository sectores;
    private RecalcularSectorUseCase recalcular;
    private PonerAlDiaSectoresService servicio;

    @BeforeEach
    void montar() {
        sectores = mock(SectorRepository.class);
        recalcular = mock(RecalcularSectorUseCase.class);
        given(sectores.listarTodos()).willReturn(List.of(sector(MANGA), sector(BOCAGRANDE), sector(CRESPO)));
        given(recalcular.recalcular(any())).willReturn(ResultadoDeRecalculo.sinCambio(EstadoPublicado.sinDatos()));
        servicio = new PonerAlDiaSectoresService(sectores, recalcular);
    }

    private static Sector sector(SectorId id) {
        return new Sector(id, id.valor().toUpperCase(), 1000, null);
    }

    @Test
    void recalculaTodosLosBarrios() {
        servicio.ponerAlDia();

        verify(recalcular).recalcular(MANGA);
        verify(recalcular).recalcular(BOCAGRANDE);
        verify(recalcular).recalcular(CRESPO);
    }

    @Test
    void cuentaLosBarriosQueCambiaronDeEstado() {
        given(recalcular.recalcular(BOCAGRANDE)).willReturn(new ResultadoDeRecalculo(
                EstadoPublicado.de(EstadoServicio.SIN_SERVICIO, null, null, false), true, List.of()));

        assertThat(servicio.ponerAlDia()).isEqualTo(1);
    }

    @Test
    void unBarrioQueFallaNoImpideRevisarLosDemas() {
        given(recalcular.recalcular(MANGA)).willThrow(new IllegalStateException("Mongo caído"));

        servicio.ponerAlDia();

        verify(recalcular).recalcular(BOCAGRANDE);
        verify(recalcular).recalcular(CRESPO);
    }
}
