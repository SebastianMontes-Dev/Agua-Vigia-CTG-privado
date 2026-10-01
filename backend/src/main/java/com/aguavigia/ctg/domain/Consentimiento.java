package com.aguavigia.ctg.domain;

import java.time.Instant;

/**
 * Lo que una persona aceptó y cuándo. Guarda la versión del texto vigente en ese momento: si el
 * aviso de privacidad cambia, se puede saber qué versión aceptó cada cuenta.
 */
public record Consentimiento(TipoConsentimiento tipo, String version, Instant fecha) {

    public Consentimiento {
        if (tipo == null) {
            throw new IllegalArgumentException("El consentimiento debe decir de qué tipo es");
        }
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("El consentimiento debe decir qué versión del texto se aceptó");
        }
        if (fecha == null) {
            throw new IllegalArgumentException("El consentimiento debe tener fecha");
        }
    }
}
