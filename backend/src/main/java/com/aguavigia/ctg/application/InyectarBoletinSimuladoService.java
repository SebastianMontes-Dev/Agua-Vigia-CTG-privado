package com.aguavigia.ctg.application;

import com.aguavigia.ctg.compartido.reloj.Reloj;
import com.aguavigia.ctg.domain.BoletinSimulado;
import com.aguavigia.ctg.domain.port.in.InyectarBoletinSimuladoUseCase;
import com.aguavigia.ctg.domain.port.out.BuzonDeBoletinesSimuladosPort;
import com.aguavigia.ctg.domain.port.out.CicloDeIngestaPort;

public class InyectarBoletinSimuladoService implements InyectarBoletinSimuladoUseCase {

    private final BuzonDeBoletinesSimuladosPort buzon;
    private final CicloDeIngestaPort ciclo;
    private final Reloj reloj;

    public InyectarBoletinSimuladoService(BuzonDeBoletinesSimuladosPort buzon, CicloDeIngestaPort ciclo, Reloj reloj) {
        this.buzon = buzon;
        this.ciclo = ciclo;
        this.reloj = reloj;
    }

    @Override
    public BoletinSimulado inyectar(BoletinSimulado boletin) {
        if (boletin == null) {
            throw new IllegalArgumentException("Falta el boletín");
        }
        BoletinSimulado conFecha = boletin.fecha() == null ? boletin.conFecha(reloj.ahora()) : boletin;
        // Primero al buzón y después el ciclo: al revés, el ciclo no lo vería hasta el siguiente intervalo.
        buzon.encolar(conFecha);
        ciclo.ejecutarAhora();
        return conFecha;
    }
}
