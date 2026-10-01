package com.aguavigia.ctg.domain.port.out;

import com.aguavigia.ctg.domain.DispositivoId;

import java.util.Optional;

/**
 * El token de dispositivo lo firma el servidor: un cliente no puede inventarse una identidad ni
 * cambiar la de otro, solo pedir una nueva (y pedirla tiene un límite por IP). Es lo que sustituye a
 * la `huella` que antes elegía el propio cliente.
 */
public interface FirmaDeDispositivosPort {

    /** El token que se le entrega al cliente: lleva el id y su firma, y nada más. */
    String emitir(DispositivoId id);

    /** Vacío si el token está mal formado o su firma no corresponde a este servidor. */
    Optional<DispositivoId> verificar(String token);
}
