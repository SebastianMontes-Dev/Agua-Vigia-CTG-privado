package com.aguavigia.ctg.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Se construye solo con Builder: la invariante finPrometido > inicio la impone VentanaTiempo,
 * y aquí no hay forma de terminar el objeto sin pasar por ella.
 *
 * Un corte agrupa varios barrios pero se restablece barrio por barrio: cada uno tiene su propio
 * {@link CierreDeCorte} (hora, fuente y si es provisional). El corte pasa a RESTABLECIDO cuando
 * todos sus barrios están cerrados, y solo entonces tiene {@code finReal}: la hora del último
 * cierre. Un corte EXPIRADO (nadie lo confirmó a tiempo) o ANULADO (se publicó por error) no tiene
 * hora real aunque conserve los cierres que llegó a tener, así que nunca entra al Índice.
 */
public final class CorteAgua {

    private final CorteId id;
    private final List<SectorId> sectoresAfectados;
    private final VentanaTiempo ventana;
    private final String causa;
    private final OrigenCorte origen;
    private final EstadoCorte estado;
    private final Map<SectorId, CierreDeCorte> cierres;
    private final String motivoAnulacion;
    private final Instant caducaEn;

    private CorteAgua(Builder builder, VentanaTiempo ventana, Map<SectorId, CierreDeCorte> cierres) {
        this.id = builder.id;
        this.sectoresAfectados = List.copyOf(builder.sectoresAfectados);
        this.ventana = ventana;
        this.causa = builder.causa;
        this.origen = builder.origen;
        this.estado = builder.estado;
        this.cierres = Map.copyOf(cierres);
        this.motivoAnulacion = builder.motivoAnulacion;
        this.caducaEn = builder.caducaEn;
    }

    public static Builder builder() {
        return new Builder();
    }

    public CorteId id() {
        return id;
    }

    public List<SectorId> sectoresAfectados() {
        return sectoresAfectados;
    }

    public VentanaTiempo ventana() {
        return ventana;
    }

    public String causa() {
        return causa;
    }

    public OrigenCorte origen() {
        return origen;
    }

    public EstadoCorte estado() {
        return estado;
    }

    /** El cierre de cada barrio que ya se restableció; los pendientes no aparecen. */
    public Map<SectorId, CierreDeCorte> cierres() {
        return cierres;
    }

    public Optional<CierreDeCorte> cierreDe(SectorId sectorId) {
        return Optional.ofNullable(cierres.get(sectorId));
    }

    /** Nulo salvo en un corte ANULADO, donde es obligatorio. */
    public String motivoAnulacion() {
        return motivoAnulacion;
    }

    /**
     * Solo en el corte del veedor, que es el override: pasada esta hora deja de afirmar nada aunque nadie lo
     * haya cerrado. Nulo cuando el corte dura hasta que alguien lo cierre o expire.
     */
    public Instant caducaEn() {
        return caducaEn;
    }

    /** Anunciado o confirmado: todavía puede cerrarse, expirar o anularse. */
    public boolean estaAbierto() {
        return estado == EstadoCorte.ANUNCIADO || estado == EstadoCorte.CONFIRMADO;
    }

    /**
     * Si este corte todavía obliga a que el barrio {@code sectorId} siga afectado en {@code ahora}.
     * Un corte de ingesta solo vale mientras dure la ventana que el boletín prometió: nadie fija su
     * {@code finReal}, así que sin este límite quedaría «abierto» para siempre y bloquearía el retorno
     * a CON_SERVICIO. El corte del veedor, en cambio, sigue en pie hasta que alguien lo cierre. Un
     * barrio ya restablecido deja de estar sostenido aunque el corte siga abierto en otros.
     */
    public boolean sostieneElEstadoEn(SectorId sectorId, Instant ahora) {
        if (!estaAbierto()) {
            return false;
        }
        CierreDeCorte cierre = cierres.get(sectorId);
        if (cierre != null && !cierre.hora().isAfter(ahora)) {
            return false;
        }
        return origen != OrigenCorte.INGESTA_IA || ventana.finPrometido().isAfter(ahora);
    }

