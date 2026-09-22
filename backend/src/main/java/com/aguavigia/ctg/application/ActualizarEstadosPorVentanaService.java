package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.CorteAgua;
import com.aguavigia.ctg.domain.EstadoServicio;
import com.aguavigia.ctg.domain.EventoBitacoraFactory;
import com.aguavigia.ctg.domain.PropuestaIngesta;
import com.aguavigia.ctg.domain.Sector;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.port.in.ActualizarEstadosPorVentanaUseCase;
import com.aguavigia.ctg.domain.port.in.RegistrarEventoBitacoraUseCase;
import com.aguavigia.ctg.domain.port.out.CorteAguaRepository;
import com.aguavigia.ctg.domain.port.out.PropuestaIngestaRepository;
import com.aguavigia.ctg.domain.port.out.RelojPort;
import com.aguavigia.ctg.domain.port.out.SectorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Barre las propuestas ya aprobadas y pone cada sector en el estado que le toca <b>ahora</b> según
 * la ventana que el boletín prometió.
 *
 * Es la mitad que faltaba del ciclo: la ingesta detecta y el veedor aprueba, pero entre el «se
 * suspende mañana de 9 a 6» y el momento en que eso ocurre pasan horas que nadie estaba mirando.
 *
 * No inventa nada. Solo aplica lo que la fuente ya declaró y un veedor ya aprobó: si la propuesta no
 * traía ventana, el sector no se toca. Y solo escribe cuando el estado cambia de verdad, para no
 * disparar correo, push y SSE en cada barrido.
 */
@Service
public class ActualizarEstadosPorVentanaService implements ActualizarEstadosPorVentanaUseCase {

    private static final Logger log = LoggerFactory.getLogger(ActualizarEstadosPorVentanaService.class);

    /**
     * Hasta un día después del fin prometido se sigue mirando la propuesta: es el margen para
     * devolver el barrio a CON_SERVICIO aunque el barrido no corriera justo al cerrarse la ventana.
     */
    private static final Duration MARGEN_TRAS_EL_FIN = Duration.ofDays(1);

    private final PropuestaIngestaRepository propuestas;
    private final SectorRepository sectores;
    private final RegistrarEventoBitacoraUseCase registrarEvento;
    private final CorteAguaRepository cortes;
    private final RelojPort reloj;

    public ActualizarEstadosPorVentanaService(PropuestaIngestaRepository propuestas,
                                               SectorRepository sectores,
                                               RegistrarEventoBitacoraUseCase registrarEvento,
                                               CorteAguaRepository cortes,
                                               RelojPort reloj) {
        this.propuestas = propuestas;
        this.sectores = sectores;
        this.registrarEvento = registrarEvento;
        this.cortes = cortes;
        this.reloj = reloj;
    }

