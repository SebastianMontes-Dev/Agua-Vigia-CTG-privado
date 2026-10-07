package com.aguavigia.ctg.domain.port.in;

import com.aguavigia.ctg.domain.BoletinSimulado;

/** Hace llegar un boletín a la ingesta de la simulación y la corre en el acto (D38). No existe en la instancia real. */
public interface InyectarBoletinSimuladoUseCase {

    /** Devuelve el boletín con su fecha resuelta. */
    BoletinSimulado inyectar(BoletinSimulado boletin);
}
