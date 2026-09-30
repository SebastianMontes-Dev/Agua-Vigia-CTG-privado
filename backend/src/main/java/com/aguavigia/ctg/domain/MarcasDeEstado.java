package com.aguavigia.ctg.domain;

/**
 * Lo que acompaña al estado de un barrio y explica de dónde sale: quién lo sostiene, qué prometió
 * la fuente oficial, si falta confirmarlo o está en disputa y cuántos vecinos lo respaldan. Se
 * guarda junto al estado porque el resolutor lo necesita en el ciclo siguiente: un estado que solo
 * sostienen los vecinos no se puede recalcular desde los reportes de los últimos 30 minutos, hay
 * que recordar que ya se alcanzó y cuándo se renovó por última vez.
 */
public record MarcasDeEstado(OrigenEstado origen, VentanaTiempo ventanaPrometida, boolean porConfirmar,
                             boolean enDisputa, int reportesEnContra, RespaldoVecinal respaldo) {

    private static final MarcasDeEstado NINGUNA = new MarcasDeEstado(null, null, false, false, 0, null);

    public static MarcasDeEstado ninguna() {
        return NINGUNA;
    }

    public static MarcasDeEstado de(EstadoPublicado publicado) {
        return new MarcasDeEstado(publicado.origen(), publicado.ventanaPrometida(),
                publicado.restablecimientoPorConfirmar(), publicado.enDisputa(), publicado.reportesEnContra(),
                publicado.respaldo());
    }
}
