package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.EstadoServicio;
import com.aguavigia.ctg.domain.EstrategiaConsenso;
import com.aguavigia.ctg.domain.EventoBitacoraFactory;
import com.aguavigia.ctg.domain.ReporteCiudadano;
import com.aguavigia.ctg.domain.ReporteId;
import com.aguavigia.ctg.domain.Sector;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.ResultadoConsenso;
import com.aguavigia.ctg.domain.TipoReporte;
import com.aguavigia.ctg.domain.port.in.EvaluarConsensoUseCase;
import com.aguavigia.ctg.domain.port.in.RegistrarEventoBitacoraUseCase;
import com.aguavigia.ctg.domain.port.out.ContadorReportesPort;
import com.aguavigia.ctg.domain.port.out.RelojPort;
import com.aguavigia.ctg.domain.port.out.ReservaDeEvaluacionPort;
import com.aguavigia.ctg.domain.port.out.ReporteCiudadanoRepository;
import com.aguavigia.ctg.domain.port.out.SectorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * RF009-RF011 — cambia el estado de un sector cuando suficientes reportes independientes
 * coinciden en una ventana de tiempo. "Coinciden" no exige el mismo `TipoReporte` exacto: el
 * nuevo estado es el que sostiene la mayoría de los reportes recientes, para que un reporte
 * aislado de signo contrario no bloquee el consenso.
 *
 * No notifica suscriptores directamente: sectores.guardar() publica SectorActualizadoEvent
 * cuando el estado cambia, y NotificarSuscripcionesService es su único suscriptor. Ese evento
 * también alimenta SSE y push, así que es el único disparador — duplicarlo aquí mandaba dos
 * correos por cada cambio de estado.
 */
@Service
public class EvaluarConsensoService implements EvaluarConsensoUseCase {

    private static final Logger log = LoggerFactory.getLogger(EvaluarConsensoService.class);

    private final SectorRepository sectores;
    private final ReporteCiudadanoRepository reportes;
    private final ContadorReportesPort contadorReportes;
    private final ReservaDeEvaluacionPort reserva;
    private final EstrategiaConsenso estrategia;
    private final RegistrarEventoBitacoraUseCase registrarEvento;
    private final RelojPort reloj;
    private final Duration ventanaConsenso;

    public EvaluarConsensoService(SectorRepository sectores,
                                   ReporteCiudadanoRepository reportes,
                                   ContadorReportesPort contadorReportes,
                                   ReservaDeEvaluacionPort reserva,
                                   EstrategiaConsenso estrategia,
                                   RegistrarEventoBitacoraUseCase registrarEvento,
                                   RelojPort reloj,
                                   @Value("${aguavigia.consenso.ventana-minutos:30}") long ventanaMinutos) {
        this.sectores = sectores;
        this.reportes = reportes;
        this.contadorReportes = contadorReportes;
        this.reserva = reserva;
        this.estrategia = estrategia;
        this.registrarEvento = registrarEvento;
        this.reloj = reloj;
        this.ventanaConsenso = Duration.ofMinutes(ventanaMinutos);
    }

