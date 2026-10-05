package com.aguavigia.ctg.domain;

import com.aguavigia.ctg.domain.Afirmacion.QuorumVecinos;

import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Lo que los vecinos (y los sensores de la red) afirman de un barrio en una ventana: un quórum por tipo de reporte y los reportes que
 * lo sustentan. Un vecino que reporta tres veces sigue siendo un vecino: se cuenta su reporte más reciente.
 *
 * @param porTipo           el quórum de cada tipo; puede incluir el que el barrio ya recuerda ({@link #conMemoria})
 * @param sustentoPorTipo   los reportes de la ventana de cada tipo, uno por dispositivo; vacío si el quórum es solo memoria
 */
public record VotosDeVecinos(Map<TipoReporte, QuorumVecinos> porTipo,
                             Map<TipoReporte, List<ReporteCiudadano>> sustentoPorTipo) {

    public VotosDeVecinos {
        porTipo = Map.copyOf(porTipo);
        sustentoPorTipo = Map.copyOf(sustentoPorTipo);
    }

    /** Un voto por dispositivo, el de su reporte más reciente, agrupado por tipo. */
    public static Map<TipoReporte, List<ReporteCiudadano>> porDispositivo(List<ReporteCiudadano> recientes) {
        Map<String, ReporteCiudadano> ultimoPorDispositivo = new LinkedHashMap<>();
        for (ReporteCiudadano reporte : recientes) {
            ultimoPorDispositivo.merge(reporte.huella().hash(), reporte,
                    (a, b) -> a.timestamp().isAfter(b.timestamp()) ? a : b);
        }
        return ultimoPorDispositivo.values().stream().collect(Collectors.groupingBy(ReporteCiudadano::tipo));
    }

    /**
     * El quórum de cada tipo a partir de los reportes de la ventana: su respaldo es cuántos dispositivos votaron, y su composición
     * (verificación y redes distintas, D9 y D16) se comprueba aparte del umbral.
     */
    public static VotosDeVecinos formar(List<ReporteCiudadano> recientes, int umbral, int redesMinimas) {
        Map<TipoReporte, List<ReporteCiudadano>> sustentoPorTipo = new EnumMap<>(TipoReporte.class);
        Map<TipoReporte, QuorumVecinos> quorums = new EnumMap<>(TipoReporte.class);
        porDispositivo(recientes).forEach((tipo, reportesDelTipo) -> {
            sustentoPorTipo.put(tipo, reportesDelTipo);
            Instant primero = reportesDelTipo.stream().map(ReporteCiudadano::timestamp).min(Comparator.naturalOrder()).orElseThrow();
            Instant ultimo = reportesDelTipo.stream().map(ReporteCiudadano::timestamp).max(Comparator.naturalOrder()).orElseThrow();
            quorums.put(tipo, new QuorumVecinos(tipo, reportesDelTipo.size(), umbral,
                    ComposicionDelSustento.cumple(reportesDelTipo, redesMinimas), primero, ultimo));
        });
        return new VotosDeVecinos(quorums, sustentoPorTipo);
    }

    /**
     * Suma lo que el barrio ya recuerda. Un quórum fresco que no llega al umbral (los reportes que fijaron el estado van saliendo de
     * la ventana) no puede borrar esa memoria: solo un quórum alcanzado y bien compuesto ocupa su lugar.
     */
    public VotosDeVecinos conMemoria(Optional<QuorumVecinos> memoria) {
        if (memoria.isEmpty()) {
            return this;
        }
        Map<TipoReporte, QuorumVecinos> quorums = new EnumMap<>(TipoReporte.class);
        quorums.putAll(porTipo);
        QuorumVecinos recordada = memoria.get();
        quorums.merge(recordada.tipo(), recordada, (fresco, recuerdo) ->
                fresco.alcanzado() && fresco.composicionValida() ? fresco : recuerdo);
        return new VotosDeVecinos(quorums, sustentoPorTipo);
    }

    public List<QuorumVecinos> quorums() {
        return List.copyOf(porTipo.values());
    }

    /** Los reportes de la ventana que sostienen {@code estado}; vacío si el estado viene de la memoria del barrio. */
    public List<ReporteCiudadano> sustentoDe(EstadoServicio estado) {
        return sustentoPorTipo.entrySet().stream()
                .filter(e -> estadoDe(e.getKey()) == estado)
                .flatMap(e -> e.getValue().stream())
                .toList();
    }

    /** Lo que los vecinos no recuerdan pero sí acaban de alcanzar: el quórum de esta ventana, no el de la memoria. */
    public Optional<QuorumVecinos> recienteDe(TipoReporte tipo) {
        return Optional.ofNullable(porTipo.get(tipo)).filter(q -> !q.sostenido());
    }

    /** El quórum de restablecimiento se descarta entero: otro mayor lo contradijo y no pueden valer los dos. */
    public VotosDeVecinos sinElRestablecimiento() {
        Map<TipoReporte, QuorumVecinos> sinRestablecimiento = new EnumMap<>(TipoReporte.class);
        sinRestablecimiento.putAll(porTipo);
        sinRestablecimiento.remove(TipoReporte.SERVICIO_RESTABLECIDO);
        return new VotosDeVecinos(sinRestablecimiento, sustentoPorTipo);
    }

    public int respaldoDeRestablecimiento() {
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