    /**
     * Restablece un barrio. Cuando es el último pendiente, el corte entero queda RESTABLECIDO con la
     * hora del último cierre. Un cierre ya puesto no se sobrescribe aquí: confirmar o corregir un
     * cierre provisional es otra operación.
     */
    public CorteAgua cerrarSector(SectorId sectorId, CierreDeCorte cierre) {
        exigirAbierto("cerrar");
        if (!sectoresAfectados.contains(sectorId)) {
            throw new IllegalArgumentException(
                    "El corte '" + id.valor() + "' no afecta al sector '" + sectorId.valor() + "'");
        }
        if (cierre.hora().isBefore(ventana.inicio())) {
            throw new IllegalArgumentException("El cierre no puede preceder al inicio del corte");
        }
        if (cierres.containsKey(sectorId)) {
            throw new IllegalStateException("El sector '" + sectorId.valor() + "' ya está cerrado en el corte '" + id.valor() + "'");
        }
        Map<SectorId, CierreDeCorte> nuevos = new LinkedHashMap<>(cierres);
        nuevos.put(sectorId, cierre);
        return copiaCon(nuevos, todosCerrados(nuevos) ? EstadoCorte.RESTABLECIDO : estado, null);
    }

    /**
     * El veedor confirma —o corrige la hora de— un cierre que solo sostenían los vecinos o los sensores.
     * Solo se confirma un cierre provisional, y la confirmación es definitiva: no puede ser provisional.
     */
    public CorteAgua confirmarCierre(SectorId sectorId, CierreDeCorte confirmado) {
        if (estado == EstadoCorte.ANULADO) {
            throw new IllegalStateException("El corte '" + id.valor() + "' está anulado");
        }
        if (confirmado.provisional()) {
            throw new IllegalArgumentException("Una confirmación no puede ser provisional");
        }
        if (confirmado.hora().isBefore(ventana.inicio())) {
            throw new IllegalArgumentException("El cierre no puede preceder al inicio del corte");
        }
        CierreDeCorte actual = cierres.get(sectorId);
        if (actual == null) {
            throw new IllegalStateException("El sector '" + sectorId.valor() + "' no tiene cierre que confirmar");
        }
        if (!actual.provisional()) {
            throw new IllegalStateException("El cierre de '" + sectorId.valor() + "' ya está confirmado");
        }
        Map<SectorId, CierreDeCorte> nuevos = new LinkedHashMap<>(cierres);
        nuevos.put(sectorId, confirmado);
        return copiaCon(nuevos, estado, null);
    }

    /**
     * Un restablecimiento que solo sostenían los vecinos resultó no ser efectivo: el barrio vuelve a estar
     * abierto en el mismo corte, en vez de abrir un corte nuevo (una intermitencia no es un evento distinto).
     * Solo se reabre un cierre provisional; lo que confirmó el veedor o un boletín es definitivo.
     */
    public CorteAgua reabrirSector(SectorId sectorId) {
        if (estado == EstadoCorte.ANULADO || estado == EstadoCorte.EXPIRADO) {
            throw new IllegalStateException("No se puede reabrir un barrio de un corte " + estado.name().toLowerCase());
        }
        CierreDeCorte actual = cierres.get(sectorId);
        if (actual == null) {
            throw new IllegalStateException("El sector '" + sectorId.valor() + "' no tiene cierre que reabrir");
        }
        if (!actual.provisional()) {
            throw new IllegalStateException("El cierre de '" + sectorId.valor() + "' está confirmado y no se reabre");
        }
        Map<SectorId, CierreDeCorte> nuevos = new LinkedHashMap<>(cierres);
        nuevos.remove(sectorId);
        return copiaCon(nuevos, EstadoCorte.ANUNCIADO, null);
    }

