package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.Afirmacion;
import com.aguavigia.ctg.domain.Afirmacion.CorteVeedor;
import com.aguavigia.ctg.domain.Afirmacion.PrensaAprobada;
import com.aguavigia.ctg.domain.Afirmacion.QuorumVecinos;
import com.aguavigia.ctg.domain.ComposicionDelSustento;
import com.aguavigia.ctg.domain.MemoriaDelBarrio;
import com.aguavigia.ctg.domain.VotosDeVecinos;
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
        VotosDeVecinos vecinos = votosDeVecinos(sector, !memoriaDescartada);

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
        List<ReporteId> sustento = cambioElEstado && OrigenEstado.votan(publicado.origen())
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
                    afirmaciones.add(new RestablecimientoOficial(propuesta.momento()));
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

    // --- los vecinos --------------------------------------------------------------------------------

    /**
     * Los quórums de los reportes de la ventana, más el que el barrio ya recuerda. Primero se cuenta (Mongo, sin traer reportes):
     * durante una avería masiva un barrio acumula miles y cargarlos en cada POST agotaba el pool de conexiones. Solo si algún tipo llega
     * a un umbral se traen los reportes, para conocer cuándo reportó el último y cuáles sustentan el cambio.
     *
     * @param conMemoria si el barrio puede apoyarse en lo que ya recuerda; al descartar reportes se comprueba antes y, si dejó de
     *                   sostenerse, se olvida
     */
    private VotosDeVecinos votosDeVecinos(Sector sector, boolean conMemoria) {
        int umbral = (int) estrategia.umbral(sector);
        int minimoParaCargar = Math.min(umbral, resolutor.reglas().quorumReducido(umbral));

        Map<TipoReporte, Long> votos = reportes.contarVotosRecientes(sector.id(), ventanaConsenso);
        List<ReporteCiudadano> recientes = votos.values().stream().anyMatch(v -> v >= minimoParaCargar)
                ? reportes.listarRecientesPorSector(sector.id(), ventanaConsenso)
                : List.of();
        VotosDeVecinos vecinos = VotosDeVecinos.formar(recientes, umbral, redesMinimas);
        return conMemoria ? vecinos.conMemoria(MemoriaDelBarrio.recordado(sector)) : vecinos;
    }

    /**
     * Al descartar reportes se vuelve a contar, desde que el barrio formó su estado, cuántos vecinos válidos
     * lo sostienen, con el mismo listón con que se fijó: el umbral que se guardó, o el reducido si lo que
     * se confirmó fue un restablecimiento. Sin memoria de los vecinos no hay nada que comprobar.
     */
    private boolean laMemoriaSigueSostenida(Sector sector, Instant ahora) {
        Optional<QuorumVecinos> memoria = MemoriaDelBarrio.recordado(sector);
        if (memoria.isEmpty()) {
            return true;
        }
        QuorumVecinos recordada = memoria.get();
        Instant formacion = sector.estadoActualizadoEn() != null ? sector.estadoActualizadoEn() : recordada.primerReporte();
        Duration desde = Duration.between(formacion.minus(ventanaConsenso), ahora);

        List<ReporteCiudadano> sustento = VotosDeVecinos.porDispositivo(reportes.listarRecientesPorSector(sector.id(), desde))
                .getOrDefault(recordada.tipo(), List.of());
        return sustento.size() >= MemoriaDelBarrio.liston(recordada, resolutor.reglas())
                && ComposicionDelSustento.cumple(sustento, redesMinimas);
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
                ? OrigenEstado.deLosVotos(sustento)
                : (sector.estadoActual() == publicado.estado() && sector.marcas().origen() == OrigenEstado.SENSOR
                        ? OrigenEstado.SENSOR : OrigenEstado.VECINOS);
        return origen == publicado.origen() ? publicado : publicado.conOrigen(origen);
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
        if (cambiaElEstado && nuevo == EstadoServicio.CON_SERVICIO && OrigenEstado.votan(publicado.origen())) {
            cambiosDeCortes.addAll(cerrarCortesPorVecinos(sector.id(), cortesDelSector, vecinos, publicado.origen(), ahora));
        }

        if (!cambiaElEstado && !cambianLasMarcas && cambiosDeCortes.isEmpty()) {
            verificarSiToca(sector, publicado, vecinos, ahora);
            return false;
        }

        List<EventoBitacora> eventos = EventoBitacoraFactory.delRecalculo(sector, publicado, aprobadas,
                reapertura.huboReapertura() ? reapertura.quorum() : null, vecinos, cambiaElEstado, memoriaDescartada, ahora);
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
        return !OrigenEstado.votan(publicado.origen()) || !vecinos.sustentoDe(estado).isEmpty();
    }
}
