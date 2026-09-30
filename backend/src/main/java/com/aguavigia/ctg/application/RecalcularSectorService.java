package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.Afirmacion;
import com.aguavigia.ctg.domain.Afirmacion.CorteVeedor;
import com.aguavigia.ctg.domain.Afirmacion.PrensaAprobada;
import com.aguavigia.ctg.domain.Afirmacion.QuorumVecinos;
import com.aguavigia.ctg.domain.Afirmacion.RestablecimientoOficial;
import com.aguavigia.ctg.domain.Afirmacion.VentanaOficial;
import com.aguavigia.ctg.domain.CierreDeCorte;
import com.aguavigia.ctg.domain.CorteAgua;
import com.aguavigia.ctg.domain.CorteId;
import com.aguavigia.ctg.domain.EstadoCorte;
import com.aguavigia.ctg.domain.EstadoPublicado;
import com.aguavigia.ctg.domain.EstadoServicio;
import com.aguavigia.ctg.domain.EstrategiaConsenso;
import com.aguavigia.ctg.domain.EventoBitacora;
import com.aguavigia.ctg.domain.EventoBitacoraFactory;
import com.aguavigia.ctg.domain.MarcasDeEstado;
import com.aguavigia.ctg.domain.OrigenCorte;
import com.aguavigia.ctg.domain.OrigenEstado;
import com.aguavigia.ctg.domain.PropuestaIngesta;
import com.aguavigia.ctg.domain.ReporteCiudadano;
import com.aguavigia.ctg.domain.ReporteId;
import com.aguavigia.ctg.domain.ResolutorDeEstadoSector;
import com.aguavigia.ctg.domain.Sector;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.TipoReporte;
import com.aguavigia.ctg.domain.port.in.RecalcularSectorUseCase;
import com.aguavigia.ctg.domain.port.in.RegistrarEventoBitacoraUseCase;
import com.aguavigia.ctg.domain.port.out.CorteAguaRepository;
import com.aguavigia.ctg.domain.port.out.PropuestaIngestaRepository;
import com.aguavigia.ctg.domain.port.out.RelojPort;
import com.aguavigia.ctg.domain.port.out.ReporteCiudadanoRepository;
import com.aguavigia.ctg.domain.port.out.SectorRepository;
import com.aguavigia.ctg.domain.port.out.TransaccionPort;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * El único escritor del estado de un barrio. Antes lo escribían cuatro procesos (consenso, barrido por
 * ventana, corte del veedor y aprobación de boletines) sin una regla común y se pisaban entre sí; ahora
 * ellos solo aportan afirmaciones y esta clase las reúne, deja que {@link ResolutorDeEstadoSector} decida
 * y escribe <b>una vez</b>, con compare-and-set para que dos recálculos simultáneos no dupliquen el evento.
 *
 * Solo el cambio de estado avisa a los suscriptores y se anexa a la bitácora; las marcas (una disputa que
 * se abre, una promesa que vence) se guardan sin ruido.
 *
 * Límites conocidos: un boletín que declara presión baja con ventana no se puede representar todavía
 * (el resolutor solo conoce la ventana de corte), y una ventana sin fin declarado tampoco; ninguno de los
 * dos afirma nada del presente.
 */
public class RecalcularSectorService implements RecalcularSectorUseCase {

    /**
     * Verificar en cada recálculo escribiría en Mongo por cada reporte de una avería masiva. Una vez cada
     * pocos minutos basta para una advertencia que salta a las 24 horas (ADR-073).
     */
    private static final Duration INTERVALO_ENTRE_VERIFICACIONES = Duration.ofMinutes(5);

    private final SectorRepository sectores;
    private final CorteAguaRepository cortes;
    private final PropuestaIngestaRepository propuestas;
    private final ReporteCiudadanoRepository reportes;
    private final EstrategiaConsenso estrategia;
    private final ResolutorDeEstadoSector resolutor;
    private final RegistrarEventoBitacoraUseCase registrarEvento;
    private final RelojPort reloj;
    private final TransaccionPort transaccion;
    private final Duration ventanaConsenso;

    public RecalcularSectorService(SectorRepository sectores, CorteAguaRepository cortes,
                                   PropuestaIngestaRepository propuestas, ReporteCiudadanoRepository reportes,
                                   EstrategiaConsenso estrategia, ResolutorDeEstadoSector resolutor,
                                   RegistrarEventoBitacoraUseCase registrarEvento, RelojPort reloj,
                                   TransaccionPort transaccion, Duration ventanaConsenso) {
        this.sectores = sectores;
        this.cortes = cortes;
        this.propuestas = propuestas;
        this.reportes = reportes;
        this.estrategia = estrategia;
        this.resolutor = resolutor;
        this.registrarEvento = registrarEvento;
        this.reloj = reloj;
        this.transaccion = transaccion;
        this.ventanaConsenso = ventanaConsenso;
    }

