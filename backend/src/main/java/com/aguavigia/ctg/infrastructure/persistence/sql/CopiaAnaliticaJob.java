package com.aguavigia.ctg.infrastructure.persistence.sql;

import com.aguavigia.ctg.domain.port.in.CopiarAnaliticaUseCase;
import com.aguavigia.ctg.infrastructure.scheduling.EjecucionUnica;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;

/**
 * Llena la base analítica cada 5 minutos, y una vez al arrancar tras un minuto de espera (da tiempo a la siembra). No se engancha al
 * cierre de un corte a propósito: eso obligaría a tocar el camino de escritura de Mongo, y un fallo de PostgreSQL quedaría a un paso de
 * él. Cinco minutos de retraso en una vista analítica no importan.
 *
 * Pasa por {@link EjecucionUnica} como las demás tareas: con varias réplicas solo una copia por ciclo.
 */
public class CopiaAnaliticaJob {

    private static final Logger log = LoggerFactory.getLogger(CopiaAnaliticaJob.class);
    private static final String TAREA = "copia-analitica";

    private final CopiarAnaliticaUseCase copiar;
    private final EjecucionUnica ejecucionUnica;
    private final Duration bloqueoMinimo;

    public CopiaAnaliticaJob(CopiarAnaliticaUseCase copiar, EjecucionUnica ejecucionUnica, long intervaloMs) {
        this.copiar = copiar;
        this.ejecucionUnica = ejecucionUnica;
        // El bloqueo mínimo no puede superar al intervalo, o un intervalo corto (pruebas) seguiría corriendo cada minuto.
        this.bloqueoMinimo = Duration.ofMillis(Math.max(1, Math.min(60_000, intervaloMs)));
    }

    @Scheduled(initialDelayString = "${aguavigia.sql.copia-retraso-inicial-ms:60000}",
            fixedDelayString = "${aguavigia.sql.copia-intervalo-ms:300000}")
    public void copiarEnUnaReplica() {
        ejecucionUnica.ejecutar(TAREA, Duration.ofMinutes(10), bloqueoMinimo, this::copiarSinPropagar);
    }

    void copiarSinPropagar() {
        try {
            copiar.copiar();
        } catch (RuntimeException fallo) {
            // El caso de uso no lanza, pero sin este catch Spring dejaría de reprogramar la tarea hasta el reinicio.
            log.warn("La copia analítica falló, se reintenta en el próximo ciclo: {}", fallo.toString());
        }
    }
}
