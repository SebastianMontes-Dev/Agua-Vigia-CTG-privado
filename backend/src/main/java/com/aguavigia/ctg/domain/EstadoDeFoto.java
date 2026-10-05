package com.aguavigia.ctg.domain;

/**
 * En qué punto está la foto de un reporte. La foto es evidencia para el veedor, no contenido público: solo se muestra
 * cuando el reporte está aprobado y la foto no se descartó.
 */
public enum EstadoDeFoto {
    /** El reporte no trae foto. */
    SIN_FOTO,
    /** Trae foto y el reporte aún espera moderación: la ve el panel, el público no. */
    EN_REVISION,
    /** El reporte está aprobado y la foto no se descartó: se sirve al público. */
    PUBLICA,
    /** El veedor descartó la foto, o el reporte entero: no se sirve al público. */
    DESCARTADA
}
