package com.aguavigia.ctg.application;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class LimitesDeReporteTest {

    private static final LimitesDeReporte LIMITES = new LimitesDeReporte(3, 30, 5, Duration.ofMinutes(30));

    @Test
    void unDispositivoAnonimoDebeTenerElCupoMasEstrecho() {
        assertThat(LIMITES.cupoPara(false, false)).isEqualTo(3);
    }

    @Test
    void unVecinoRegistradoDebeTenerMasCupoQueUnDispositivo() {
        assertThat(LIMITES.cupoPara(false, true)).isEqualTo(5);
    }

    /** Un sensor se autentica con su clave y reporta cada pocos minutos por diseño: su cupo manda sobre los demás. */
    @Test
    void unSensorDebeTenerSuPropioCupoAunqueNoSeaUnaCuenta() {
        assertThat(LIMITES.cupoPara(true, false)).isEqualTo(30);
        assertThat(LIMITES.cupoPara(true, true)).isEqualTo(30);
    }

    @Test
    void debeRechazarUnCupoOUnaVentanaQueNoSeanPositivos() {
        assertThatIllegalArgumentException().isThrownBy(() -> new LimitesDeReporte(0, 30, 5, Duration.ofMinutes(30)));
        assertThatIllegalArgumentException().isThrownBy(() -> new LimitesDeReporte(3, 0, 5, Duration.ofMinutes(30)));
        assertThatIllegalArgumentException().isThrownBy(() -> new LimitesDeReporte(3, 30, 0, Duration.ofMinutes(30)));
        assertThatIllegalArgumentException().isThrownBy(() -> new LimitesDeReporte(3, 30, 5, Duration.ZERO));
        assertThatIllegalArgumentException().isThrownBy(() -> new LimitesDeReporte(3, 30, 5, null));
    }
}
