package com.aguavigia.ctg.domain;

/**
 * Cuánto respaldo tiene el hecho de que quien reporta está en el barrio que reporta (D16). Sin pesos
 * ni puntajes: un reporte tiene alguna verificación o no la tiene, y el quórum exige una proporción
 * mínima de reportes que sí (ver {@link ComposicionDelSustento}).
 */
public enum NivelDeVerificacion {

    /** Un vecino registrado con su barrio verificado reportó ese mismo barrio. */
    CUENTA_VERIFICADA,

    /** La ubicación del momento, con buena precisión, cayó dentro del barrio reportado. La coordenada no es la prueba que se guarda: solo este nivel. */
    UBICACION_VERIFICADA,

    /** Nada respalda dónde está quien reporta: el caso de quien reporta sin cuenta ni ubicación. */
    NINGUNA
}
