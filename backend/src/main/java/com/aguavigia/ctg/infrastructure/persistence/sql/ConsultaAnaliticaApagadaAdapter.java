package com.aguavigia.ctg.infrastructure.persistence.sql;

import com.aguavigia.ctg.domain.AnaliticaNoDisponibleException;
import com.aguavigia.ctg.domain.CumplimientoAnalitico;
import com.aguavigia.ctg.domain.PuntoMensualAnalitico;
import com.aguavigia.ctg.domain.RegistroDeCopia;
import com.aguavigia.ctg.domain.port.out.ConsultaAnaliticaPort;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** La copia analítica está apagada (`aguavigia.sql.habilitado=false`): toda consulta se niega, sin tocar ninguna otra base. */
public class ConsultaAnaliticaApagadaAdapter implements ConsultaAnaliticaPort {

    static final String MENSAJE = "La copia analítica en PostgreSQL no está activada en este servidor.";

    @Override
    public CumplimientoAnalitico cumplimientoGlobal() {
        throw apagada();
    }

    @Override
    public Optional<CumplimientoAnalitico> cumplimientoDe(String sectorId) {
        throw apagada();
    }

    @Override
    public List<PuntoMensualAnalitico> cumplimientoMensual() {
        throw apagada();
    }

    @Override
    public Optional<Instant> ultimaCopiaOk() {
        throw apagada();
    }

    @Override
    public List<RegistroDeCopia> ultimasCopias(int limite) {
        throw apagada();
    }

    private static AnaliticaNoDisponibleException apagada() {
        return new AnaliticaNoDisponibleException(MENSAJE);
    }
}
