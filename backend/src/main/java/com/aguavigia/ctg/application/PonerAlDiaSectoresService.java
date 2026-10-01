package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.Sector;
import com.aguavigia.ctg.domain.port.in.PonerAlDiaSectoresUseCase;
import com.aguavigia.ctg.domain.port.in.RecalcularSectorUseCase;
import com.aguavigia.ctg.domain.port.out.SectorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * El consenso solo se evalúa cuando llega un reporte y el barrido de ventanas solo mira los barrios con un
 * boletín o un corte abierto: nadie revisa el resto. Esta pasada recalcula todos, que es barato (un barrio
 * sin nada que decir no escribe) y cubre lo que los otros disparadores no ven.
 */
public class PonerAlDiaSectoresService implements PonerAlDiaSectoresUseCase {

    private static final Logger log = LoggerFactory.getLogger(PonerAlDiaSectoresService.class);

    private final SectorRepository sectores;
    private final RecalcularSectorUseCase recalcular;

    public PonerAlDiaSectoresService(SectorRepository sectores, RecalcularSectorUseCase recalcular) {
        this.sectores = sectores;
        this.recalcular = recalcular;
    }

    @Override
    public int ponerAlDia() {
        int cambiados = 0;
        for (Sector sector : sectores.listarTodos()) {
            try {
                if (recalcular.recalcular(sector.id()).cambioElEstado()) {
                    cambiados++;
                }
            } catch (RuntimeException fallo) {
                // Un barrio que falla no puede dejar sin revisar a los demás: la próxima pasada lo reintenta.
                log.warn("No se pudo poner al día el barrio '{}': {}", sector.id().valor(), fallo.toString());
            }
        }
        return cambiados;
    }
}
