package com.aguavigia.ctg.domain;

/** Qué aceptó una persona al registrarse o después. Cada tipo se acepta y se retira por separado. */
public enum TipoConsentimiento {

    /** Tratamiento de sus datos personales según el aviso de privacidad vigente (Ley 1581 de 2012). */
    PRIVACIDAD,

    /** Recibir avisos de cortes en su barrio. Casilla explícita: nunca va incluida en la de privacidad. */
    AVISOS
}
