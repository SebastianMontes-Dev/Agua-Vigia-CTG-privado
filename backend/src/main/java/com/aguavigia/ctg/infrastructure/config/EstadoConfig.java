package com.aguavigia.ctg.infrastructure.config;

import com.aguavigia.ctg.domain.ReglasDeEstado;
import com.aguavigia.ctg.domain.ResolutorDeEstadoSector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Los plazos del resolutor del estado son valores iniciales sin datos reales que los respalden (plan §9):
 * son configuración y no constantes para poder calibrarlos con las métricas sin tocar el código.
 */
@Configuration
public class EstadoConfig {

    @Bean
    public ReglasDeEstado reglasDeEstado(
            @Value("${aguavigia.estado.expira-tras-fin-horas:72}") long expiraTrasFinHoras,
            @Value("${aguavigia.estado.vecinos-sin-verificacion-horas:6}") long vecinosSinVerificacionHoras,
            @Value("${aguavigia.estado.vecinos-caducan-horas:24}") long vecinosCaducanHoras,
            @Value("${aguavigia.estado.restablecimiento-minimo-vecinos:2}") int restablecimientoMinimo,
            @Value("${aguavigia.estado.reapertura-horas:3}") long reaperturaHoras) {
        return new ReglasDeEstado(Duration.ofHours(expiraTrasFinHoras), Duration.ofHours(vecinosSinVerificacionHoras),
                Duration.ofHours(vecinosCaducanHoras), restablecimientoMinimo, Duration.ofHours(reaperturaHoras));
    }

    @Bean
    public ResolutorDeEstadoSector resolutorDeEstadoSector(ReglasDeEstado reglas) {
        return new ResolutorDeEstadoSector(reglas);
    }
}
