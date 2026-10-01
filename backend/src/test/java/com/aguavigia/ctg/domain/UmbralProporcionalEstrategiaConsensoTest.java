package com.aguavigia.ctg.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UmbralProporcionalEstrategiaConsensoTest {

    private static final EstrategiaConsenso ESTRATEGIA = new UmbralProporcionalEstrategiaConsenso(0.001, 3, 15);

    @Test
    void debeExigirMasReportesEnUnSectorMasPoblado() {
        Sector bocagrande = new Sector(new SectorId("bocagrande"), "BOCAGRANDE", 12000, null);

        assertThat(ESTRATEGIA.seAlcanzaConsenso(11, bocagrande)).isFalse();
        assertThat(ESTRATEGIA.seAlcanzaConsenso(12, bocagrande)).isTrue();
    }

    @Test
    void debeAplicarElPisoEnUnSectorPequeno() {
        Sector sectorPequeno = new Sector(new SectorId("el-socorro"), "EL SOCORRO", 500, null);

        // ceil(500 * 0.001) = 1, pero el piso es 3
        assertThat(ESTRATEGIA.seAlcanzaConsenso(2, sectorPequeno)).isFalse();
        assertThat(ESTRATEGIA.seAlcanzaConsenso(3, sectorPequeno)).isTrue();
    }

    @Test
    void debeUsarElPisoCuandoNoHayDatoCensal() {
        Sector sinCenso = new Sector(new SectorId("isla-fuerte"), "ISLA FUERTE", null, null);

        assertThat(ESTRATEGIA.seAlcanzaConsenso(2, sinCenso)).isFalse();
        assertThat(ESTRATEGIA.seAlcanzaConsenso(3, sinCenso)).isTrue();
    }

    /** El resolutor recibe el umbral como número: lo necesita para aplicar el quórum reducido y mostrar «11 de 12». */
    @Test
    void debeExponerElUmbralQueAplicaAUnSector() {
        assertThat(ESTRATEGIA.umbral(new Sector(new SectorId("bocagrande"), "BOCAGRANDE", 12000, null))).isEqualTo(12);
        assertThat(ESTRATEGIA.umbral(new Sector(new SectorId("el-socorro"), "EL SOCORRO", 500, null))).isEqualTo(3);
        assertThat(ESTRATEGIA.umbral(new Sector(new SectorId("isla-fuerte"), "ISLA FUERTE", null, null))).isEqualTo(3);
    }

    /** Participación no es población: 48 vecinos de El Pozón harían inalcanzable el quórum, así que hay un tope. */
    @Test
    void debeAplicarElTopeEnUnSectorMuyPoblado() {
        Sector elPozon = new Sector(new SectorId("el-pozon"), "EL POZON", 47616, null);

        assertThat(ESTRATEGIA.umbral(elPozon)).isEqualTo(15);
        assertThat(ESTRATEGIA.seAlcanzaConsenso(14, elPozon)).isFalse();
        assertThat(ESTRATEGIA.seAlcanzaConsenso(15, elPozon)).isTrue();
    }

    @Test
    void elTopeNoAfectaAUnSectorQueNoLoAlcanza() {
        assertThat(ESTRATEGIA.umbral(new Sector(new SectorId("manga"), "MANGA", 10754, null))).isEqualTo(11);
        assertThat(ESTRATEGIA.umbral(new Sector(new SectorId("limite"), "LIMITE", 15000, null))).isEqualTo(15);
    }

    @Test
    void debeRechazarUnTopeMenorQueElPiso() {
        assertThatThrownBy(() -> new UmbralProporcionalEstrategiaConsenso(0.001, 3, 2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void debeRechazarUnFactorNoPositivo() {
        assertThatThrownBy(() -> new UmbralProporcionalEstrategiaConsenso(0, 3, 15))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void debeRechazarUnUmbralMinimoNoPositivo() {
        assertThatThrownBy(() -> new UmbralProporcionalEstrategiaConsenso(0.001, 0, 15))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
