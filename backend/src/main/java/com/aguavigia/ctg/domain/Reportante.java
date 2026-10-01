package com.aguavigia.ctg.domain;

/**
 * Quién envía un reporte, ya identificado por el servidor: la huella con la que vota (de su dispositivo o de su
 * cuenta), la cuenta si reporta como vecino y el barrio que esa cuenta tiene verificado (nulo si no lo tiene).
 */
public record Reportante(HuellaDispositivo huella, UsuarioId cuentaId, SectorId barrioVerificado) {

    public Reportante {
        if (huella == null) {
            throw new IllegalArgumentException("El reportante debe tener huella");
        }
    }

    /** Sin cuenta: un dispositivo con token, o un sensor. No hay nada verificado sobre dónde está. */
    public static Reportante anonimo(HuellaDispositivo huella) {
        return new Reportante(huella, null, null);
    }
}