    /**
     * RF017 — el atajo del veedor: restablece de una vez todos los barrios que siguen pendientes con
     * la hora indicada, y respeta los que ya estaban cerrados. Cierra el corte y marca el estado
     * atómicamente, así nunca puede existir un CorteAgua con estado y cierres incoherentes por esta vía.
     */
    public CorteAgua cerrar(Instant finReal) {
        exigirAbierto("cerrar");
        Map<SectorId, CierreDeCorte> nuevos = new LinkedHashMap<>(cierres);
        for (SectorId sectorId : sectoresAfectados) {
            nuevos.putIfAbsent(sectorId, new CierreDeCorte(finReal, OrigenEstado.VEEDOR, false));
        }
        return builderDesde(this).cierres(nuevos).estado(EstadoCorte.RESTABLECIDO).build();
    }

    /** Nadie confirmó el restablecimiento a tiempo: el corte deja de afirmar nada y no tiene hora real. */
    public CorteAgua expirar() {
        exigirAbierto("expirar");
        return copiaCon(cierres, EstadoCorte.EXPIRADO, null);
    }

    /** Se publicó por error: queda como historia con su motivo, fuera del Índice y de las estadísticas. */
    public CorteAgua anular(String motivo) {
        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException("Anular un corte exige un motivo");
        }
        if (estado == EstadoCorte.ANULADO) {
            throw new IllegalStateException("El corte '" + id.valor() + "' ya está anulado");
        }
        return copiaCon(cierres, EstadoCorte.ANULADO, motivo);
    }

    private void exigirAbierto(String accion) {
        if (!estaAbierto()) {
            throw new IllegalStateException("No se puede " + accion + " el corte '" + id.valor()
                    + "': ya está " + estado.name().toLowerCase());
        }
    }

    private CorteAgua copiaCon(Map<SectorId, CierreDeCorte> nuevosCierres, EstadoCorte nuevoEstado, String motivo) {
        return builderDesde(this).cierres(nuevosCierres).estado(nuevoEstado).motivoAnulacion(motivo).build();
    }

    private static Builder builderDesde(CorteAgua corte) {
        return builder()
                .id(corte.id)
                .sectoresAfectados(corte.sectoresAfectados)
                .inicio(corte.ventana.inicio())
                .finPrometido(corte.ventana.finPrometido())
                .causa(corte.causa)
                .origen(corte.origen)
                .cierres(corte.cierres)
                .estado(corte.estado)
                .motivoAnulacion(corte.motivoAnulacion)
                .caducaEn(corte.caducaEn);
    }

    private boolean todosCerrados(Map<SectorId, CierreDeCorte> porSector) {
        return porSector.keySet().containsAll(sectoresAfectados);
    }

    public static final class Builder {
        private CorteId id;
        private final List<SectorId> sectoresAfectados = new ArrayList<>();
        private Instant inicio;
        private Instant finPrometido;
        private Instant finReal;
        private String causa;
        private OrigenCorte origen;
        private EstadoCorte estado = EstadoCorte.ANUNCIADO;
        private final Map<SectorId, CierreDeCorte> cierres = new LinkedHashMap<>();
        private String motivoAnulacion;
        private Instant caducaEn;

        private Builder() {
        }

        public Builder id(CorteId id) {
            this.id = id;
            return this;
        }

        public Builder sectoresAfectados(List<SectorId> sectores) {
            this.sectoresAfectados.clear();
            this.sectoresAfectados.addAll(sectores);
            return this;
        }

        public Builder inicio(Instant inicio) {
            this.inicio = inicio;
            return this;
        }

        public Builder finPrometido(Instant finPrometido) {
            this.finPrometido = finPrometido;
            return this;
        }

        /**
         * La forma antigua de cerrar un corte: una sola hora para todo. Se conserva para leer los
         * cortes guardados antes de los cierres por sector, y cierra con la hora del veedor cada
         * barrio que no traiga su propio cierre.
         */
        public Builder finReal(Instant finReal) {
            this.finReal = finReal;
            return this;
        }

        public Builder causa(String causa) {
            this.causa = causa;
            return this;
        }

        public Builder origen(OrigenCorte origen) {
            this.origen = origen;
            return this;
        }

        public Builder estado(EstadoCorte estado) {
            this.estado = estado;
            return this;
        }

        public Builder cierres(Map<SectorId, CierreDeCorte> cierres) {
            this.cierres.clear();
            this.cierres.putAll(cierres);
            return this;
        }

        public Builder motivoAnulacion(String motivoAnulacion) {
            this.motivoAnulacion = motivoAnulacion;
            return this;
        }

        public Builder caducaEn(Instant caducaEn) {
            this.caducaEn = caducaEn;
            return this;
        }

        public CorteAgua build() {
            Objects.requireNonNull(id, "El corte debe tener id");
            if (sectoresAfectados.isEmpty()) {
                throw new IllegalStateException("El corte debe afectar al menos un sector");
            }
            Objects.requireNonNull(causa, "El corte debe tener causa");
            Objects.requireNonNull(origen, "El corte debe tener origen");
            Objects.requireNonNull(estado, "El corte debe tener estado");
            // VentanaTiempo valida finPrometido > inicio al construirse — no se puede rodear.
            new VentanaTiempo(inicio, finPrometido);
            if (caducaEn != null && caducaEn.isBefore(inicio)) {
                throw new IllegalArgumentException("La caducidad no puede preceder al inicio del corte");
            }

            if ((estado == EstadoCorte.ANULADO) != (motivoAnulacion != null && !motivoAnulacion.isBlank())) {
                throw new IllegalStateException(
                        "El motivo de anulación es obligatorio si el corte está ANULADO, y solo entonces");
            }

            Map<SectorId, CierreDeCorte> cierresCompletos = new LinkedHashMap<>(cierres);
            if (finReal != null) {
                sectoresAfectados.forEach(sector ->
                        cierresCompletos.putIfAbsent(sector, new CierreDeCorte(finReal, OrigenEstado.VEEDOR, false)));
            }
            for (Map.Entry<SectorId, CierreDeCorte> cierre : cierresCompletos.entrySet()) {
                if (!sectoresAfectados.contains(cierre.getKey())) {
                    throw new IllegalStateException("El cierre es de un sector que el corte no afecta: "
                            + cierre.getKey().valor());
                }
                if (cierre.getValue().hora().isBefore(inicio)) {
                    throw new IllegalArgumentException("El cierre no puede preceder al inicio del corte");
                }
            }

            boolean todosCerrados = cierresCompletos.keySet().containsAll(sectoresAfectados);
            if (estado == EstadoCorte.RESTABLECIDO && !todosCerrados) {
                throw new IllegalStateException("Un corte RESTABLECIDO debe tener cerrados todos sus sectores");
            }
            boolean abiertoOSinConfirmar = estado == EstadoCorte.ANUNCIADO || estado == EstadoCorte.CONFIRMADO
                    || estado == EstadoCorte.EXPIRADO;
            if (todosCerrados && abiertoOSinConfirmar) {
                throw new IllegalStateException("El estado '" + estado + "' es incoherente: todos los sectores están cerrados");
            }

            // La hora real es la del último cierre y solo existe en un corte restablecido.
            Instant finRealDerivado = estado == EstadoCorte.RESTABLECIDO
                    ? cierresCompletos.values().stream().map(CierreDeCorte::hora).max(Comparator.naturalOrder()).orElseThrow()
                    : null;
            return new CorteAgua(this, new VentanaTiempo(inicio, finPrometido, finRealDerivado), cierresCompletos);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CorteAgua otro)) return false;
        return Objects.equals(id, otro.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
