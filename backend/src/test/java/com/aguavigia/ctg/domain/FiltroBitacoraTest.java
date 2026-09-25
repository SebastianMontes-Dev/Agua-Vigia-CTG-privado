package com.aguavigia.ctg.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FiltroBitacoraTest {

    private static final Instant DESDE = Instant.parse("2026-09-01T05:00:00Z");

    @Test
    void sinFiltroNoDebeRestringirNada() {
        FiltroBitacora filtro = FiltroBitacora.sinFiltro();

        assertThat(filtro.sectorId()).isNull();
        assertThat(filtro.tipo()).isNull();
        assertThat(filtro.desde()).isNull();
        assertThat(filtro.hasta()).isNull();
    }

    @Test
    void debeAceptarUnRangoConHastaPosteriorADesde() {
        FiltroBitacora filtro = new FiltroBitacora(new SectorId("manga"), TipoEvento.CORTE_ANUNCIADO,
                DESDE, DESDE.plusSeconds(86_400));

        assertThat(filtro.hasta()).isAfter(filtro.desde());
    }

    @Test
    void debeAceptarSoloUnExtremoDelRango() {
        assertThat(new FiltroBitacora(null, null, DESDE, null).desde()).isEqualTo(DESDE);
        assertThat(new FiltroBitacora(null, null, null, DESDE).hasta()).isEqualTo(DESDE);
    }

    @Test
    void debeRechazarUnRangoConHastaAnteriorADesde() {
        assertThatThrownBy(() -> new FiltroBitacora(null, null, DESDE, DESDE.minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hasta");
    }

    /** `hasta` es exclusivo: con los dos extremos iguales el rango no contiene ningún instante. */
    @Test
    void debeRechazarUnRangoVacioConDesdeIgualAHasta() {
        assertThatThrownBy(() -> new FiltroBitacora(null, null, DESDE, DESDE))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
