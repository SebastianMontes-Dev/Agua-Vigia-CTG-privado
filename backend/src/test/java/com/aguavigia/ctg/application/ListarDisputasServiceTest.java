package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.EstadoServicio;
import com.aguavigia.ctg.domain.MarcasDeEstado;
import com.aguavigia.ctg.domain.OrigenEstado;
import com.aguavigia.ctg.domain.Sector;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.VentanaTiempo;
import com.aguavigia.ctg.domain.port.out.SectorRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/** Los barrios en disputa son los que los vecinos contradicen sin cambiar el color: el veedor decide qué hacer. */
class ListarDisputasServiceTest {

    private static final Instant T = Instant.parse("2026-08-21T14:00:00Z");

    private static Sector sector(String id, int enContra, boolean enDisputa) {
        MarcasDeEstado marcas = new MarcasDeEstado(OrigenEstado.ACUACAR, new VentanaTiempo(T, T.plusSeconds(3600)),
                false, enDisputa, enContra, null);
        return new Sector(new SectorId(id), id.toUpperCase(), 1000, EstadoServicio.SIN_SERVICIO, T, T, marcas);
    }

    @Test
    void devuelveSoloLosBarriosEnDisputaConLosQueMasVecinosContradicenPrimero() {
        SectorRepository sectores = mock(SectorRepository.class);
        given(sectores.listarTodos()).willReturn(List.of(
                sector("manga", 4, true), sector("crespo", 0, false), sector("bocagrande", 9, true)));

        List<Sector> disputas = new ListarDisputasService(sectores).listar();

        assertThat(disputas).extracting(s -> s.id().valor()).containsExactly("bocagrande", "manga");
    }
}
