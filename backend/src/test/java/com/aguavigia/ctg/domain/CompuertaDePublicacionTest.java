package com.aguavigia.ctg.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Las compuertas de publicación (D5): un boletín de Acuacar sale solo al mapa únicamente si la extracción es
 * fiable y tiene sentido. Lo que no pasa va a la cola del veedor con el motivo, no se descarta.
 */
class CompuertaDePublicacionTest {

    private static final Instant PUBLICADO = Instant.parse("2026-08-20T16:00:00Z");
    private static final Instant INICIO = Instant.parse("2026-08-21T14:00:00Z");
    private static final Instant FIN = Instant.parse("2026-08-21T23:00:00Z");

    private final CompuertaDePublicacion compuerta = CompuertaDePublicacion.porDefecto();

    private List<String> motivos(EstadoServicio estado, double confianza, Instant inicio, Instant fin, int barrios,
                                 boolean aliasAmbiguo) {
        return compuerta.motivosParaRevisar(estado, confianza, inicio, fin, PUBLICADO, barrios, aliasAmbiguo, "cita");
    }

    private List<String> motivosDeUnCorte(double confianza, Instant inicio, Instant fin, int barrios) {
        return motivos(EstadoServicio.CORTE_PROGRAMADO, confianza, inicio, fin, barrios, false);
    }

    @Test
    void unAvisoFiableYConSentidoSePublicaSolo() {
        assertThat(motivosDeUnCorte(0.85, INICIO, FIN, 20)).isEmpty();
    }

    @Test
    void unaMencionSueltaVaAlVeedor() {
        assertThat(motivosDeUnCorte(0.45, null, null, 1))
                .singleElement().asString().contains("confianza").contains("0,45").contains("0,85");
    }

    @Test
    void unaEnumeracionSinHorarioVaAlVeedorSiAnunciaUnCorte() {
        assertThat(motivosDeUnCorte(0.75, null, null, 5)).isNotEmpty();
    }

    /** El restablecimiento con enumeración basta con 0,75: afirmar que hay agua donde el operador dice que volvió no inventa una emergencia. */
    @Test
    void unRestablecimientoConEnumeracionSePublicaSolo() {
        assertThat(motivos(EstadoServicio.CON_SERVICIO, 0.75, null, null, 12, false)).isEmpty();
    }

    @Test
    void unRestablecimientoSueltoVaAlVeedor() {
        assertThat(motivos(EstadoServicio.CON_SERVICIO, 0.45, null, null, 1, false)).isNotEmpty();
    }

    @Test
    void unaVentanaDeMasDeSetentaYDosHorasVaAlVeedor() {
        assertThat(motivosDeUnCorte(0.85, INICIO, INICIO.plus(Duration.ofHours(72)), 5)).isEmpty();
        assertThat(motivosDeUnCorte(0.85, INICIO, INICIO.plus(Duration.ofHours(72)).plusSeconds(1), 5))
                .singleElement().asString().contains("72");
    }

    @Test
    void unInicioMuyLejanoDeLaPublicacionVaAlVeedor() {
        // Un año mal leído (2025 por 2026) o un día equivocado no debe llegar al mapa.
        Instant haceUnAnio = INICIO.minus(Duration.ofDays(365));
        assertThat(motivosDeUnCorte(0.85, haceUnAnio, haceUnAnio.plus(Duration.ofHours(9)), 5))
                .singleElement().asString().contains("7 días");
        Instant enUnMes = PUBLICADO.plus(Duration.ofDays(30));
        assertThat(motivosDeUnCorte(0.85, enUnMes, enUnMes.plus(Duration.ofHours(9)), 5)).isNotEmpty();
    }

    @Test
    void elMargenDeSieteDiasEsInclusivo() {
        Instant justo = PUBLICADO.plus(Duration.ofDays(7));
        assertThat(motivosDeUnCorte(0.85, justo, justo.plus(Duration.ofHours(9)), 5)).isEmpty();
        Instant antes = PUBLICADO.minus(Duration.ofDays(7));
        assertThat(motivosDeUnCorte(0.85, antes, antes.plus(Duration.ofHours(9)), 5)).isEmpty();
    }

    @Test
    void unAvisoConDemasiadosBarriosVaAlVeedor() {
        assertThat(motivosDeUnCorte(0.85, INICIO, FIN, 40)).isEmpty();
        assertThat(motivosDeUnCorte(0.85, INICIO, FIN, 41)).singleElement().asString().contains("41").contains("40");
    }

    @Test
    void sinCitaTextualVaAlVeedor() {
        assertThat(compuerta.motivosParaRevisar(EstadoServicio.CORTE_PROGRAMADO, 0.85, INICIO, FIN, PUBLICADO, 5, false, "  "))
                .singleElement().asString().contains("cita");
        assertThat(compuerta.motivosParaRevisar(EstadoServicio.CORTE_PROGRAMADO, 0.85, INICIO, FIN, PUBLICADO, 5, false, null))
                .isNotEmpty();
    }

    @Test
    void unAliasAmbiguoVaAlVeedor() {
        assertThat(motivos(EstadoServicio.CORTE_PROGRAMADO, 0.85, INICIO, FIN, 5, true))
                .singleElement().asString().contains("ambigu");
    }

    @Test
    void cadaProblemaSumaSuMotivo() {
        assertThat(motivos(EstadoServicio.CORTE_PROGRAMADO, 0.45, INICIO, INICIO.plus(Duration.ofHours(100)), 80, true))
                .hasSize(4);
    }

    @Test
    void sinHorarioNoHayNadaQueComprobarDeLaVentana() {
        assertThat(motivosDeUnCorte(0.85, null, null, 5)).isEmpty();
    }

    @Test
    void losUmbralesDebenTenerSentido() {
        assertThatThrownBy(() -> new CompuertaDePublicacion(1.2, 0.75, Duration.ofHours(72), Duration.ofDays(7), 40))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CompuertaDePublicacion(0.85, 0.75, Duration.ZERO, Duration.ofDays(7), 40))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CompuertaDePublicacion(0.85, 0.75, Duration.ofHours(72), Duration.ofDays(7), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
