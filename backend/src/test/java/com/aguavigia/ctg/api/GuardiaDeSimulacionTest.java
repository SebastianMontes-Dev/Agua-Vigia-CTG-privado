package com.aguavigia.ctg.api;

import com.aguavigia.ctg.api.error.ServicioNoDisponibleException;
import com.aguavigia.ctg.domain.CredencialInvalidaException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GuardiaDeSimulacionTest {

    private static final String CLAVE = "0123456789abcdef0123456789abcdef";

    @Test
    void laClaveCorrectaPasa() {
        assertThatCode(() -> new GuardiaDeSimulacion(CLAVE).exigir(CLAVE)).doesNotThrowAnyException();
    }

    @Test
    void unaClaveDistintaOAusenteEsUnaCredencialInvalida() {
        GuardiaDeSimulacion guardia = new GuardiaDeSimulacion(CLAVE);

        assertThatThrownBy(() -> guardia.exigir("otra-clave")).isInstanceOf(CredencialInvalidaException.class);
        assertThatThrownBy(() -> guardia.exigir(null)).isInstanceOf(CredencialInvalidaException.class);
        assertThatThrownBy(() -> guardia.exigir("")).isInstanceOf(CredencialInvalidaException.class);
    }

    @Test
    void sinClaveConfiguradaNoAtiendeANadieAunqueLaCabeceraVaciaCoincida() {
        GuardiaDeSimulacion guardia = new GuardiaDeSimulacion("");

        assertThatThrownBy(() -> guardia.exigir("")).isInstanceOf(ServicioNoDisponibleException.class);
        assertThatThrownBy(() -> guardia.exigir(CLAVE)).isInstanceOf(ServicioNoDisponibleException.class);
    }

    /** Una clave adivinable en una ruta que mueve el reloj y abre sesiones de ADMIN no vale: el arranque debe fallar. */
    @Test
    void unaClaveCortaHaceFallarElArranque() {
        assertThatThrownBy(() -> new GuardiaDeSimulacion("corta"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SIMULACION_CLAVE");
    }
}
