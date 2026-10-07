package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.port.in.ReiniciarSimulacionUseCase;
import com.aguavigia.ctg.domain.port.out.BuzonDeBoletinesSimuladosPort;
import com.aguavigia.ctg.domain.port.out.RelojControlablePort;

public class ReiniciarSimulacionService implements ReiniciarSimulacionUseCase {

    private final BuzonDeBoletinesSimuladosPort buzon;
    private final RelojControlablePort reloj;

    public ReiniciarSimulacionService(BuzonDeBoletinesSimuladosPort buzon, RelojControlablePort reloj) {
        this.buzon = buzon;
        this.reloj = reloj;
    }

    @Override
    public void reiniciar() {
        buzon.vaciar();
        reloj.volverAlRelojReal();
    }
}
