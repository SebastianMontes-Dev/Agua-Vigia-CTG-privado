package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.Afirmacion;
import com.aguavigia.ctg.domain.Afirmacion.CorteVeedor;
import com.aguavigia.ctg.domain.Afirmacion.PrensaAprobada;
import com.aguavigia.ctg.domain.Afirmacion.QuorumVecinos;
import com.aguavigia.ctg.domain.ComposicionDelSustento;
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
import com.aguavigia.ctg.domain.RespaldoVecinal;
import com.aguavigia.ctg.domain.ResolutorDeEstadoSector;
import com.aguavigia.ctg.domain.ResultadoDeRecalculo;
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
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * El único escritor del estado de un barrio. Antes lo escribían cuatro procesos (consenso, barrido por
 * ventana, corte del veedor y aprobación de boletines) sin una regla común y se pisaban entre sí; ahora
 * ellos solo aportan afirmaciones y esta clase las reúne, deja que {@link ResolutorDeEstadoSector} decida
 * y escribe <b>una vez</b>, con compare-and-set para que dos recálculos simultáneos no dupliquen el evento.
 *
 * Solo el cambio de estado avisa a los suscriptores; las marcas (una disputa que se abre, una promesa que
 * vence) se guardan sin ruido, aunque la disputa deja su anotación en la bitácora. Lo que cambia junto al
 * estado —el corte que los vecinos cierran o reabren y su evento— se escribe en la misma transacción.
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
    private final int redesMinimas;

    public RecalcularSectorService(SectorRepository sectores, CorteAguaRepository cortes,
                                   PropuestaIngestaRepository propuestas, ReporteCiudadanoRepository reportes,
                                   EstrategiaConsenso estrategia, ResolutorDeEstadoSector resolutor,
                                   RegistrarEventoBitacoraUseCase registrarEvento, RelojPort reloj,
                                   TransaccionPort transaccion, Duration ventanaConsenso,
                                   int redesMinimas) {
        this.redesMinimas = redesMinimas;
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
    public ResultadoDeRecalculo recalcular(SectorId sectorId) {
        return calcular(sectorId, false);
    }

    @Override
    public ResultadoDeRecalculo reevaluarTrasDescarte(SectorId sectorId) {
        return calcular(sectorId, true);
    }

    private ResultadoDeRecalculo calcular(SectorId sectorId, boolean trasDescarte) {
        Sector sector = sectores.buscarPorId(sectorId)
                .orElseThrow(() -> new IllegalArgumentException("No existe el sector '" + sectorId.valor() + "'"));
        Instant ahora = reloj.ahora();

        List<PropuestaIngesta> aprobadas = propuestas.listarAprobadasPorSector(sectorId);
        List<CorteAgua> cortesDelSector = cortes.listarPorSector(sectorId);

        boolean memoriaDescartada = trasDescarte && !laMemoriaSigueSostenida(sector, ahora);
        VotosDeVecinos vecinos = votosDeVecinos(sector, ahora, !memoriaDescartada);

        Reapertura reapertura = reabrirSiLosVecinosContradicen(sectorId, cortesDelSector, vecinos, ahora);
        if (reapertura.huboReapertura()) {
            cortesDelSector = reapertura.cortes();
            // Lo que los vecinos dijeron de que «ya volvió» —memoria o reportes frescos— es justo lo que acaba de perder.
            vecinos = vecinos.sinElRestablecimiento();
        }

        List<Afirmacion> afirmaciones = new ArrayList<>();
        afirmaciones.addAll(afirmacionesDeCortes(sectorId, cortesDelSector));
        afirmaciones.addAll(afirmacionesDeBoletines(sectorId, aprobadas, cortesDelSector));
        afirmaciones.addAll(vecinos.quorums());

        EstadoPublicado publicado = resolutor.resolver(afirmaciones, ahora);
        publicado = conOrigenDeSensores(sector, publicado, vecinos);
        boolean cambioElEstado = aplicar(sector, publicado, aprobadas, cortesDelSector, reapertura, vecinos,
                memoriaDescartada, ahora);
        List<ReporteId> sustento = cambioElEstado && sostienenLosVotos(publicado.origen())
                ? idsDe(vecinos.sustentoDe(publicado.estado()))
                : List.of();
        return new ResultadoDeRecalculo(publicado, cambioElEstado, sustento);
    }

    private static List<ReporteId> idsDe(List<ReporteCiudadano> reportes) {
        return reportes.stream().map(ReporteCiudadano::id).toList();
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
                afirmaciones.add(new CorteVeedor(corte.ventana().inicio(), corte.ventana().finPrometido(), corte.caducaEn(), cierre));
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
            if (propuesta.inicioDeclarado() == null || propuesta.finPrometido() == null) {
                continue;
            }
            CorteAgua corte = cortesPorId.get(propuesta.idDelCorte());
            if (corte != null && !vigenteParaResolver(corte)) {
                continue;
            }
            CierreDeCorte cierre = corte == null ? null : corte.cierreDe(sectorId).orElse(null);
            afirmaciones.add(propuesta.esDeFuenteOficial()
                    ? new VentanaOficial(propuesta.inicioDeclarado(), propuesta.finPrometido(), cierre, propuesta.estadoPropuesto())
                    : new PrensaAprobada(propuesta.inicioDeclarado(), propuesta.finPrometido(), cierre, propuesta.estadoPropuesto()));
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
     *
     * @param conMemoria si el barrio puede apoyarse en lo que ya recuerda; al descartar reportes se comprueba
     *                   antes y, si dejó de sostenerse, se olvida
     */
    private VotosDeVecinos votosDeVecinos(Sector sector, Instant ahora, boolean conMemoria) {
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
                        quorums.put(tipo, new QuorumVecinos(tipo, reportesDelTipo.size(), umbral,
                                ComposicionDelSustento.cumple(reportesDelTipo, redesMinimas), primero, ultimo));
                    });
        }

        if (conMemoria) {
            // Un quórum fresco que no llega al umbral (los reportes que fijaron el estado van saliendo de la ventana)
            // no puede borrar lo que el barrio ya recuerda: solo un quórum alcanzado ocupa su lugar.
            recordado(sector).ifPresent(memoria ->
                    quorums.merge(memoria.tipo(), memoria, (fresco, recordada) ->
                            fresco.alcanzado() && fresco.composicionValida() ? fresco : recordada));
        }
        return new VotosDeVecinos(quorums, sustentoPorTipo);
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
        if (sector.estadoActual() == null || !sostienenLosVotos(marcas.origen()) || marcas.respaldo() == null) {
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

    /**
     * Al descartar reportes se vuelve a contar, desde que el barrio formó su estado, cuántos vecinos válidos
     * lo sostienen, con el mismo listón con que se fijó: el umbral que se guardó, o el reducido si lo que
     * se confirmó fue un restablecimiento. Sin memoria de los vecinos no hay nada que comprobar.
     */
    private boolean laMemoriaSigueSostenida(Sector sector, Instant ahora) {
        Optional<QuorumVecinos> memoria = recordado(sector);
        if (memoria.isEmpty()) {
            return true;
        }
        QuorumVecinos recordada = memoria.get();
        Instant formacion = sector.estadoActualizadoEn() != null ? sector.estadoActualizadoEn() : recordada.primerReporte();
        Duration desde = Duration.between(formacion.minus(ventanaConsenso), ahora);

        List<ReporteCiudadano> sustento = porDispositivo(reportes.listarRecientesPorSector(sector.id(), desde))
                .getOrDefault(recordada.tipo(), List.of());
        int liston = recordada.estado() == EstadoServicio.CON_SERVICIO
                ? resolutor.reglas().quorumReducido(recordada.umbral())
                : recordada.umbral();
        return sustento.size() >= liston && ComposicionDelSustento.cumple(sustento, redesMinimas);
    }

    private record VotosDeVecinos(Map<TipoReporte, QuorumVecinos> porTipo,
                                  Map<TipoReporte, List<ReporteCiudadano>> sustentoPorTipo) {

        List<QuorumVecinos> quorums() {
            return List.copyOf(porTipo.values());
        }

        /** Los reportes de la ventana que sostienen {@code estado}; vacío si el estado viene de la memoria del barrio. */
        List<ReporteCiudadano> sustentoDe(EstadoServicio estado) {
            return sustentoPorTipo.entrySet().stream()
                    .filter(e -> estadoDe(e.getKey()) == estado)
                    .flatMap(e -> e.getValue().stream())
                    .toList();
        }

        /** Lo que los vecinos no recuerdan pero sí acaban de alcanzar: el quórum de esta ventana, no el de la memoria. */
        Optional<QuorumVecinos> recienteDe(TipoReporte tipo) {
            return Optional.ofNullable(porTipo.get(tipo)).filter(q -> !q.sostenido());
        }

        /** El quórum de restablecimiento se descarta entero: otro mayor lo contradijo y no pueden valer los dos. */
        VotosDeVecinos sinElRestablecimiento() {
            Map<TipoReporte, QuorumVecinos> sinRestablecimiento = new EnumMap<>(TipoReporte.class);
            sinRestablecimiento.putAll(porTipo);
            sinRestablecimiento.remove(TipoReporte.SERVICIO_RESTABLECIDO);
            return new VotosDeVecinos(sinRestablecimiento, sustentoPorTipo);
        }

        int respaldoDeRestablecimiento() {
            QuorumVecinos restablecimiento = porTipo.get(TipoReporte.SERVICIO_RESTABLECIDO);
            return restablecimiento == null ? 0 : restablecimiento.respaldo();
        }

        private static EstadoServicio estadoDe(TipoReporte tipo) {
            return switch (tipo) {
                case SIN_AGUA -> EstadoServicio.SIN_SERVICIO;
                case PRESION_BAJA -> EstadoServicio.PRESION_BAJA;
                case SERVICIO_RESTABLECIDO -> EstadoServicio.CON_SERVICIO;
            };
        }
    }

    /** Los votos los pueden dar vecinos o sensores de la red; el resto de las fuentes no votan. */
    private static boolean sostienenLosVotos(OrigenEstado origen) {
        return origen == OrigenEstado.VECINOS || origen == OrigenEstado.SENSOR;
    }

    /**
     * El resolutor no distingue quién vota: un quórum es un quórum. Pero «según los sensores de la red» no es «según
     * 11 vecinos», así que cuando todos los votos que sostienen el estado entraron por el endpoint de sensores el
     * origen se declara SENSOR. Con un solo vecino de por medio es VECINOS. Si el estado viene de la memoria del
     * barrio (sin reportes que mirar) conserva el origen que ya tenía.
     */
    private static EstadoPublicado conOrigenDeSensores(Sector sector, EstadoPublicado publicado, VotosDeVecinos vecinos) {
        if (publicado.origen() != OrigenEstado.VECINOS) {
            return publicado;
        }
        List<ReporteCiudadano> sustento = vecinos.sustentoDe(publicado.estado());
        OrigenEstado origen = !sustento.isEmpty()
                ? origenDe(sustento)
                : (sector.estadoActual() == publicado.estado() && sector.marcas().origen() == OrigenEstado.SENSOR
                        ? OrigenEstado.SENSOR : OrigenEstado.VECINOS);
        return origen == publicado.origen() ? publicado : publicado.conOrigen(origen);
    }

    private static OrigenEstado origenDe(List<ReporteCiudadano> reportes) {
        return !reportes.isEmpty() && reportes.stream().allMatch(ReporteCiudadano::esSensor)
                ? OrigenEstado.SENSOR : OrigenEstado.VECINOS;
    }

    // --- cerrar y reabrir el corte -------------------------------------------------------------------

    /**
     * Lo que hay que escribir de un corte, como operación y no como copia: el corte se vuelve a leer dentro de la
     * transacción (un veedor pudo confirmarlo, cerrarlo o anularlo desde que se leyó) y la operación se aplica sobre
     * esa lectura. Si ya no admite el cambio, no se escribe nada y la próxima pasada decide con datos frescos.
     */
    private record CambioDeCorte(CorteId id, UnaryOperator<CorteAgua> operacion) {
    }

    /**
     * Los cortes que se reabrieron y la lista del barrio ya con ellos; {@code quorum} es el que los
     * contradijo. Sin reapertura la lista es la original.
     */
    private record Reapertura(List<CorteAgua> cortes, List<CorteAgua> reabiertos, QuorumVecinos quorum,
                              List<CambioDeCorte> cambios) {

        static Reapertura ninguna(List<CorteAgua> cortes) {
            return new Reapertura(cortes, List.of(), null, List.of());
        }

        boolean huboReapertura() {
            return !reabiertos.isEmpty();
        }
    }

    /**
     * D15: un restablecimiento que solo sostenían los vecinos o los sensores (cierre provisional) puede no ser
     * efectivo. Si un quórum nuevo de «sin agua» o «presión baja», posterior al cierre y dentro de la ventana de
     * reapertura, lo contradice, se reabre el mismo corte: una intermitencia no es un evento distinto. Lo que
     * confirmó un veedor o un boletín es definitivo, y pasada la ventana los vecinos solo lo contradicen.
     */
    private Reapertura reabrirSiLosVecinosContradicen(SectorId sectorId, List<CorteAgua> cortesDelSector,
                                                      VotosDeVecinos vecinos, Instant ahora) {
        Duration plazo = resolutor.reglas().ventanaDeReapertura();
        List<CorteAgua> reabiertos = new ArrayList<>();
        List<CorteAgua> resultado = new ArrayList<>();
        List<CambioDeCorte> cambios = new ArrayList<>();
        QuorumVecinos contradice = null;

        for (CorteAgua corte : cortesDelSector) {
            CierreDeCorte cierre = corte.cierreDe(sectorId).orElse(null);
            Optional<QuorumVecinos> quorum = cierre != null && cierre.provisional() && vigenteParaResolver(corte)
                    ? quorumQueContradice(vecinos, cierre, plazo)
                    : Optional.empty();
            if (quorum.isPresent()) {
                CorteAgua reabierto = corte.reabrirSector(sectorId);
                reabiertos.add(reabierto);
                resultado.add(reabierto);
                cambios.add(new CambioDeCorte(corte.id(), actual -> actual.reabrirSector(sectorId)));
                contradice = quorum.get();
            } else {
                resultado.add(corte);
            }
        }
        return reabiertos.isEmpty() ? Reapertura.ninguna(cortesDelSector) : new Reapertura(resultado, reabiertos, contradice, cambios);
    }

    private static Optional<QuorumVecinos> quorumQueContradice(VotosDeVecinos vecinos, CierreDeCorte cierre, Duration plazo) {
        Instant limite = cierre.hora().plus(plazo);
        return Stream.of(TipoReporte.SIN_AGUA, TipoReporte.PRESION_BAJA)
                .map(vecinos::recienteDe)
                .flatMap(Optional::stream)
                .filter(q -> q.composicionValida() && q.alcanzado())
                // Un empate no es una contradicción: sin un quórum mayor el restablecimiento sigue en pie.
                .filter(q -> q.respaldo() > vecinos.respaldoDeRestablecimiento())
                .filter(q -> q.ultimoReporte().isAfter(cierre.hora()) && !q.ultimoReporte().isAfter(limite))
                .max(Comparator.comparingInt(QuorumVecinos::respaldo));
    }

    /**
     * Si los vecinos confirman que volvió el agua pero el corte sigue abierto en el barrio, a las 24 horas
     * —cuando su memoria caduca— el barrio volvería a «sin servicio por confirmar». El cierre provisional,
     * con la hora del primer reporte del grupo, lo evita. Solo se cierra un corte cuya promesa ya venció:
     * antes de eso los vecinos no cierran nada, lo disputan.
     */
    private static List<CambioDeCorte> cerrarCortesPorVecinos(SectorId sectorId, List<CorteAgua> cortesDelSector,
                                                          VotosDeVecinos vecinos, OrigenEstado fuente, Instant ahora) {
        Optional<QuorumVecinos> confirmacion = vecinos.recienteDe(TipoReporte.SERVICIO_RESTABLECIDO);
        if (confirmacion.isEmpty()) {
            return List.of();
        }
        Instant primerReporte = confirmacion.get().primerReporte() != null
                ? confirmacion.get().primerReporte() : confirmacion.get().ultimoReporte();
        List<CambioDeCorte> cerrados = new ArrayList<>();
        for (CorteAgua corte : cortesDelSector) {
            boolean abiertoEnElBarrio = corte.estaAbierto() && corte.cierreDe(sectorId).isEmpty();
            if (abiertoEnElBarrio && !ahora.isBefore(corte.ventana().finPrometido())) {
                Instant hora = primerReporte.isBefore(corte.ventana().inicio()) ? corte.ventana().inicio() : primerReporte;
                CierreDeCorte cierre = new CierreDeCorte(hora, fuente, true);
                cerrados.add(new CambioDeCorte(corte.id(), actual -> actual.cerrarSector(sectorId, cierre)));
            }
        }
        return cerrados;
    }

    // --- escribir una vez ---------------------------------------------------------------------------

    /**
     * Lo que haya que escribir va en una sola transacción y detrás del compare-and-set: quien pierde la carrera
     * no toca nada más. Sin nada que escribir no se abre transacción.
     *
     * @return si este recálculo movió el estado; quien pierde la carrera contra otro proceso no lo movió
     */
    private boolean aplicar(Sector sector, EstadoPublicado publicado, List<PropuestaIngesta> aprobadas,
                            List<CorteAgua> cortesDelSector, Reapertura reapertura, VotosDeVecinos vecinos,
                            boolean memoriaDescartada, Instant ahora) {
        EstadoServicio nuevo = publicado.estado();
        MarcasDeEstado marcas = MarcasDeEstado.de(publicado);
        boolean cambiaElEstado = nuevo != sector.estadoActual();
        boolean cambianLasMarcas = !marcas.equals(sector.marcas());

        List<CambioDeCorte> cambiosDeCortes = new ArrayList<>(reapertura.cambios());
        if (cambiaElEstado && nuevo == EstadoServicio.CON_SERVICIO && sostienenLosVotos(publicado.origen())) {
            cambiosDeCortes.addAll(cerrarCortesPorVecinos(sector.id(), cortesDelSector, vecinos, publicado.origen(), ahora));
        }

        if (!cambiaElEstado && !cambianLasMarcas && cambiosDeCortes.isEmpty()) {
            verificarSiToca(sector, publicado, vecinos, ahora);
            return false;
        }

        List<EventoBitacora> eventos = eventosDelCambio(sector, publicado, aprobadas, reapertura, vecinos,
                cambiaElEstado, memoriaDescartada, ahora);
        boolean publica = cambiaElEstado || cambianLasMarcas;
        // Solo se abre la disputa (el estado no cambia): el compare-and-set no alcanza para que dos recálculos simultáneos
        // no anoten el mismo evento, porque los dos ven el mismo estado; hace falta que además siga sin estar abierta.
        boolean soloAbreLaDisputa = !cambiaElEstado && publicado.enDisputa() && !sector.marcas().enDisputa();

        // Estado + evento en la misma transacción: si el registro del evento falla, revierte también el
        // estado — sin esto quedaba un cambio publicado sin la cita que lo sustenta en la bitácora (RF028).
        boolean movioElEstado = transaccion.ejecutar(() -> {
            // Las lecturas van dentro: si la transacción se reintenta, decide sobre cortes frescos. Antes de escribir
            // nada, para no tener que deshacer el compare-and-set si un corte ya no admite el cambio.
            Optional<List<CorteAgua>> actualizados = reaplicarSobreLosCortesFrescos(cambiosDeCortes);
            if (actualizados.isEmpty()) {
                return false;
            }
            boolean escrito = soloAbreLaDisputa
                    ? sectores.abrirDisputaSiEs(sector.id(), sector.estadoActual(), marcas)
                    : !publica || sectores.publicarSiEs(sector.id(), sector.estadoActual(), nuevo, marcas);
            if (!escrito) {
                return false;
            }
            actualizados.get().forEach(cortes::guardar);
            eventos.forEach(registrarEvento::registrar);
            return cambiaElEstado;
        });
        if (!cambiaElEstado) {
            verificarSiToca(sector, publicado, vecinos, ahora);
        }
        return movioElEstado;
    }

    /** Vacío si algún corte desapareció o ya no admite el cambio: quien lo cambió primero gana. */
    private Optional<List<CorteAgua>> reaplicarSobreLosCortesFrescos(List<CambioDeCorte> cambios) {
        List<CorteAgua> actualizados = new ArrayList<>();
        for (CambioDeCorte cambio : cambios) {
            Optional<CorteAgua> fresco = cortes.buscarPorId(cambio.id());
            if (fresco.isEmpty()) {
                return Optional.empty();
            }
            try {
                actualizados.add(cambio.operacion().apply(fresco.get()));
            } catch (IllegalStateException | IllegalArgumentException yaNoAdmiteElCambio) {
                return Optional.empty();
            }
        }
        return Optional.of(actualizados);
    }

    private void verificarSiToca(Sector sector, EstadoPublicado publicado, VotosDeVecinos vecinos, Instant ahora) {
        EstadoServicio estado = publicado.estado();
        if (estado != null && estado == sector.estadoActual() && laFuenteLoSostieneAhora(publicado, vecinos, estado)
                && sector.verificadoAntesDe(ahora.minus(INTERVALO_ENTRE_VERIFICACIONES))) {
            sectores.confirmarEstado(sector.id(), estado);
        }
    }

    /**
     * Un estado de los vecinos solo se verifica con reportes nuevos que lo respalden: si se renovara desde la
     * memoria del barrio no caducaría nunca. Un boletín o un corte vigentes sí verifican: siguen afirmándolo.
     */
    private static boolean laFuenteLoSostieneAhora(EstadoPublicado publicado, VotosDeVecinos vecinos,
                                                  EstadoServicio estado) {
        return !sostienenLosVotos(publicado.origen()) || !vecinos.sustentoDe(estado).isEmpty();
    }

    // --- la cita del cambio --------------------------------------------------------------------------

    private static List<EventoBitacora> eventosDelCambio(Sector sector, EstadoPublicado publicado,
                                                         List<PropuestaIngesta> aprobadas, Reapertura reapertura,
                                                         VotosDeVecinos vecinos, boolean cambiaElEstado,
                                                         boolean memoriaDescartada, Instant ahora) {
        List<EventoBitacora> eventos = new ArrayList<>();
        if (cambiaElEstado && memoriaDescartada) {
            eventos.add(EventoBitacoraFactory.consensoRevertido(sector.id(), ahora));
        }
        if (reapertura.huboReapertura()) {
            // Reabrir el corte es un hecho de la bitácora aunque el estado ya fuera el que correspondía.
            eventoDeReapertura(sector, reapertura, vecinos, ahora).ifPresent(eventos::add);
        } else if (cambiaElEstado) {
            eventoDelEstado(sector, publicado, aprobadas, vecinos, ahora).ifPresent(eventos::add);
        } else if (publicado.enDisputa() && !sector.marcas().enDisputa()) {
            // Se anota al abrirse la disputa, no en cada minuto que sigue abierta.
            eventos.add(EventoBitacoraFactory.estadoEnDisputa(sector.id(), publicado.estado(),
                    publicado.reportesEnContra(), ahora));
        }
        return eventos;
    }

    /**
     * Quién ve el cambio depende de quién lo sostiene: los vecinos dejan el consenso con sus reportes; un
     * boletín, su cita textual. El corte del veedor no anexa nada aquí porque ya dejó su evento al registrarse
     * o cerrarse, y volver a «sin datos» no es una noticia. Un corte que se reabre lo provocaron los vecinos
     * aunque el estado lo afirme de nuevo la fuente oficial: se cita su reporte, no el boletín de antes.
     */
    private static Optional<EventoBitacora> eventoDelEstado(Sector sector, EstadoPublicado publicado,
                                                            List<PropuestaIngesta> aprobadas,
                                                            VotosDeVecinos vecinos, Instant ahora) {
        EstadoServicio estado = publicado.estado();
        if (estado == null || publicado.origen() == null) {
            return Optional.empty();
        }
        return switch (publicado.origen()) {
            case VECINOS, SENSOR -> {
                List<ReporteId> ids = idsDe(vecinos.sustentoDe(estado));
                if (ids.isEmpty()) {
                    yield Optional.empty();
                }
                EventoBitacora evento = estado == EstadoServicio.CON_SERVICIO
                        ? EventoBitacoraFactory.restablecimientoPorVecinos(sector.id(), ids, publicado.respaldo(), ahora)
                        : EventoBitacoraFactory.consensoConfirmado(sector.id(), estado, ids, publicado.respaldo(), ahora);
                yield Optional.of(evento.conFuente(publicado.origen(), publicado.respaldo()));
            }
            case ACUACAR, PRENSA -> propuestaQueSustenta(publicado, aprobadas)
                    .map(p -> EventoBitacoraFactory.detectadoPorIngesta(sector.id(), sector.nombre(), estado,
                            p.fuente(), p.urlOriginal(), p.imagenUrl(), p.tituloOriginal(), ahora));
            case VEEDOR -> Optional.empty();
        };
    }

    /**
     * Un corte que se reabre lo provocaron los vecinos (o los sensores) aunque el estado lo afirme de nuevo la fuente
     * oficial: se cita su quórum, no el boletín de antes.
     */
    private static Optional<EventoBitacora> eventoDeReapertura(Sector sector, Reapertura reapertura,
                                                              VotosDeVecinos vecinos, Instant ahora) {
        QuorumVecinos contradice = reapertura.quorum();
        List<ReporteCiudadano> sustento = vecinos.sustentoPorTipo().getOrDefault(contradice.tipo(), List.of());
        if (sustento.isEmpty()) {
            return Optional.empty();
        }
        RespaldoVecinal respaldo = new RespaldoVecinal(contradice.respaldo(), contradice.umbral());
        return Optional.of(EventoBitacoraFactory.consensoConfirmado(sector.id(), contradice.estado(), idsDe(sustento), respaldo, ahora)
                .conFuente(origenDe(sustento), respaldo));
    }

    /** El boletín de la fuente que sostiene el estado y cuya ventana es la que se publicó; el más reciente gana. */
    private static Optional<PropuestaIngesta> propuestaQueSustenta(EstadoPublicado publicado, List<PropuestaIngesta> aprobadas) {
        boolean oficial = publicado.origen() == OrigenEstado.ACUACAR;
        return aprobadas.stream()
                .filter(p -> p.esDeFuenteOficial() == oficial)
                .filter(p -> publicado.estado() == EstadoServicio.CON_SERVICIO
                        ? p.estadoPropuesto() == EstadoServicio.CON_SERVICIO
                        : p.estadoPropuesto() != EstadoServicio.CON_SERVICIO && mismaVentana(p, publicado))
                .max(Comparator.<PropuestaIngesta, Instant>comparing(RecalcularSectorService::momentoDe)
                        .thenComparing(p -> p.id().valor()));
    }

    private static boolean mismaVentana(PropuestaIngesta propuesta, EstadoPublicado publicado) {
        return publicado.ventanaPrometida() != null
                && publicado.ventanaPrometida().inicio().equals(propuesta.inicioDeclarado())
                && publicado.ventanaPrometida().finPrometido().equals(propuesta.finPrometido());
    }
}