    @Override
    public EstadoPublicado recalcular(SectorId sectorId) {
        Sector sector = sectores.buscarPorId(sectorId)
                .orElseThrow(() -> new IllegalArgumentException("No existe el sector '" + sectorId.valor() + "'"));
        Instant ahora = reloj.ahora();

        List<PropuestaIngesta> aprobadas = propuestas.listarAprobadasPorSector(sectorId);
        List<CorteAgua> cortesDelSector = cortes.listarPorSector(sectorId);
        VotosDeVecinos vecinos = votosDeVecinos(sector, ahora);

        List<Afirmacion> afirmaciones = new ArrayList<>();
        afirmaciones.addAll(afirmacionesDeCortes(sectorId, cortesDelSector));
        afirmaciones.addAll(afirmacionesDeBoletines(sectorId, aprobadas, cortesDelSector));
        afirmaciones.addAll(vecinos.quorums());

        EstadoPublicado publicado = resolutor.resolver(afirmaciones, ahora);
        aplicar(sector, publicado, aprobadas, vecinos, ahora);
        return publicado;
    }

    // --- qué afirma cada fuente ---------------------------------------------------------------------

    /**
     * Los cortes del veedor y los oficiales entran directos. Los de ingesta no: su ventana llega por el
     * boletín, que sabe de qué fuente es, y aquí solo interesa su cierre. Un corte anulado o expirado
     * ya no afirma nada.
     */
    private static List<Afirmacion> afirmacionesDeCortes(SectorId sectorId, List<CorteAgua> cortes) {
        List<Afirmacion> afirmaciones = new ArrayList<>();
        for (CorteAgua corte : cortes) {
            if (!vigenteParaResolver(corte)) {
                continue;
            }
            CierreDeCorte cierre = corte.cierreDe(sectorId).orElse(null);
            if (corte.origen() == OrigenCorte.VEEDOR) {
                afirmaciones.add(new CorteVeedor(corte.ventana().inicio(), corte.ventana().finPrometido(), null, cierre));
            } else if (corte.origen() == OrigenCorte.OFICIAL_ACUACAR) {
                afirmaciones.add(new VentanaOficial(corte.ventana().inicio(), corte.ventana().finPrometido(), cierre));
            }
        }
        return afirmaciones;
    }

    private static boolean vigenteParaResolver(CorteAgua corte) {
        return corte.estado() != EstadoCorte.ANULADO && corte.estado() != EstadoCorte.EXPIRADO;
    }

    /**
     * Un boletín con ventana es una afirmación de corte; uno de Acuacar que dice que el servicio volvió es un
     * restablecimiento. Las buenas noticias piden una fuente más fuerte: una nota de prensa que dice que volvió
     * el agua no cuenta. Sin ventana un boletín es historia, no dice nada del presente.
     */
    private static List<Afirmacion> afirmacionesDeBoletines(SectorId sectorId, List<PropuestaIngesta> aprobadas,
                                                            List<CorteAgua> cortes) {
        Map<CorteId, CorteAgua> cortesPorId = cortes.stream()
                .collect(Collectors.toMap(CorteAgua::id, Function.identity(), (a, b) -> a));
        List<Afirmacion> afirmaciones = new ArrayList<>();
        for (PropuestaIngesta propuesta : aprobadas) {
            if (propuesta.estadoPropuesto() == EstadoServicio.CON_SERVICIO) {
                if (propuesta.esDeFuenteOficial()) {
                    afirmaciones.add(new RestablecimientoOficial(momentoDe(propuesta)));
                }
                continue;
            }
            if (propuesta.estadoPropuesto() != EstadoServicio.SIN_SERVICIO
                    || propuesta.inicioDeclarado() == null || propuesta.finPrometido() == null) {
                continue;
            }
            CorteAgua corte = cortesPorId.get(propuesta.idDelCorte());
            if (corte != null && !vigenteParaResolver(corte)) {
                continue;
            }
            CierreDeCorte cierre = corte == null ? null : corte.cierreDe(sectorId).orElse(null);
            afirmaciones.add(propuesta.esDeFuenteOficial()
                    ? new VentanaOficial(propuesta.inicioDeclarado(), propuesta.finPrometido(), cierre)
                    : new PrensaAprobada(propuesta.inicioDeclarado(), propuesta.finPrometido(), cierre));
        }
        return afirmaciones;
    }

    private static Instant momentoDe(PropuestaIngesta propuesta) {
        return propuesta.publicadoEn() != null ? propuesta.publicadoEn() : propuesta.detectadaEn();
    }

    // --- los vecinos --------------------------------------------------------------------------------

