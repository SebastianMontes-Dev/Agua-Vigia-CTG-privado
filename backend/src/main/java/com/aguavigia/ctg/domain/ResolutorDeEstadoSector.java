package com.aguavigia.ctg.domain;

import com.aguavigia.ctg.domain.Afirmacion.ConVentana;
import com.aguavigia.ctg.domain.Afirmacion.CorteVeedor;
import com.aguavigia.ctg.domain.Afirmacion.QuorumVecinos;
import com.aguavigia.ctg.domain.Afirmacion.RestablecimientoOficial;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Decide el estado público de un barrio a partir de lo que cada fuente afirma en este instante.
 * Es una función pura: mismo conjunto de afirmaciones y misma hora, mismo resultado, sin importar
 * el orden en que lleguen. Sustituye a los escritores dispersos de {@code estadoActual}.
 *
 * Asimetría deliberada (plan §3.2): las malas noticias viajan rápido y las buenas despacio. Una
 * promesa cumplida no prueba que volvió el agua, así que un corte sigue sin servicio hasta que
 * alguien lo confirme o expire; y confirmar un restablecimiento exige menos vecinos que reportar
 * una avería, porque es lo que menos se reporta.
 */
public class ResolutorDeEstadoSector {

    /** El veedor manda; entre los demás gana el estado más severo; a igual severidad, la fuente más autorizada. */
    private static final Comparator<Candidato> PRECEDENCIA = Comparator
            .comparing((Candidato c) -> c.ventana() instanceof CorteVeedor)
            .thenComparing((a, b) -> compararSeveridad(a.estado(), b.estado()))
            .thenComparing(c -> c.ventana().origen() == OrigenEstado.ACUACAR);

    /** El restablecimiento más reciente es el que cuenta; a la misma hora, un orden fijo para no depender del de llegada. */
    private static final Comparator<Restablecimiento> MAS_RECIENTE = Comparator
            .comparing(Restablecimiento::en)
            .thenComparing(Restablecimiento::origen);

    private final ReglasDeEstado reglas;

    public ResolutorDeEstadoSector(ReglasDeEstado reglas) {
        this.reglas = Objects.requireNonNull(reglas);
    }

    public ReglasDeEstado reglas() {
        return reglas;
    }

    public EstadoPublicado resolver(List<Afirmacion> afirmaciones, Instant ahora) {
        List<ConVentana> ventanas = afirmaciones.stream()
                .filter(ConVentana.class::isInstance).map(ConVentana.class::cast).toList();
        List<Instant> boletinesDeRestablecimiento = afirmaciones.stream()
                .filter(RestablecimientoOficial.class::isInstance).map(RestablecimientoOficial.class::cast)
                .map(RestablecimientoOficial::publicadoEn)
                .filter(publicadoEn -> !publicadoEn.isAfter(ahora))
                .toList();
        List<QuorumVecinos> vecinos = afirmaciones.stream()
                .filter(QuorumVecinos.class::isInstance).map(QuorumVecinos.class::cast)
                .filter(q -> q.composicionValida() && estaVigente(q, ahora))
                .toList();

        Optional<Candidato> oficial = ventanas.stream()
                .filter(ventana -> sigueAbierta(ventana, ahora))
                .filter(ventana -> !cerradaPorBoletin(ventana, boletinesDeRestablecimiento))
                .map(ventana -> candidato(ventana, ahora))
                .max(PRECEDENCIA);

        return oficial
                .map(candidato -> conLosVecinos(candidato, vecinos, ahora))
                .orElseGet(() -> sinCorteAbierto(ventanas, boletinesDeRestablecimiento, vecinos, ahora));
    }

    // --- fuente oficial vigente -------------------------------------------------------------------

    /** Un corte cerrado, caducado o vencido hace más del plazo de expiración ya no afirma nada. */
    private boolean sigueAbierta(ConVentana ventana, Instant ahora) {
        if (estaCerrada(ventana, ahora)) {
            return false;
        }
        if (ventana instanceof CorteVeedor delVeedor
                && delVeedor.caducaEn() != null && !ahora.isBefore(delVeedor.caducaEn())) {
            return false;
        }
        return ahora.isBefore(ventana.finPrometido().plus(reglas.expiraTrasFin()));
    }

