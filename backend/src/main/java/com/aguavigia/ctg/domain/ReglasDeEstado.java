package com.aguavigia.ctg.domain;

import java.time.Duration;
import java.util.Objects;

/**
 * Los plazos del resolutor. Son valores iniciales sin datos reales que los respalden (plan §9):
 * se calibran con las métricas del sistema, por eso viajan como configuración y no como constantes.
 *
 * @param expiraTrasFin           cuánto después del fin prometido un corte que nadie confirmó deja de
 *                                afirmar nada y el barrio vuelve a «sin datos»: un mapa congelado en
 *                                rojo es peor que admitir que no se sabe
 * @param vecinosSinVerificacion  desde cuándo, sin un reporte nuevo, un estado que solo sostienen los
 *                                vecinos se marca «sin verificación reciente»
 * @param vecinosCaducan          desde cuándo, sin un reporte nuevo, ese estado vuelve a «sin datos»
 * @param restablecimientoMinimo  piso de vecinos para confirmar un restablecimiento pasada la promesa
 * @param ventanaDeReapertura     cuánto después de un cierre provisional los vecinos pueden contradecirlo y reabrir el
 *                                mismo corte: pasado ese plazo una intermitencia ya es otro evento
 */
public record ReglasDeEstado(Duration expiraTrasFin, Duration vecinosSinVerificacion, Duration vecinosCaducan,
                             int restablecimientoMinimo, Duration ventanaDeReapertura) {

    public ReglasDeEstado {
        Objects.requireNonNull(expiraTrasFin, "Falta el plazo de expiración");
        Objects.requireNonNull(vecinosSinVerificacion, "Falta el plazo de «sin verificación reciente»");
        Objects.requireNonNull(vecinosCaducan, "Falta el plazo de caducidad de los vecinos");
        Objects.requireNonNull(ventanaDeReapertura, "Falta la ventana de reapertura");
        for (Duration plazo : new Duration[]{expiraTrasFin, vecinosSinVerificacion, vecinosCaducan, ventanaDeReapertura}) {
            if (plazo.isZero() || plazo.isNegative()) {
                throw new IllegalArgumentException("Los plazos del estado deben ser positivos: " + plazo);
            }
        }
        if (restablecimientoMinimo < 1) {
            throw new IllegalArgumentException("El mínimo de vecinos para confirmar un restablecimiento debe ser al menos 1");
        }
    }

    /**
     * Cuántos vecinos bastan para confirmar un restablecimiento una vez vencida la promesa: la mitad
     * del umbral, redondeada hacia arriba y nunca menos que el mínimo configurado.
     */
    public int quorumReducido(int umbral) {
        return Math.max(restablecimientoMinimo, (umbral + 1) / 2);
    }

    public static ReglasDeEstado porDefecto() {
        return new ReglasDeEstado(Duration.ofHours(72), Duration.ofHours(6), Duration.ofHours(24), 2, Duration.ofHours(3));
    }
}
