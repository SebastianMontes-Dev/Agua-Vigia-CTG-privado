package com.aguavigia.ctg.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Lo que una fuente afirma sobre un barrio. Las fuentes solo aportan afirmaciones; quien decide
 * qué estado se publica es {@link ResolutorDeEstadoSector}, y nadie más escribe el estado.
 */
public sealed interface Afirmacion
        permits Afirmacion.ConVentana, Afirmacion.RestablecimientoOficial, Afirmacion.QuorumVecinos {

    /** Una afirmación que declara una ventana de corte: desde cuándo y hasta cuándo se promete. */
    sealed interface ConVentana extends Afirmacion permits CorteVeedor, VentanaOficial, PrensaAprobada {
        Instant inicio();

        Instant finPrometido();

        /** Nulo mientras el corte siga abierto en este barrio. */
        CierreDeCorte cierre();

        OrigenEstado origen();
    }

    /**
     * El corte que el veedor registró: es el override, la señal más autorizada. {@code caducaEn}
     * es opcional y, pasado ese instante, el corte deja de afirmar nada.
     */
    record CorteVeedor(Instant inicio, Instant finPrometido, Instant caducaEn, CierreDeCorte cierre)
            implements ConVentana {

        public CorteVeedor {
            new VentanaTiempo(inicio, finPrometido);
        }

        @Override
        public OrigenEstado origen() {
            return OrigenEstado.VEEDOR;
        }
    }

    /**
     * Un boletín de Acuacar que declara el servicio normalizado. Cierra las ventanas oficiales que ya
     * habían empezado; no toca un corte que aún no empieza ni el override del veedor.
     */
    record RestablecimientoOficial(Instant publicadoEn) implements Afirmacion {

        public RestablecimientoOficial {
            Objects.requireNonNull(publicadoEn, "El boletín debe tener fecha de publicación");
        }
    }

    /**
     * Los vecinos que coinciden en un tipo de reporte. La capa de aplicación cuenta los votos y
     * comprueba la composición (verificación y diversidad de redes); aquí llega el resultado:
     * cuántos son, cuántos hacían falta y cuándo reportó el último. El resolutor decide qué umbral
     * aplica según la fase, por eso recibe el completo. {@code sostenido} marca el que el barrio ya
     * recuerda: se alcanzó en su momento, así que solo caduca con el tiempo.
     */
    record QuorumVecinos(TipoReporte tipo, int respaldo, int umbral, boolean composicionValida,
                         Instant primerReporte, Instant ultimoReporte, boolean sostenido) implements Afirmacion {

        public QuorumVecinos {
            Objects.requireNonNull(tipo, "El quórum debe declarar el tipo de reporte");
            Objects.requireNonNull(ultimoReporte, "El quórum debe declarar cuándo reportó el último vecino");
            if (respaldo < 0 || umbral < 1) {
                throw new IllegalArgumentException("Respaldo y umbral no válidos: " + respaldo + "/" + umbral);
            }
        }

        /** Un quórum reciente, que los reportes de la ventana sostienen ahora mismo. */
        public QuorumVecinos(TipoReporte tipo, int respaldo, int umbral, boolean composicionValida,
                             Instant primerReporte, Instant ultimoReporte) {
            this(tipo, respaldo, umbral, composicionValida, primerReporte, ultimoReporte, false);
        }

        /** Si el quórum ya se alcanzó (ahora o cuando el barrio lo registró) y no hay que volver a exigir el umbral. */
        public boolean alcanzado() {
            return sostenido || respaldo >= umbral;
        }

        public EstadoServicio estado() {
            return switch (tipo) {
                case SIN_AGUA -> EstadoServicio.SIN_SERVICIO;
                case PRESION_BAJA -> EstadoServicio.PRESION_BAJA;
                case SERVICIO_RESTABLECIDO -> EstadoServicio.CON_SERVICIO;
            };
        }
    }

    /** El boletín de Acuacar aprobado con su ventana declarada. */
    record VentanaOficial(Instant inicio, Instant finPrometido, CierreDeCorte cierre) implements ConVentana {

        public VentanaOficial {
            new VentanaTiempo(inicio, finPrometido);
        }

        @Override
        public OrigenEstado origen() {
            return OrigenEstado.ACUACAR;
        }
    }

    /** Una nota de prensa que el veedor aprobó, con la ventana que declara. */
    record PrensaAprobada(Instant inicio, Instant finPrometido, CierreDeCorte cierre) implements ConVentana {

        public PrensaAprobada {
            new VentanaTiempo(inicio, finPrometido);
        }

        @Override
        public OrigenEstado origen() {
            return OrigenEstado.PRENSA;
        }
    }
}