    /** Un cierre posterior a «ahora» todavía no ocurrió: mirar atrás da lo que valía entonces. */
    private static boolean estaCerrada(ConVentana ventana, Instant ahora) {
        return ventana.cierre() != null && !ventana.cierre().hora().isAfter(ahora);
    }

    /** El override del veedor solo lo cierra el veedor, y un corte que aún no empieza no lo cierra una señal anterior. */
    private static boolean cerradaPorBoletin(ConVentana ventana, List<Instant> boletines) {
        return !(ventana instanceof CorteVeedor)
                && boletines.stream().anyMatch(publicadoEn -> !publicadoEn.isBefore(ventana.inicio()));
    }

    private static Candidato candidato(ConVentana ventana, Instant ahora) {
        if (ahora.isBefore(ventana.inicio())) {
            return new Candidato(ventana, EstadoServicio.CORTE_PROGRAMADO, false);
        }
        return new Candidato(ventana, ventana.estadoEnVentana(), !ahora.isBefore(ventana.finPrometido()));
    }

    // --- qué aportan los vecinos ------------------------------------------------------------------

    private EstadoPublicado conLosVecinos(Candidato oficial, List<QuorumVecinos> vecinos, Instant ahora) {
        if (oficial.porConfirmar()) {
            return confirmadoPorLosVecinos(oficial, vecinos)
                    .orElseGet(oficial::publicar);
        }
        // El corte del veedor no lo discuten ni lo adelantan los vecinos mientras dura.
        if (oficial.ventana() instanceof CorteVeedor) {
            return oficial.publicar();
        }
        Optional<QuorumVecinos> quorum = ganador(alcanzados(vecinos));
        if (quorum.isEmpty()) {
            return oficial.publicar();
        }
        int comparacion = compararSeveridad(quorum.get().estado(), oficial.estado());
        if (comparacion > 0) {
            return EstadoPublicado.porVecinos(quorum.get().estado(), oficial.ventanaPrometida(),
                    respaldoDe(quorum.get()), sinVerificacionReciente(quorum.get(), ahora));
        }
        if (comparacion < 0 && oficial.estado() == EstadoServicio.SIN_SERVICIO) {
            return oficial.publicar().enDisputaPor(quorum.get().respaldo());
        }
        return oficial.publicar();
    }

    /** Pasada la promesa basta la mitad del umbral (mínimo configurado): confirmar que volvió el agua es lo escaso. */
    private Optional<EstadoPublicado> confirmadoPorLosVecinos(Candidato oficial, List<QuorumVecinos> vecinos) {
        return vecinos.stream()
                .filter(q -> q.estado() == EstadoServicio.CON_SERVICIO)
                .filter(q -> q.sostenido() || q.respaldo() >= reglas.quorumReducido(q.umbral()))
                .max(Comparator.comparingInt(QuorumVecinos::respaldo))
                .map(q -> EstadoPublicado.porVecinos(EstadoServicio.CON_SERVICIO, null, respaldoDe(q), false));
    }

    // --- sin ningún corte abierto -----------------------------------------------------------------

    private EstadoPublicado sinCorteAbierto(List<ConVentana> ventanas, List<Instant> boletines,
                                            List<QuorumVecinos> vecinos, Instant ahora) {
        Optional<Restablecimiento> ultimo = restablecimientos(ventanas, boletines, ahora, reglas.expiraTrasFin())
                .stream().max(MAS_RECIENTE);

        if (ultimo.isPresent()) {
            // Los vecinos pueden contradecir un restablecimiento, pero solo con reportes posteriores a él.
            Optional<QuorumVecinos> contradice = ganador(alcanzados(vecinos).stream()
                    .filter(q -> q.estado() != EstadoServicio.CON_SERVICIO)
                    .filter(q -> q.ultimoReporte().isAfter(ultimo.get().en()))
                    .toList());
            if (contradice.isPresent()) {
                return EstadoPublicado.porVecinos(contradice.get().estado(), null, respaldoDe(contradice.get()),
                        sinVerificacionReciente(contradice.get(), ahora));
            }
            return EstadoPublicado.de(EstadoServicio.CON_SERVICIO, ultimo.get().origen(), null, false);
        }

        return ganador(alcanzados(vecinos))
                .map(q -> EstadoPublicado.porVecinos(q.estado(), null, respaldoDe(q), sinVerificacionReciente(q, ahora)))
                .orElseGet(EstadoPublicado::sinDatos);
    }

