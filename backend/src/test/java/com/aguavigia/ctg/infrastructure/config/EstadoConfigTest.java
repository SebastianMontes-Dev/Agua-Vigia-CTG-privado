package com.aguavigia.ctg.infrastructure.config;

import com.aguavigia.ctg.domain.ReglasDeEstado;
import com.aguavigia.ctg.domain.ResolutorDeEstadoSector;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EstadoConfigTest {

    private final EstadoConfig config = new EstadoConfig();

    @Test
    void debeConvertirLosPlazosEnHorasADuraciones() {
        ReglasDeEstado reglas = config.reglasDeEstado(72, 6, 24, 2, 3);

        assertThat(reglas.expiraTrasFin()).isEqualTo(Duration.ofHours(72));
        assertThat(reglas.vecinosSinVerificacion()).isEqualTo(Duration.ofHours(6));
        assertThat(reglas.vecinosCaducan()).isEqualTo(Duration.ofHours(24));
        assertThat(reglas.restablecimientoMinimo()).isEqualTo(2);
        assertThat(reglas.ventanaDeReapertura()).isEqualTo(Duration.ofHours(3));
    }

    /** Los valores iniciales del plan son los que rigen cuando nadie configura nada. */
    @Test
    void losValoresPorDefectoDeLaConfiguracionSonLosDelPlan() {
        assertThat(config.reglasDeEstado(72, 6, 24, 2, 3)).isEqualTo(ReglasDeEstado.porDefecto());
    }

    @Test
    void elResolutorUsaLasReglasConfiguradas() {
        ReglasDeEstado reglas = config.reglasDeEstado(48, 3, 12, 3, 5);

        ResolutorDeEstadoSector resolutor = config.resolutorDeEstadoSector(reglas);

        assertThat(resolutor.reglas()).isEqualTo(reglas);
    }

    @Test
    void debeRechazarUnPlazoNoPositivo() {
        assertThatThrownBy(() -> config.reglasDeEstado(0, 6, 24, 2, 3)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> config.reglasDeEstado(72, 6, -1, 2, 3)).isInstanceOf(IllegalArgumentException.class);
    }
}