    @Override
    public ResultadoConsenso evaluar(SectorId sectorId) {
        if (!reserva.reservar(sectorId)) {
            reserva.dejarPendiente(sectorId);
            return new ResultadoConsenso(sectorId, false, null, List.of());
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

        long reportesRecientes = contadorReportes.contarRecientes(sectorId, ventanaConsenso);
        if (!estrategia.seAlcanzaConsenso(reportesRecientes, sector)) {
            return new ResultadoConsenso(sectorId, false, null, List.of());
        }

        // Mongo cuenta los votos por tipo (una fila por tipo) y solo si el estado va a cambiar se traen
        // los reportes que lo sustentan. En una avería masiva casi todos los POST llegan con el sector
        // ya al umbral y sin nada nuevo que decidir; cargar entonces los miles de reportes de la ventana
        // en cada uno agotaba el pool de conexiones.
        Map<TipoReporte, Long> votos = reportes.contarVotosRecientes(sectorId, ventanaConsenso);
        long vecinos = votos.values().stream().mapToLong(Long::longValue).sum();
        if (!estrategia.seAlcanzaConsenso(vecinos, sector)
                || estadoPorMayoria(votos, sector.estadoActual()) == sector.estadoActual()) {
            return new ResultadoConsenso(sectorId, false, null, List.of());
        }

        List<ReporteCiudadano> sustento = reportes.listarRecientesPorSector(sectorId, ventanaConsenso).stream()
                .collect(Collectors.toMap(
                        reporte -> reporte.huella().hash(),
                        Function.identity(),
                        (primero, segundo) -> primero.timestamp().isAfter(segundo.timestamp()) ? primero : segundo,
                        LinkedHashMap::new))
                .values().stream()
                .toList();

        // Redis es un prefiltro rapido, pero Mongo contiene la evidencia moderada y duradera. Esta
        // segunda comprobacion evita que reportes repetidos o ya descartados sostengan un cambio.
        if (!estrategia.seAlcanzaConsenso(sustento.size(), sector)) {
            return new ResultadoConsenso(sectorId, false, null, List.of());
        }
        EstadoServicio nuevoEstado = estadoPorMayoria(votosDe(sustento), sector.estadoActual());

        // Sin cambio real de estado no hay evento nuevo que anexar a la bitácora (RF028: no editar,
        // pero tampoco duplicar un evento idéntico cada vez que alguien vuelve a evaluar el mismo sector).
        if (nuevoEstado == sector.estadoActual()) {
            return new ResultadoConsenso(sectorId, false, null, List.of());
        }

        // Compare-and-set: si otra peticion ya movio el estado entre nuestra lectura y esta escritura,
        // ese cambio ya quedo anotado en la bitacora y anexarlo otra vez lo duplicaria (RF028).
        if (!sectores.cambiarEstadoSiEs(sectorId, sector.estadoActual(), nuevoEstado)) {
            return new ResultadoConsenso(sectorId, false, null, List.of());
        }

        List<ReporteId> ids = sustento.stream().map(ReporteCiudadano::id).toList();
        registrarEvento.registrar(EventoBitacoraFactory.consensoConfirmado(
                sectorId, nuevoEstado, ids, reloj.ahora()));

        return new ResultadoConsenso(sectorId, true, nuevoEstado, ids);
    }

    private static Map<TipoReporte, Long> votosDe(List<ReporteCiudadano> sustento) {
        return sustento.stream().collect(Collectors.groupingBy(ReporteCiudadano::tipo, Collectors.counting()));
    }

    // Empate entre tipos de reporte = evidencia ambigua, no motivo para cambiar el estado publicado
    // (CLAUDE.md, ética de datos: nada se publica sin poder sustentarlo). El orden de iteración de
    // un HashMap sobre una enum no está garantizado por el JLS, así que resolver el empate por el
    // primer máximo encontrado no era determinista — quedaba a merced del hashing de la JVM.
    private static EstadoServicio estadoPorMayoria(Map<TipoReporte, Long> conteoPorTipo, EstadoServicio estadoActual) {
        long maximo = conteoPorTipo.values().stream()
                .mapToLong(Long::longValue)
                .max()
                .orElseThrow(() -> new IllegalStateException("No hay reportes para sustentar el consenso"));
        List<TipoReporte> mayoritarios = conteoPorTipo.entrySet().stream()
                .filter(entrada -> entrada.getValue() == maximo)
                .map(Map.Entry::getKey)
                .toList();
        if (mayoritarios.size() > 1) {
            return estadoActual;
        }
        return switch (mayoritarios.get(0)) {
            case SIN_AGUA -> EstadoServicio.SIN_SERVICIO;
            case PRESION_BAJA -> EstadoServicio.PRESION_BAJA;
            case SERVICIO_RESTABLECIDO -> EstadoServicio.CON_SERVICIO;
        };
    }
}