    /**
     * Los boletines siguen cerrando las ventanas que cubren ({@link #cerradaPorBoletin}), pero solo afirman «hay servicio»
     * mientras no pase el plazo de expiración: uno de hace meses dice qué pasó entonces, no qué pasa hoy.
     */
    private static List<Restablecimiento> restablecimientos(List<ConVentana> ventanas, List<Instant> boletines,
                                                            Instant ahora, Duration vigencia) {
        List<Restablecimiento> restablecimientos = new ArrayList<>();
        for (ConVentana ventana : ventanas) {
            if (estaCerrada(ventana, ahora)) {
                restablecimientos.add(new Restablecimiento(ventana.cierre().hora(), ventana.cierre().fuente()));
            }
        }
        boletines.stream()
                .filter(publicadoEn -> ahora.isBefore(publicadoEn.plus(vigencia)))
                .forEach(publicadoEn -> restablecimientos.add(new Restablecimiento(publicadoEn, OrigenEstado.ACUACAR)));
        return restablecimientos;
    }

    // --- piezas comunes ---------------------------------------------------------------------------

    /** Un quórum que no se renueva caduca: sin reportes nuevos, el barrio vuelve a «sin datos». */
    private boolean estaVigente(QuorumVecinos quorum, Instant ahora) {
        return Duration.between(quorum.ultimoReporte(), ahora).compareTo(reglas.vecinosCaducan()) < 0;
    }

    private boolean sinVerificacionReciente(QuorumVecinos quorum, Instant ahora) {
        return Duration.between(quorum.ultimoReporte(), ahora).compareTo(reglas.vecinosSinVerificacion()) >= 0;
    }

    private static List<QuorumVecinos> alcanzados(List<QuorumVecinos> vecinos) {
        return vecinos.stream().filter(QuorumVecinos::alcanzado).toList();
    }

    /** El quórum con más respaldo. Un empate entre tipos distintos es evidencia ambigua y no decide nada. */
    private static Optional<QuorumVecinos> ganador(List<QuorumVecinos> quorums) {
        int maximo = quorums.stream().mapToInt(QuorumVecinos::respaldo).max().orElse(0);
        List<QuorumVecinos> conMasRespaldo = quorums.stream().filter(q -> q.respaldo() == maximo).toList();
        return conMasRespaldo.size() == 1 ? Optional.of(conMasRespaldo.get(0)) : Optional.empty();
    }

    private static RespaldoVecinal respaldoDe(QuorumVecinos quorum) {
        return new RespaldoVecinal(quorum.respaldo(), quorum.umbral());
    }

    /** Positivo si {@code a} es más severo que {@code b}. */
    private static int compararSeveridad(EstadoServicio a, EstadoServicio b) {
        if (a == b) {
            return 0;
        }
        return EstadoServicio.masSevero(a, b) == a ? 1 : -1;
    }

    private record Restablecimiento(Instant en, OrigenEstado origen) {
    }

    private record Candidato(ConVentana ventana, EstadoServicio estado, boolean porConfirmar) {

        VentanaTiempo ventanaPrometida() {
            return new VentanaTiempo(ventana.inicio(), ventana.finPrometido());
        }

        EstadoPublicado publicar() {
            return EstadoPublicado.de(estado, ventana.origen(), ventanaPrometida(), porConfirmar);
        }
    }
}
