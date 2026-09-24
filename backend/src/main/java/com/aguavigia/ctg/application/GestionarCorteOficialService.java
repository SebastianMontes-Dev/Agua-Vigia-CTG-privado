package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.EntidadNoEncontradaException;
import com.aguavigia.ctg.domain.CorteAgua;
import com.aguavigia.ctg.domain.CorteId;
import com.aguavigia.ctg.domain.EstadoServicio;
import com.aguavigia.ctg.domain.EventoBitacora;
import com.aguavigia.ctg.domain.EventoBitacoraFactory;
import com.aguavigia.ctg.domain.Sector;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.port.in.GestionarCorteOficialUseCase;
import com.aguavigia.ctg.domain.port.in.RegistrarEventoBitacoraUseCase;
import com.aguavigia.ctg.domain.port.out.CorteAguaRepository;
import com.aguavigia.ctg.domain.port.out.RelojPort;
import com.aguavigia.ctg.domain.port.out.SectorRepository;
import com.aguavigia.ctg.domain.port.out.TransaccionPort;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * RF016-RF017 — el veedor registra un corte oficial y lo cierra con la hora real de
 * restablecimiento. `CorteAgua` llega ya construido (el `Builder` del dominio impone sus propias
 * invariantes, como `RegistrarReporteService` deja que el constructor de `ReporteCiudadano`
 * imponga las suyas); este servicio solo aporta la regla que cruza agregados: los sectores
 * afectados tienen que existir.
 *
 * RF026: cada registro y cada cierre anexan un evento a la bitácora pública — uno por sector
 * afectado, porque `EventoBitacora.sectorId` es singular y un corte puede tocar varios sectores a
 * la vez. Misma dependencia de `RegistrarEventoBitacoraUseCase` que `RegistrarReporteService` usa
 * para `EvaluarConsensoUseCase`: un caso de uso dispara a otro, no a su repositorio directamente.
 *
 * RF001: registrar o cerrar un corte también mueve el estado de los sectores afectados. Antes no
 * lo hacía, y `EstadoServicio.CORTE_PROGRAMADO` no se asignaba en ninguna parte del sistema: el
 * mapa no distinguía un corte anunciado de un barrio con servicio normal.
 */
public class GestionarCorteOficialService implements GestionarCorteOficialUseCase {

    private final CorteAguaRepository cortes;
    private final SectorRepository sectores;
    private final RegistrarEventoBitacoraUseCase registrarEvento;
    private final RelojPort reloj;
    private final TransaccionPort transaccion;

    public GestionarCorteOficialService(CorteAguaRepository cortes,
                                         SectorRepository sectores,
                                         RegistrarEventoBitacoraUseCase registrarEvento,
                                         RelojPort reloj,
                                         TransaccionPort transaccion) {
        this.cortes = cortes;
        this.sectores = sectores;
        this.registrarEvento = registrarEvento;
        this.reloj = reloj;
        this.transaccion = transaccion;
    }

    @Override
    public CorteAgua registrar(CorteAgua corte) {
        // Una sola lectura de todos los sectores (ya cacheada, ver SectorMongoAdapter) en vez de
        // un buscarPorId por sector afectado — un corte de 20 barrios hacía 20 round-trips solo
        // para validar existencia.
        Map<SectorId, Sector> sectoresPorId = indiceDeSectores();
        for (SectorId sectorId : corte.sectoresAfectados()) {
            if (!sectoresPorId.containsKey(sectorId)) {
                throw new IllegalArgumentException("No existe el sector '" + sectorId.valor() + "'");
            }
        }

        CorteAgua guardado = cortes.guardar(corte);
        // Un corte anunciado para dentro de tres días no deja el barrio sin agua hoy: es
        // CORTE_PROGRAMADO hasta que llega su hora. Si el veedor registra uno que ya empezó
        // (pasa: primero se corta el agua, después alguien lo anuncia), nace SIN_SERVICIO.
        EstadoServicio estado = guardado.ventana().inicio().isAfter(reloj.ahora())
                ? EstadoServicio.CORTE_PROGRAMADO
                : EstadoServicio.SIN_SERVICIO;
        anexarYMoverEstado(guardado, estado, sectoresPorId,
                sectorId -> EventoBitacoraFactory.corteAnunciado(guardado, sectorId, reloj.ahora()));
        return guardado;
    }

