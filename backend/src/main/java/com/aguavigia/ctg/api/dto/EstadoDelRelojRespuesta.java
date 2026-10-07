package com.aguavigia.ctg.api.dto;

import com.aguavigia.ctg.domain.EstadoDelReloj;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "La hora del reloj de la simulación y cuánto se aparta del real")
public record EstadoDelRelojRespuesta(Instant ahora, long desfaseSegundos) {

    public static EstadoDelRelojRespuesta de(EstadoDelReloj estado) {
        return new EstadoDelRelojRespuesta(estado.ahora(), estado.desfase().getSeconds());
    }
}
