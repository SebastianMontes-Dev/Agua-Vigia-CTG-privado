package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.ReporteId;
import com.aguavigia.ctg.domain.port.in.EmitirTokenDeSubidaUseCase;
import com.aguavigia.ctg.domain.port.out.RelojPort;
import com.aguavigia.ctg.domain.port.out.SubidaDeFotoRepository;
import com.aguavigia.ctg.domain.port.out.TokenDeSubidaPort;

import java.time.Duration;

public class EmitirTokenDeSubidaService implements EmitirTokenDeSubidaUseCase {

    private final SubidaDeFotoRepository subidas;
    private final TokenDeSubidaPort tokens;
    private final RelojPort reloj;
    private final Duration vigencia;

    public EmitirTokenDeSubidaService(SubidaDeFotoRepository subidas, TokenDeSubidaPort tokens, RelojPort reloj,
                                      Duration vigencia) {
        this.subidas = subidas;
        this.tokens = tokens;
        this.reloj = reloj;
        this.vigencia = vigencia;
    }

    @Override
    public String emitir(ReporteId reporte) {
        String token = tokens.nuevo();
        subidas.guardar(reporte, tokens.hash(token), reloj.ahora().plus(vigencia));
        return token;
    }
}
