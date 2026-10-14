package com.aguavigia.ctg.infrastructure.estado;

import com.aguavigia.ctg.domain.port.in.ExpirarCortesVencidosUseCase;
import com.aguavigia.ctg.domain.port.in.PonerAlDiaSectoresUseCase;
import com.aguavigia.ctg.domain.port.in.RepoblarContadorDeReportesUseCase;
import com.aguavigia.ctg.infrastructure.scheduling.EjecucionUnica;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Pone el mapa al día con lo que ya hay guardado: al terminar de arrancar y después cada 5 minutos expira los
 * cortes que nadie confirmó y recalcula todos los barrios desde Mongo (D29). Sin esto el consenso solo se
 * evaluaría cuando llega un reporte, y un reinicio o un Redis vaciado dejaría el mapa tal como estaba.
 *
 * El {@code @Scheduled} vive aquí y no en los servicios para que {@code application/} no dependa del
 * planificador de Spring. Al arrancar y en el ciclo pasan por {@link EjecucionUnica} con el mismo nombre:
 * con varias réplicas solo una lo hace.
 */
@Component
public class PuestaAlDiaDeEstadosJob {

    private static final Logger log = LoggerFactory.getLogger(PuestaAlDiaDeEstadosJob.class);
    private static final String TAREA = "puesta-al-dia";

    private final ExpirarCortesVencidosUseCase expirar;
    private final PonerAlDiaSectoresUseCase ponerAlDia;
    private final RepoblarContadorDeReportesUseCase repoblarContador;
    private final EjecucionUnica ejecucionUnica;
    private final Duration bloqueoMinimo;

    public PuestaAlDiaDeEstadosJob(ExpirarCortesVencidosUseCase expirar, PonerAlDiaSectoresUseCase ponerAlDia,
                                   RepoblarContadorDeReportesUseCase repoblarContador, EjecucionUnica ejecucionUnica,
                                   @Value("${aguavigia.estado.puesta-al-dia-ms:300000}") long intervaloMs) {
        this.expirar = expirar;
        this.ponerAlDia = ponerAlDia;
        this.repoblarContador = repoblarContador;
        this.ejecucionUnica = ejecucionUnica;
        // El bloqueo mínimo no puede superar al intervalo: con 1 minuto fijo, un intervalo de 5 s (la simulación) seguía corriendo cada minuto.
        this.bloqueoMinimo = Duration.ofMillis(Math.max(1, Math.min(60_000, intervaloMs)));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void alArrancar() {
        ejecucionUnica.ejecutar(TAREA, Duration.ofMinutes(5), bloqueoMinimo, this::ponerAlDia);
    }

    /** El primer ciclo espera un intervalo completo: al arrancar ya corrió {@link #alArrancar()}. */
    @Scheduled(initialDelayString = "${aguavigia.estado.puesta-al-dia-ms:300000}",
            fixedDelayString = "${aguavigia.estado.puesta-al-dia-ms:300000}")
    public void ponerAlDiaEnUnaReplica() {
        ejecucionUnica.ejecutar(TAREA, Duration.ofMinutes(5), bloqueoMinimo, this::ponerAlDia);
    }

    void repoblarElContador() {
        try {
            int votos = repoblarContador.repoblar();
            if (votos > 0) {
                log.info("Puesta al día: {} voto(s) recientes devueltos al contador de reportes", votos);
            }
        } catch (RuntimeException fallo) {
            // Sin Redis no hay contador que repoblar; el recálculo desde Mongo igual pone el mapa al día.
            log.warn("No se pudo repoblar el contador de reportes: {}", fallo.toString());
        }
    }

    public void ponerAlDia() {
        // Antes de recalcular: con Redis vaciado (al arrancar, o con el backend arriba si Redis se reinició) el primer reporte nuevo no
        // llegaría al listón del quórum. Es idempotente: no pisa un voto que ya estaba.
        repoblarElContador();
        try {
            int expirados = expirar.expirarVencidos();
            if (expirados > 0) {
                log.info("Puesta al día: {} corte(s) expirados", expirados);
            }
        } catch (RuntimeException fallo) {
            // Un fallo aquí no puede impedir que se recalculen los barrios.
            log.warn("No se pudieron expirar los cortes vencidos: {}", fallo.toString());
        }
        try {
            int cambiados = ponerAlDia.ponerAlDia();
            if (cambiados > 0) {
                log.info("Puesta al día: {} barrio(s) cambiaron de estado", cambiados);
            }
        } catch (RuntimeException fallo) {
            // Sin este catch Spring deja de reprogramar la tarea y el mapa no se pone al día hasta el reinicio.
            log.warn("La puesta al día de los barrios falló, se reintenta en el próximo ciclo: {}", fallo.toString());
        }
    }
}
