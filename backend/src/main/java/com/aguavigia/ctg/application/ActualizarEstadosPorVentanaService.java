package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.port.in.ActualizarEstadosPorVentanaUseCase;
import com.aguavigia.ctg.domain.port.in.RecalcularSectorUseCase;
import com.aguavigia.ctg.domain.port.out.CorteAguaRepository;
import com.aguavigia.ctg.domain.port.out.PropuestaIngestaRepository;
import com.aguavigia.ctg.domain.port.out.RelojPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Vuelve a preguntar por los barrios cuya ventana o cuyo corte el paso del tiempo puede haber movido.
 *
 * Es la mitad que faltaba del ciclo: la ingesta detecta y el veedor aprueba, pero entre el «se suspende
 * mañana de 9 a 6» y el momento en que eso ocurre pasan horas que nadie estaba mirando. No decide qué
 * estado corresponde —eso es del resolutor— ni escribe: elige los barrios y delega en
 * {@link RecalcularSectorUseCase}, el único escritor del estado.
 */
public class ActualizarEstadosPorVentanaService implements ActualizarEstadosPorVentanaUseCase {

    private static final Logger log = LoggerFactory.getLogger(ActualizarEstadosPorVentanaService.class);

    private final PropuestaIngestaRepository propuestas;
    private final CorteAguaRepository cortes;
    private final RecalcularSectorUseCase recalcular;
    private final RelojPort reloj;
    private final Duration memoria;

    /**
     * @param memoria cuánto después del fin prometido una ventana sigue pesando: es el plazo de expiración del
     *                resolutor, porque hasta entonces un corte sin confirmar sigue afirmando «sin servicio»
     */
    public ActualizarEstadosPorVentanaService(PropuestaIngestaRepository propuestas, CorteAguaRepository cortes,
                                              RecalcularSectorUseCase recalcular, RelojPort reloj, Duration memoria) {
        this.propuestas = propuestas;
        this.cortes = cortes;
        this.recalcular = recalcular;
        this.reloj = reloj;
        this.memoria = memoria;
    }

    @Override
    public int aplicarVentanasVencidas() {
        Instant ahora = reloj.ahora();

        // Un barrio puede estar en un boletín y en un corte a la vez: se recalcula una sola vez.
        Set<SectorId> aRecalcular = new LinkedHashSet<>();
        propuestas.listarAprobadasConVentanaVigente(ahora.minus(memoria))
                .forEach(propuesta -> aRecalcular.add(propuesta.sectorId()));
        cortes.listarAbiertos()
                .forEach(corte -> aRecalcular.addAll(corte.sectoresAfectados()));

        int cambiados = 0;
        for (SectorId sectorId : aRecalcular) {
            try {
                if (recalcular.recalcular(sectorId).cambioElEstado()) {
                    cambiados++;
                }
            } catch (RuntimeException fallo) {
                // Un barrio que falla (p. ej. un boletín que apunta a uno que ya no existe) no puede dejar sin
                // revisar a los demás: el barrido vuelve a intentarlo en el próximo ciclo.
                log.warn("No se pudo recalcular el barrio '{}' en el barrido de ventanas: {}",
                        sectorId.valor(), fallo.toString());
            }
        }
        return cambiados;
    }
}