    @Override
    public CorteAgua cerrar(CorteId corteId, Instant horaReal) {
        CorteAgua corte = cortes.buscarPorId(corteId)
                .orElseThrow(() -> new EntidadNoEncontradaException(
                        "No existe el corte '" + corteId.valor() + "'"));

        CorteAgua cerrado = corte.cerrar(horaReal);

        CorteAgua guardado = cortes.guardar(cerrado);
        anexarYMoverEstado(guardado, EstadoServicio.CON_SERVICIO, indiceDeSectores(),
                sectorId -> EventoBitacoraFactory.corteRestablecido(guardado, sectorId, reloj.ahora()));
        return guardado;
    }

    private Map<SectorId, Sector> indiceDeSectores() {
        return sectores.listarTodos().stream().collect(Collectors.toMap(Sector::id, Function.identity()));
    }

    /**
     * Anexa el evento a la bitácora y mueve el estado de cada sector afectado, todo en una sola
     * transacción multi-documento (Fase 3 de `plan-validacion-backend.md`): si falla a mitad del
     * `for`, revierte también los sectores ya procesados en esta misma llamada — antes quedaban
     * "algunos sectores movidos de estado y otros no", sin nada que lo reconciliara.
     *
     * No notifica suscriptores aquí: guardar el sector publica `SectorActualizadoEvent` y
     * `NotificarSuscripcionesService` es su único suscriptor. Este servicio recorría además las
     * suscripciones a mano, así que cada corte mandaba dos correos al mismo vecino — y con el
     * estado viejo, porque nadie estaba cambiando el sector: el aviso decía "cambió su estado a:
     * Desconocido" en cualquier barrio que todavía no tuviera estado registrado. Es la misma
     * corrección que se le hizo a `EvaluarConsensoService`. `SectorMongoAdapter` difiere ese
     * evento (y la invalidación de caché) hasta que esta transacción confirme.
     */
    private void anexarYMoverEstado(CorteAgua corte, EstadoServicio nuevoEstado, Map<SectorId, Sector> sectoresPorId,
                                     Function<SectorId, EventoBitacora> eventoDe) {
        // Una sola consulta ($in) por los cortes que tocan cualquiera de estos sectores, en vez de
        // un listarPorSector por sector dentro del for — mismo antipatrón que arriba.
        Map<SectorId, List<CorteAgua>> otrosCortesPorSector = agruparPorSector(
                cortes.listarPorSectores(corte.sectoresAfectados()));

        transaccion.ejecutar(() -> {
            for (SectorId sectorId : corte.sectoresAfectados()) {
                registrarEvento.registrar(eventoDe.apply(sectorId));
                EstadoServicio estadoReal = sinDegradarPorOtrosCortesAbiertos(
                        sectorId, corte, nuevoEstado, otrosCortesPorSector.getOrDefault(sectorId, List.of()));
                Sector sector = sectoresPorId.get(sectorId);
                if (sector != null && sector.estadoActual() != estadoReal) {
                    sectores.guardar(sector.conEstado(estadoReal));
                }
            }
            return null;
        });
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
     * Un sector puede estar afectado por más de un corte a la vez (uno masivo, uno local sobre el
     * mismo barrio). Cerrar o reprogramar el corte que se está tocando no debe pisar el estado del
     * sector si otro corte distinto sigue abierto sobre él: cerrar el corte local mientras el
     * masivo seguía abierto dejaba el sector en `CON_SERVICIO` aunque en la realidad seguía sin
     * agua — el falso positivo que `ADR-014` prohíbe. Se calcula el estado más severo entre el que
     * se iba a aplicar y el de cada otro corte todavía abierto sobre el mismo sector.
     */
    private EstadoServicio sinDegradarPorOtrosCortesAbiertos(SectorId sectorId, CorteAgua corteActual,
                                                               EstadoServicio nuevoEstado,
                                                               List<CorteAgua> otrosCortesDelSector) {
        EstadoServicio masSevero = nuevoEstado;
        for (CorteAgua otro : otrosCortesDelSector) {
            if (otro.equals(corteActual) || otro.ventana().estaCerrada()) {
                continue;
            }
            EstadoServicio estadoOtro = otro.ventana().inicio().isAfter(reloj.ahora())
                    ? EstadoServicio.CORTE_PROGRAMADO
                    : EstadoServicio.SIN_SERVICIO;
            masSevero = EstadoServicio.masSevero(masSevero, estadoOtro);
        }
        return masSevero;
    }
}