    @Override
    public int aplicarVentanasVencidas() {
        Instant ahora = reloj.ahora();
        int cambiados = 0;

        // Una sola lectura de los 211 sectores (ya cacheada, TTL 15s) en vez de un
        // `buscarPorId` por propuesta — mismo antipatrón N+1 que GestionarCorteOficialService.
        Map<SectorId, Sector> sectoresPorId = sectores.listarTodos().stream()
                .collect(Collectors.toMap(Sector::id, Function.identity()));

        // Agrupadas por sector: dos boletines aprobados pueden solaparse sobre el mismo barrio (uno
        // extiende el corte que el otro ya había anunciado), y aplicarlos uno a la vez dejaba el
        // resultado a merced del orden en que Mongo los devolviera — el último procesado ganaba.
        Map<SectorId, List<PropuestaIngesta>> propuestasPorSector = propuestas
                .listarAprobadasConVentanaVigente(ahora.minus(MARGEN_TRAS_EL_FIN)).stream()
                .collect(Collectors.groupingBy(PropuestaIngesta::sectorId));

        // Una sola consulta ($in) por los cortes que tocan cualquiera de estos sectores, en vez de un
        // listarPorSectores dentro del for — mismo antipatrón N+1 que GestionarCorteOficialService
        // ya evita.
        Map<SectorId, List<CorteAgua>> cortesPorSector = agruparPorSector(
                cortes.listarPorSectores(new ArrayList<>(propuestasPorSector.keySet())));

        for (Map.Entry<SectorId, List<PropuestaIngesta>> entrada : propuestasPorSector.entrySet()) {
            SectorId sectorId = entrada.getKey();
            Sector sector = sectoresPorId.get(sectorId);
            if (sector == null) {
                continue;
            }

            List<PropuestaIngesta> propuestasDelSector = entrada.getValue();
            EstadoServicio queCorresponde = propuestasDelSector.stream()
                    .map(p -> p.estadoVigenteEn(ahora))
                    .reduce(EstadoServicio::masSevero)
                    .orElseThrow();

            // Un corte oficial del veedor (RF016) todavía abierto sobre este sector es una señal más
            // autorizada que un boletín de ingesta: no debe rebajarse el estado que ese corte exige,
            // aunque la ventana del boletín ya haya vencido según su propia fecha.
            queCorresponde = plegarConCortesOficialesAbiertos(
                    cortesPorSector.getOrDefault(sectorId, List.of()), ahora, queCorresponde);

            if (sector.estadoActual() == queCorresponde) {
                continue;
            }

            sectores.guardar(sector.conEstado(queCorresponde));
            cambiados++;

            // El estado ganador puede venir solo del corte oficial (ninguna propuesta de ingesta lo
            // sustenta): pasa cuando un fallo parcial de GestionarCorteOficialService dejó el sector
            // desincronizado del corte que sigue abierto (ver su propio javadoc). Sanea el sector sin
            // fabricar una cita de ingesta que no respalda el cambio — ese corte ya anexó su propio
            // evento a la bitácora al registrarse.
            Optional<PropuestaIngesta> sustento = propuestaQueSustenta(propuestasDelSector, ahora, queCorresponde);
            if (sustento.isEmpty()) {
                log.info("Sector '{}' saneado a {} por un corte oficial abierto, sin propuesta de ingesta que lo sustente",
                        sectorId.valor(), queCorresponde);
                continue;
            }

            registrarEvento.registrar(EventoBitacoraFactory.detectadoPorIngesta(
                    sectorId, sector.nombre(), queCorresponde, sustento.get().fuente(),
                    sustento.get().urlOriginal(), sustento.get().imagenUrl(), sustento.get().tituloOriginal(), ahora));

            log.info("Ventana declarada aplicada: '{}' pasa a {} (fuente: {})",
                    sectorId.valor(), queCorresponde, sustento.get().fuente());
        }

        return cambiados;
    }

    private static EstadoServicio plegarConCortesOficialesAbiertos(List<CorteAgua> cortesDelSector, Instant ahora,
                                                                     EstadoServicio queCorresponde) {
        EstadoServicio resultado = queCorresponde;
        for (CorteAgua corte : cortesDelSector) {
            if (corte.ventana().estaCerrada()) {
                continue;
            }
            EstadoServicio estadoDelCorte = corte.ventana().inicio().isAfter(ahora)
                    ? EstadoServicio.CORTE_PROGRAMADO
                    : EstadoServicio.SIN_SERVICIO;
            resultado = EstadoServicio.masSevero(resultado, estadoDelCorte);
        }
        return resultado;
    }

    private static Map<SectorId, List<CorteAgua>> agruparPorSector(List<CorteAgua> cortesEncontrados) {
        Map<SectorId, List<CorteAgua>> agrupados = new HashMap<>();
        for (CorteAgua corte : cortesEncontrados) {
            for (SectorId sectorId : corte.sectoresAfectados()) {
                agrupados.computeIfAbsent(sectorId, s -> new ArrayList<>()).add(corte);
            }
        }
        return agrupados;
    }

    /**
     * Entre las propuestas del sector que de verdad sustentan el resultado (las demás quedaron
     * dominadas por una más severa), la más reciente por lo que su boletín declaró — nunca se cita
     * una URL que no respalda el estado que en verdad se está publicando (`ADR-006`). El id
     * desempata de forma estable si dos boletines declaran la misma fecha.
     */
    private static Optional<PropuestaIngesta> propuestaQueSustenta(List<PropuestaIngesta> propuestasDelSector,
                                                                     Instant ahora, EstadoServicio resultado) {
        return propuestasDelSector.stream()
                .filter(p -> p.estadoVigenteEn(ahora) == resultado)
                .max(Comparator.<PropuestaIngesta, Instant>comparing(
                                p -> p.publicadoEn() != null ? p.publicadoEn() : p.detectadaEn())
                        .thenComparing(p -> p.id().valor()));
    }
}
