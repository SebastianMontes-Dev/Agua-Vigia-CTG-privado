package com.aguavigia.ctg.application;

import java.time.Duration;

/**
 * Cuántos reportes puede hacer cada tipo de reportante por sector dentro de la ventana (RF006): un dispositivo anónimo
 * es a quien el requisito quiere frenar; un vecino registrado tiene cuenta, se le puede suspender y su reporte lleva
 * verificación; un sensor se autentica con su clave y reporta cada pocos minutos por diseño.
 */
public record LimitesDeReporte(int porDispositivo, int porSensor, int porVecino, Duration ventana) {

    public LimitesDeReporte {
        if (porDispositivo <= 0 || porSensor <= 0 || porVecino <= 0) {
            throw new IllegalArgumentException("Los cupos de reporte deben ser positivos");
        }
        if (ventana == null || ventana.isZero() || ventana.isNegative()) {
            throw new IllegalArgumentException("La ventana del cupo debe ser positiva");
        }
    }

    /** El cupo que aplica a este reportante; el del sensor manda sobre los demás. */
    public int cupoPara(boolean esSensor, boolean esVecino) {
        if (esSensor) {
            return porSensor;
        }
        return esVecino ? porVecino : porDispositivo;
    }
}
