package com.aguavigia.ctg.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MetricasDelSistemaTest {

    private static final Instant DESDE = Instant.parse("2026-10-06T12:00:00Z");
    private static final MetricasDelSistema.TiempoHastaElCambio SIN_CAMBIOS =
            new MetricasDelSistema.TiempoHastaElCambio(0, 0, 0);

    private static MetricasDelSistema con(Map<String, Long> mapa) {
        return new MetricasDelSistema(DESDE, mapa, 0, mapa, mapa, mapa, SIN_CAMBIOS);
    }

    /** `Map.copyOf` baraja el orden en cada arranque: el panel y las pruebas deben ver siempre el mismo. */
    @Test
    void debeConservarElOrdenDeLosMapasQueRecibe() {
        Map<String, Long> ordenado = new TreeMap<>();
        ordenado.put("SIN_SERVICIO/VECINOS", 3L);
        ordenado.put("CON_SERVICIO/ACUACAR", 1L);
        ordenado.put("PRESION_BAJA/SENSOR", 2L);

        MetricasDelSistema metricas = con(ordenado);

        assertThat(metricas.cambiosDeEstado().keySet())
                .containsExactly("CON_SERVICIO/ACUACAR", "PRESION_BAJA/SENSOR", "SIN_SERVICIO/VECINOS");
        assertThat(metricas.fallosDeColectores().keySet()).containsExactlyElementsOf(ordenado.keySet());
    }

    @Test
    void debeConservarElOrdenDeInsercionDeUnMapaNoOrdenado() {
        Map<String, Long> insercion = new LinkedHashMap<>();
        insercion.put("z", 1L);
        insercion.put("a", 2L);

        assertThat(con(insercion).reportesPorNivelDeVerificacion().keySet()).containsExactly("z", "a");
    }

    @Test
    void losMapasQueExponeNoSePuedenModificar() {
        MetricasDelSistema metricas = con(new LinkedHashMap<>(Map.of("a", 1L)));

        assertThatThrownBy(() -> metricas.cambiosDeEstado().put("b", 2L))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void siElMapaOriginalCambiaDespuesNoAfectaALasMetricas() {
        Map<String, Long> original = new LinkedHashMap<>(Map.of("a", 1L));
        MetricasDelSistema metricas = con(original);

        original.put("b", 2L);

        assertThat(metricas.cambiosDeEstado()).containsOnlyKeys("a");
    }
}
