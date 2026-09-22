package com.aguavigia.ctg.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EstadoServicioTest {

    @Test
    void sinServicioEsMasSeveroQueCualquierOtroEstado() {
        assertThat(EstadoServicio.masSevero(EstadoServicio.SIN_SERVICIO, EstadoServicio.CORTE_PROGRAMADO))
                .isEqualTo(EstadoServicio.SIN_SERVICIO);
        assertThat(EstadoServicio.masSevero(EstadoServicio.SIN_SERVICIO, EstadoServicio.PRESION_BAJA))
                .isEqualTo(EstadoServicio.SIN_SERVICIO);
        assertThat(EstadoServicio.masSevero(EstadoServicio.SIN_SERVICIO, EstadoServicio.CON_SERVICIO))
                .isEqualTo(EstadoServicio.SIN_SERVICIO);
    }

    @Test
    void corteProgramadoEsMasSeveroQuePresionBajaYConServicio() {
        assertThat(EstadoServicio.masSevero(EstadoServicio.CORTE_PROGRAMADO, EstadoServicio.PRESION_BAJA))
                .isEqualTo(EstadoServicio.CORTE_PROGRAMADO);
        assertThat(EstadoServicio.masSevero(EstadoServicio.CORTE_PROGRAMADO, EstadoServicio.CON_SERVICIO))
                .isEqualTo(EstadoServicio.CORTE_PROGRAMADO);
    }

    @Test
    void presionBajaEsMasSeveroQueConServicio() {
        assertThat(EstadoServicio.masSevero(EstadoServicio.PRESION_BAJA, EstadoServicio.CON_SERVICIO))
                .isEqualTo(EstadoServicio.PRESION_BAJA);
    }

    @Test
    void conServicioEsElMenosSevero() {
        assertThat(EstadoServicio.masSevero(EstadoServicio.CON_SERVICIO, EstadoServicio.CON_SERVICIO))
                .isEqualTo(EstadoServicio.CON_SERVICIO);
    }

    /** El orden de los argumentos no debe alterar el resultado: es un requisito para plegar una
     * lista de estados candidatos sin importar en qué orden llegan (avisos solapados). */
    @Test
    void esConmutativo() {
        for (EstadoServicio a : EstadoServicio.values()) {
            for (EstadoServicio b : EstadoServicio.values()) {
                assertThat(EstadoServicio.masSevero(a, b)).isEqualTo(EstadoServicio.masSevero(b, a));
            }
        }
    }
}
