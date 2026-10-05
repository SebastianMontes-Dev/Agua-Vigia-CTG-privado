package com.aguavigia.ctg.domain.port.in;

import com.aguavigia.ctg.domain.ReporteCiudadano;
import com.aguavigia.ctg.domain.SectorId;

/**
 * «¿Ya volvió el agua?» con un toque (D18). Se reporta el problema, no la solución, así que confirmar un restablecimiento
 * es lo que menos llega: el enlace del correo lo vuelve un voto de {@code SERVICIO_RESTABLECIDO} del suscriptor.
 */
public interface ConfirmarRestablecimientoPorEnlaceUseCase {

    /**
     * @throws com.aguavigia.ctg.domain.EnlaceDeRestablecimientoInvalidoException si el enlace no sirve, por la razón que sea
     */
    ReporteCiudadano confirmar(SectorId sector, String token, String ip);
}
