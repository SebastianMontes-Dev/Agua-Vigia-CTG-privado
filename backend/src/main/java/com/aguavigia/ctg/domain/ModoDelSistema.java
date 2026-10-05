package com.aguavigia.ctg.domain;

/**
 * Qué instancia es esta y cuánto de lo que contiene es sintético, dicho tal cual: la simulación nunca se presenta como real, y las
 * cuentas sintéticas nunca se presentan como adopción.
 */
public record ModoDelSistema(Modo modo, long cuentasSinteticas) {

    public enum Modo {
        /** La instancia de verdad: boletines reales y reportes de personas. */
        REAL,
        /** La instancia de simulación (reloj acelerado, boletines sintéticos); nada suyo toca la real. */
        SIMULACION;

        public static Modo deTexto(String texto) {
            if (texto == null || texto.isBlank()) {
                return REAL;
            }
            try {
                return valueOf(texto.strip().toUpperCase());
            } catch (IllegalArgumentException desconocido) {
                throw new IllegalArgumentException("Modo del sistema desconocido '" + texto + "': usa REAL o SIMULACION");
            }
        }
    }

    public ModoDelSistema {
        if (modo == null) {
            throw new IllegalArgumentException("El modo del sistema no puede ser nulo");
        }
        if (cuentasSinteticas < 0) {
            throw new IllegalArgumentException("El número de cuentas sintéticas no puede ser negativo");
        }
    }
}
