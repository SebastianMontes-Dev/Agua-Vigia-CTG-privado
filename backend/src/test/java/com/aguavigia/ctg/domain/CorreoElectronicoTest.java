package com.aguavigia.ctg.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CorreoElectronicoTest {

    @Test
    void debeAceptarUnCorreoConFormatoValido() {
        CorreoElectronico correo = new CorreoElectronico("vecino@correo.com");

        assertThat(correo.valor()).isEqualTo("vecino@correo.com");
    }

    @Test
    void debeRechazarUnCorreoSinArroba() {
        assertThatThrownBy(() -> new CorreoElectronico("vecino-correo.com"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void debeRechazarUnCorreoSinDominio() {
        assertThatThrownBy(() -> new CorreoElectronico("vecino@correo"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void debeRechazarUnCorreoEnBlanco() {
        assertThatThrownBy(() -> new CorreoElectronico(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** Para un log basta reconocer el dominio y la inicial: el correo entero es un dato personal. */
    @Test
    void enmascaradoDebeConservarSoloLaInicialYElDominio() {
        assertThat(new CorreoElectronico("persona@correo.com").enmascarado()).isEqualTo("p***@correo.com");
        assertThat(new CorreoElectronico("a@correo.com").enmascarado()).isEqualTo("a***@correo.com");
    }
}
