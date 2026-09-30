package com.aguavigia.ctg.domain;

/**
 * Lo que el resolutor decide publicar de un barrio. {@code estado} nulo significa «sin datos»
 * (`ADR-014`): nadie lo sabe, y eso se dice en vez de pintar un color.
 *
 * @param ventanaPrometida             la ventana que prometió la fuente oficial, si el estado sale de una
 * @param restablecimientoPorConfirmar la promesa ya venció pero nadie confirmó que volvió el agua
 * @param enDisputa                    los vecinos contradicen a la fuente oficial, sin cambiar el color
 * @param reportesEnContra             cuántos vecinos sostienen esa contradicción
 * @param respaldo                     los vecinos que sostienen el estado, cuando sale de ellos
 * @param sinVerificacionReciente      un estado que solo sostienen los vecinos y nadie ha renovado hace horas
 */
public record EstadoPublicado(EstadoServicio estado, OrigenEstado origen, VentanaTiempo ventanaPrometida,
                              boolean restablecimientoPorConfirmar, boolean enDisputa, int reportesEnContra,
                              RespaldoVecinal respaldo, boolean sinVerificacionReciente) {

    public static EstadoPublicado sinDatos() {
        return new EstadoPublicado(null, null, null, false, false, 0, null, false);
    }

    /** Un estado que sostiene una fuente oficial, sin vecinos de por medio. */
    public static EstadoPublicado de(EstadoServicio estado, OrigenEstado origen, VentanaTiempo ventanaPrometida,
                                     boolean restablecimientoPorConfirmar) {
        return new EstadoPublicado(estado, origen, ventanaPrometida, restablecimientoPorConfirmar, false, 0, null, false);
    }

    /** Un estado que sostienen los vecinos por quórum. */
    public static EstadoPublicado porVecinos(EstadoServicio estado, VentanaTiempo ventanaPrometida,
                                             RespaldoVecinal respaldo, boolean sinVerificacionReciente) {
        return new EstadoPublicado(estado, OrigenEstado.VECINOS, ventanaPrometida, false, false, 0, respaldo,
                sinVerificacionReciente);
    }

    /** El mismo estado, marcado en disputa porque un quórum de vecinos lo contradice. */
    public EstadoPublicado enDisputaPor(int vecinos) {
        return new EstadoPublicado(estado, origen, ventanaPrometida, restablecimientoPorConfirmar, true, vecinos,
                respaldo, sinVerificacionReciente);
    }
}