    /**
     * Los quórums de los reportes de la ventana, más el que el barrio ya recuerda. Primero se cuenta
     * (Mongo, sin traer reportes): durante una avería masiva un barrio acumula miles y cargarlos en cada
     * POST agotaba el pool de conexiones. Solo si algún tipo llega a un umbral se traen los reportes, para
     * conocer cuándo reportó el último y cuáles sustentan el cambio.
     */
    private VotosDeVecinos votosDeVecinos(Sector sector, Instant ahora) {
        int umbral = (int) estrategia.umbral(sector);
        int minimoParaCargar = Math.min(umbral, resolutor.reglas().quorumReducido(umbral));

        Map<TipoReporte, Long> votos = reportes.contarVotosRecientes(sector.id(), ventanaConsenso);
        Map<TipoReporte, List<ReporteCiudadano>> sustentoPorTipo = new EnumMap<>(TipoReporte.class);
        Map<TipoReporte, QuorumVecinos> quorums = new EnumMap<>(TipoReporte.class);

        if (votos.values().stream().anyMatch(v -> v >= minimoParaCargar)) {
            porDispositivo(reportes.listarRecientesPorSector(sector.id(), ventanaConsenso)).forEach(
                    (tipo, reportesDelTipo) -> {
                        sustentoPorTipo.put(tipo, reportesDelTipo);
                        Instant primero = reportesDelTipo.stream().map(ReporteCiudadano::timestamp)
                                .min(Comparator.naturalOrder()).orElseThrow();
                        Instant ultimo = reportesDelTipo.stream().map(ReporteCiudadano::timestamp)
                                .max(Comparator.naturalOrder()).orElseThrow();
                        quorums.put(tipo, new QuorumVecinos(tipo, reportesDelTipo.size(), umbral, true, primero, ultimo));
                    });
        }

        recordado(sector).ifPresent(q -> quorums.putIfAbsent(q.tipo(), q));
        return new VotosDeVecinos(List.copyOf(quorums.values()), sustentoPorTipo);
    }

    /** Un voto por dispositivo, el de su reporte más reciente: un vecino que reporta tres veces sigue siendo un vecino. */
    private static Map<TipoReporte, List<ReporteCiudadano>> porDispositivo(List<ReporteCiudadano> recientes) {
        Map<String, ReporteCiudadano> ultimoPorDispositivo = new LinkedHashMap<>();
        for (ReporteCiudadano reporte : recientes) {
            ultimoPorDispositivo.merge(reporte.huella().hash(), reporte,
                    (a, b) -> a.timestamp().isAfter(b.timestamp()) ? a : b);
        }
        return ultimoPorDispositivo.values().stream().collect(Collectors.groupingBy(ReporteCiudadano::tipo));
    }

    /**
     * El quórum a 30 minutos deja de verse pronto, pero el estado que produjo no: el barrio lo recuerda con
     * su origen y respaldo, y solo caduca si ningún reporte nuevo lo renueva (6 h sin verificación, 24 h sin datos).
     */
    private static Optional<QuorumVecinos> recordado(Sector sector) {
        MarcasDeEstado marcas = sector.marcas();
        if (sector.estadoActual() == null || marcas.origen() != OrigenEstado.VECINOS || marcas.respaldo() == null) {
            return Optional.empty();
        }
        Instant ultimo = sector.estadoVerificadoEn() != null ? sector.estadoVerificadoEn() : sector.estadoActualizadoEn();
        if (ultimo == null) {
            ultimo = Instant.EPOCH;
        }
        Instant primero = sector.estadoActualizadoEn() != null ? sector.estadoActualizadoEn() : ultimo;
        return Optional.of(new QuorumVecinos(tipoDe(sector.estadoActual()), marcas.respaldo().vecinos(),
                marcas.respaldo().umbral(), true, primero, ultimo, true));
    }

    private static TipoReporte tipoDe(EstadoServicio estado) {
        return switch (estado) {
            case SIN_SERVICIO, CORTE_PROGRAMADO -> TipoReporte.SIN_AGUA;
            case PRESION_BAJA -> TipoReporte.PRESION_BAJA;
            case CON_SERVICIO -> TipoReporte.SERVICIO_RESTABLECIDO;
        };
    }

    private record VotosDeVecinos(List<QuorumVecinos> quorums, Map<TipoReporte, List<ReporteCiudadano>> sustentoPorTipo) {

        /** Los reportes de la ventana que sostienen {@code estado}; vacío si el estado viene de la memoria del barrio. */
        List<ReporteCiudadano> sustentoDe(EstadoServicio estado) {
            return sustentoPorTipo.entrySet().stream()
                    .filter(e -> estadoDe(e.getKey()) == estado)
                    .flatMap(e -> e.getValue().stream())
                    .toList();
        }

        private static EstadoServicio estadoDe(TipoReporte tipo) {
            return switch (tipo) {
                case SIN_AGUA -> EstadoServicio.SIN_SERVICIO;
                case PRESION_BAJA -> EstadoServicio.PRESION_BAJA;
                case SERVICIO_RESTABLECIDO -> EstadoServicio.CON_SERVICIO;
            };
        }
    }

