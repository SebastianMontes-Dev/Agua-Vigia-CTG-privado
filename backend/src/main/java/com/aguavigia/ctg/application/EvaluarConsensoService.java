package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.EstrategiaConsenso;
import com.aguavigia.ctg.domain.ReglasDeEstado;
import com.aguavigia.ctg.domain.ResultadoConsenso;
import com.aguavigia.ctg.domain.ResultadoDeRecalculo;
import com.aguavigia.ctg.domain.Sector;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.port.in.EvaluarConsensoUseCase;
import com.aguavigia.ctg.domain.port.in.RecalcularSectorUseCase;
import com.aguavigia.ctg.domain.port.out.ContadorReportesPort;
import com.aguavigia.ctg.domain.port.out.ReservaDeEvaluacionPort;
import com.aguavigia.ctg.domain.port.out.SectorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;

/**
 * RF009-RF011 — el punto de entrada del consenso de vecinos: cada reporte pregunta aquí si vale la pena
 * volver a mirar el barrio. Ya no decide el estado ni lo escribe: eso lo hace {@link RecalcularSectorUseCase},
 * el único escritor, que reúne también los boletines y los cortes para que el consenso no los pise.
 *
 * Lo que sí conserva es lo que evita recalcular de más: la reserva de Redis (una evaluación por intervalo y
 * sector; el resto queda pendiente) y un prefiltro sobre el contador de Redis, que es barato. El listón es el
 * menor de los dos que puede exigir el recálculo —el umbral, o el reducido con que se confirma que volvió el
 * agua—, para que confirmar un restablecimiento no quede tapado por el prefiltro.
 *
 * No notifica suscriptores directamente: el cambio de estado publica SectorActualizadoEvent y
 * NotificarSuscripcionesService es su único suscriptor; duplicarlo aquí mandaba dos correos por cada cambio.
 */
public class EvaluarConsensoService implements EvaluarConsensoUseCase {

    private static final Logger log = LoggerFactory.getLogger(EvaluarConsensoService.class);

    private final SectorRepository sectores;
    private final ContadorReportesPort contadorReportes;
    private final ReservaDeEvaluacionPort reserva;
    private final EstrategiaConsenso estrategia;
    private final ReglasDeEstado reglas;
    private final RecalcularSectorUseCase recalcular;
    private final Duration ventanaConsenso;

    public EvaluarConsensoService(SectorRepository sectores,
                                  ContadorReportesPort contadorReportes,
                                  ReservaDeEvaluacionPort reserva,
                                  EstrategiaConsenso estrategia,
                                  ReglasDeEstado reglas,
                                  RecalcularSectorUseCase recalcular,
                                  long ventanaMinutos) {
        this.sectores = sectores;
        this.contadorReportes = contadorReportes;
        this.reserva = reserva;
        this.estrategia = estrategia;
        this.reglas = reglas;
        this.recalcular = recalcular;
        this.ventanaConsenso = Duration.ofMinutes(ventanaMinutos);
    }

    @Override
    public ResultadoConsenso evaluar(SectorId sectorId) {
        if (!reserva.reservar(sectorId)) {
            reserva.dejarPendiente(sectorId);
            return noAlcanzado(sectorId);
        }
        return evaluarAhora(sectorId);
    }

    @Override
    public void evaluarPendientes() {
        for (SectorId sectorId : reserva.tomarPendientes()) {
            try {
                evaluarAhora(sectorId);
            } catch (RuntimeException fallo) {
                log.warn("No se pudo evaluar el consenso pendiente de '{}'; se reintenta en el próximo barrido: {}",
                        sectorId.valor(), fallo.toString());
                reserva.dejarPendiente(sectorId);
            }
        }
    }

    private ResultadoConsenso evaluarAhora(SectorId sectorId) {
        Sector sector = sectores.buscarPorId(sectorId)
                .orElseThrow(() -> new IllegalArgumentException("No existe el sector '" + sectorId.valor() + "'"));

        int umbral = (int) estrategia.umbral(sector);
        long liston = Math.min(umbral, reglas.quorumReducido(umbral));
        if (contadorReportes.contarRecientes(sectorId, ventanaConsenso) < liston) {
            return noAlcanzado(sectorId);
        }

        ResultadoDeRecalculo resultado = recalcular.recalcular(sectorId);
        if (!resultado.cambioElEstado()) {
            return noAlcanzado(sectorId);
        }
        return new ResultadoConsenso(sectorId, true, resultado.publicado().estado(), resultado.reportesQueSustentan());
    }

    private static ResultadoConsenso noAlcanzado(SectorId sectorId) {
        return new ResultadoConsenso(sectorId, false, null, List.of());
    }
}
