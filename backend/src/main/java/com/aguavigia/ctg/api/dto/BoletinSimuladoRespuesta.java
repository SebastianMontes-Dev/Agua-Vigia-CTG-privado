package com.aguavigia.ctg.api.dto;

import com.aguavigia.ctg.domain.BoletinSimulado;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "El boletín simulado ya entregado a la ingesta, con su fecha resuelta")
public record BoletinSimuladoRespuesta(long id, Instant fecha, String enlace) {

    public static BoletinSimuladoRespuesta de(BoletinSimulado boletin) {
        return new BoletinSimuladoRespuesta(boletin.id(), boletin.fecha(), boletin.enlace());
    }
}
