package com.aguavigia.ctg.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class HuellaDispositivoTest {

    private static final DispositivoId DISPOSITIVO = new DispositivoId("7f1c2b9e-4d3a-4e5b-8c6d-1a2b3c4d5e6f");

    @Test
    void laHuellaDeUnDispositivoDebeSerUnSha256QueNoContieneSuId() {
        HuellaDispositivo huella = HuellaDispositivo.deDispositivo(DISPOSITIVO);

        assertThat(huella.hash()).matches("[0-9a-f]{64}").doesNotContain(DISPOSITIVO.valor());
    }

    @Test
    void laHuellaDeUnDispositivoDebeSerEstable() {
        assertThat(HuellaDispositivo.deDispositivo(DISPOSITIVO)).isEqualTo(HuellaDispositivo.deDispositivo(DISPOSITIVO));
    }

    @Test
    void dispositivosDistintosDebenTenerHuellasDistintas() {
        assertThat(HuellaDispositivo.deDispositivo(DISPOSITIVO))
                .isNotEqualTo(HuellaDispositivo.deDispositivo(new DispositivoId("otro")));
    }

    /** Un vecino que reporta desde su cuenta cuenta como un solo votante aunque cambie de aparato. */
    @Test
    void laHuellaDeUnaCuentaDebeSerEstableYNoContenerElIdDeLaCuenta() {
        UsuarioId cuenta = new UsuarioId("v-1");

        assertThat(HuellaDispositivo.deCuenta(cuenta)).isEqualTo(HuellaDispositivo.deCuenta(cuenta));
        assertThat(HuellaDispositivo.deCuenta(cuenta).hash()).matches("[0-9a-f]{64}").doesNotContain("v-1");
    }

    /** Un id de cuenta y uno de dispositivo con el mismo texto no pueden compartir huella: serían el mismo votante. */
    @Test
    void laHuellaDeCuentaYLaDeDispositivoConElMismoTextoDebenDiferir() {
        assertThat(HuellaDispositivo.deCuenta(new UsuarioId("x")))
                .isNotEqualTo(HuellaDispositivo.deDispositivo(new DispositivoId("x")));
    }

    @Test
    void sigueRechazandoUnaHuellaVacia() {
        assertThatIllegalArgumentException().isThrownBy(() -> new HuellaDispositivo(" "));
    }

    @Test
    void laHuellaDeSensorSigueTeniendoSuPrefijo() {
        assertThat(HuellaDispositivo.deSensor("s-1").hash()).isEqualTo("IoT-s-1");
    }
}