    // --- escribir una vez ---------------------------------------------------------------------------

    private void aplicar(Sector sector, EstadoPublicado publicado, List<PropuestaIngesta> aprobadas,
                         VotosDeVecinos vecinos, Instant ahora) {
        EstadoServicio nuevo = publicado.estado();
        MarcasDeEstado marcas = MarcasDeEstado.de(publicado);

        if (nuevo == sector.estadoActual()) {
            if (!marcas.equals(sector.marcas())) {
                sectores.publicarSiEs(sector.id(), nuevo, nuevo, marcas);
            }
            if (nuevo != null && laFuenteLoSostieneAhora(publicado, vecinos, nuevo)
                    && sector.verificadoAntesDe(ahora.minus(INTERVALO_ENTRE_VERIFICACIONES))) {
                sectores.confirmarEstado(sector.id(), nuevo);
            }
            return;
        }

        // Estado + evento en la misma transacción: si el registro del evento falla, revierte también el
        // estado — sin esto quedaba un cambio publicado sin la cita que lo sustenta en la bitácora (RF028).
        transaccion.ejecutar(() -> {
            if (!sectores.publicarSiEs(sector.id(), sector.estadoActual(), nuevo, marcas)) {
                return false;
            }
            eventoPara(sector, publicado, aprobadas, vecinos, ahora).ifPresent(registrarEvento::registrar);
            return true;
        });
    }

    /**
     * Un estado de los vecinos solo se verifica con reportes nuevos que lo respalden: si se renovara desde la
     * memoria del barrio no caducaría nunca. Un boletín o un corte vigentes sí verifican: siguen afirmándolo.
     */
    private static boolean laFuenteLoSostieneAhora(EstadoPublicado publicado, VotosDeVecinos vecinos,
                                                  EstadoServicio estado) {
        return publicado.origen() != OrigenEstado.VECINOS || !vecinos.sustentoDe(estado).isEmpty();
    }

    // --- la cita del cambio --------------------------------------------------------------------------

    /**
     * Quién ve el cambio depende de quién lo sostiene: los vecinos dejan el consenso con sus reportes; un
     * boletín, su cita textual. El corte del veedor no anexa nada aquí porque ya dejó su evento al registrarse
     * o cerrarse, y volver a «sin datos» no es una noticia.
     */
    private static Optional<EventoBitacora> eventoPara(Sector sector, EstadoPublicado publicado,
                                                       List<PropuestaIngesta> aprobadas, VotosDeVecinos vecinos,
                                                       Instant ahora) {
        EstadoServicio estado = publicado.estado();
        if (estado == null || publicado.origen() == null) {
            return Optional.empty();
        }
        return switch (publicado.origen()) {
            case VECINOS -> {
                List<ReporteId> ids = vecinos.sustentoDe(estado).stream().map(ReporteCiudadano::id).toList();
                yield ids.isEmpty() ? Optional.empty()
                        : Optional.of(EventoBitacoraFactory.consensoConfirmado(sector.id(), estado, ids, ahora));
            }
            case ACUACAR, PRENSA -> propuestaQueSustenta(publicado, aprobadas)
                    .map(p -> EventoBitacoraFactory.detectadoPorIngesta(sector.id(), sector.nombre(), estado,
                            p.fuente(), p.urlOriginal(), p.imagenUrl(), p.tituloOriginal(), ahora));
            case VEEDOR -> Optional.empty();
        };
    }

    /** El boletín de la fuente que sostiene el estado y cuya ventana es la que se publicó; el más reciente gana. */
    private static Optional<PropuestaIngesta> propuestaQueSustenta(EstadoPublicado publicado, List<PropuestaIngesta> aprobadas) {
        boolean oficial = publicado.origen() == OrigenEstado.ACUACAR;
        return aprobadas.stream()
                .filter(p -> p.esDeFuenteOficial() == oficial)
                .filter(p -> publicado.estado() == EstadoServicio.CON_SERVICIO
                        ? p.estadoPropuesto() == EstadoServicio.CON_SERVICIO
                        : p.estadoPropuesto() == EstadoServicio.SIN_SERVICIO && mismaVentana(p, publicado))
                .max(Comparator.<PropuestaIngesta, Instant>comparing(RecalcularSectorService::momentoDe)
                        .thenComparing(p -> p.id().valor()));
    }

    private static boolean mismaVentana(PropuestaIngesta propuesta, EstadoPublicado publicado) {
        return publicado.ventanaPrometida() != null
                && publicado.ventanaPrometida().inicio().equals(propuesta.inicioDeclarado())
                && publicado.ventanaPrometida().finPrometido().equals(propuesta.finPrometido());
    }
}
