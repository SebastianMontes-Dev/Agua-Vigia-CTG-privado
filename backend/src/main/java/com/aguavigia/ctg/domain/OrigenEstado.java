package com.aguavigia.ctg.domain;

/** Quién sostiene el estado que se publica de un barrio: es lo que el vecino ve como «según …». */
public enum OrigenEstado {
    ACUACAR,
    VEEDOR,
    VECINOS,
    PRENSA,
    SENSOR;

    /** Los votos los pueden dar vecinos o sensores de la red; el resto de las fuentes no votan. */
    public static boolean votan(OrigenEstado origen) {
        return origen == VECINOS || origen == SENSOR;
    }

    /**
     * «Según los sensores de la red» no es «según 11 vecinos»: cuando todos los reportes que sostienen un estado entraron por el
     * endpoint de sensores el origen es SENSOR; con un solo vecino de por medio es VECINOS.
     */
    public static OrigenEstado deLosVotos(java.util.List<ReporteCiudadano> reportes) {
        return !reportes.isEmpty() && reportes.stream().allMatch(ReporteCiudadano::esSensor) ? SENSOR : VECINOS;
    }
}
