package com.aguavigia.ctg.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Qué exige el quórum de vecinos además de llegar al umbral (D9, D16): que no sea una sola persona con
 * varios aparatos y que alguien respalde dónde está.
 *
 * - **Verificación**: al menos un tercio del sustento (mínimo 1) con alguna verificación. Exigir la mitad dejaría
 *   inútil el reporte anónimo, porque muchos no comparten su ubicación; con un tercio, el anónimo cuenta pero no
 *   puede mover el mapa solo.
 * - **Redes**: al menos {@code redesMinimas} redes distintas. Una ráfaga desde una sola red no cambia el mapa,
 *   pero tampoco se bloquea con un tope duro por IP: tras un CGNAT móvil, mucha gente legítima comparte IP.
 *
 * Un sensor se autentica con su clave y no pasa por ninguna red de ciudadano: cuenta como verificado y como su
 * propia red. Un reporte sin red conocida no cuenta como una red más.
 */
public final class ComposicionDelSustento {

    private ComposicionDelSustento() {
    }

    public static boolean cumple(List<ReporteCiudadano> sustento, int redesMinimas) {
        if (sustento.isEmpty()) {
            return false;
        }
        long verificados = sustento.stream().filter(ComposicionDelSustento::estaVerificado).count();
        long minimoVerificados = Math.max(1, (sustento.size() + 2) / 3);
        return verificados >= minimoVerificados && redesDistintas(sustento) >= redesMinimas;
    }

    private static boolean estaVerificado(ReporteCiudadano reporte) {
        return reporte.esSensor() || reporte.verificacion() != NivelDeVerificacion.NINGUNA;
    }

    private static int redesDistintas(List<ReporteCiudadano> sustento) {
        Set<String> redes = new HashSet<>();
        for (ReporteCiudadano reporte : sustento) {
            if (reporte.esSensor()) {
                redes.add("sensor:" + reporte.huella().hash());
            } else if (reporte.redHash() != null) {
                redes.add(reporte.redHash());
            }
        }
        return redes.size();
    }
}
