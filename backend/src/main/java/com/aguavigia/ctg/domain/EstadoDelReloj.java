package com.aguavigia.ctg.domain;

import java.time.Duration;
import java.time.Instant;

/** Qué hora marca el reloj de la simulación y cuánto se aparta del real. */
public record EstadoDelReloj(Instant ahora, Duration desfase) {
}
