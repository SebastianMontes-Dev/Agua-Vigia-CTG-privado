package com.aguavigia.ctg.infrastructure.ingest;

import com.aguavigia.ctg.infrastructure.ingest.LectorDeVentanaDeclarada.Ventana;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class LectorDeVentanaDeclaradaTest {

    private static final Instant PUBLICADO = Instant.parse("2026-08-20T15:00:00Z");

    private static Instant cartagena(int anio, int mes, int dia, int hora, int minuto) {
        return LocalDateTime.of(anio, mes, dia, hora, minuto).atZone(LectorDeVentanaDeclarada.ZONA_CARTAGENA).toInstant();
    }

    @Test
    void debeLeerLaVentanaPrometidaEnHoraDeCartagena() {
        Ventana ventana = LectorDeVentanaDeclarada.leer(
                "Mañana viernes 21 de agosto, entre las 9:00 a.m. y las 6:00 p.m., se suspenderá el servicio.", PUBLICADO);

        assertThat(ventana.inicio()).isEqualTo(cartagena(2026, 8, 21, 9, 0));
        assertThat(ventana.fin()).isEqualTo(cartagena(2026, 8, 21, 18, 0));
    }

    /** La línea de fecha del encabezado no es la del corte: tomarla adelantaba la ventana un día entero. */
    @Test
    void debeTomarLaFechaQuePrecedeAlHorarioYNoLaDelEncabezado() {
        Ventana ventana = LectorDeVentanaDeclarada.leer(
                "Cartagena de Indias, 20 de agosto de 2026. Mañana viernes 21 de agosto, entre las 9:00 a.m. y las 6:00 p.m.",
                PUBLICADO);

        assertThat(ventana.inicio()).isEqualTo(cartagena(2026, 8, 21, 9, 0));
    }

    @Test
    void debeAceptarLasDosFormasDeEscribirLaMeridiano() {
        Ventana ventana = LectorDeVentanaDeclarada.leer("El 21 de agosto entre las 9 a. m. y las 6 p. m.", PUBLICADO);

        assertThat(ventana.inicio()).isEqualTo(cartagena(2026, 8, 21, 9, 0));
        assertThat(ventana.fin()).isEqualTo(cartagena(2026, 8, 21, 18, 0));
    }

    @Test
    void unHorarioQueCruzaLaMedianocheDebeTerminarAlDiaSiguiente() {
        Ventana ventana = LectorDeVentanaDeclarada.leer("El 21 de agosto entre las 8:00 p.m. y las 5:00 a.m.", PUBLICADO);

        assertThat(ventana.inicio()).isEqualTo(cartagena(2026, 8, 21, 20, 0));
        assertThat(ventana.fin()).isEqualTo(cartagena(2026, 8, 22, 5, 0));
    }

    @Test
    void lasDoceDebenInterpretarseComoMediodiaYMedianoche() {
        Ventana ventana = LectorDeVentanaDeclarada.leer("El 21 de agosto entre las 12:00 a.m. y las 12:00 p.m.", PUBLICADO);

        assertThat(ventana.inicio()).isEqualTo(cartagena(2026, 8, 21, 0, 0));
        assertThat(ventana.fin()).isEqualTo(cartagena(2026, 8, 21, 12, 0));
    }

    @Test
    void unBoletinDeDiciembreQueAnunciaEneroEsDelAnioSiguiente() {
        Ventana ventana = LectorDeVentanaDeclarada.leer(
                "El 2 de enero entre las 9:00 a.m. y las 5:00 p.m.", Instant.parse("2026-12-30T15:00:00Z"));

        assertThat(ventana.inicio()).isEqualTo(cartagena(2027, 1, 2, 9, 0));
    }

    @Test
    void elAnioExplicitoManda() {
        Ventana ventana = LectorDeVentanaDeclarada.leer(
                "El 5 de marzo de 2027 entre las 9:00 a.m. y las 5:00 p.m.", PUBLICADO);

        assertThat(ventana.inicio()).isEqualTo(cartagena(2027, 3, 5, 9, 0));
    }

    /** Un cumplimiento calculado contra una promesa que nadie hizo sería peor que no calcular ninguno. */
    @Test
    void sinHorarioExplicitoNoDebeInventarUnaVentana() {
        assertThat(LectorDeVentanaDeclarada.leer("Habrá trabajos en el sector mañana.", PUBLICADO).vacia()).isTrue();
        assertThat(LectorDeVentanaDeclarada.leer("", PUBLICADO).vacia()).isTrue();
        assertThat(LectorDeVentanaDeclarada.leer(null, PUBLICADO).vacia()).isTrue();
    }

    @Test
    void unHorarioSinFechaNoDebeInventarUnaVentana() {
        assertThat(LectorDeVentanaDeclarada.leer("Entre las 9:00 a.m. y las 6:00 p.m.", PUBLICADO).vacia()).isTrue();
    }

    @Test
    void unaFechaInexistenteNoDebeTumbarLaIngesta() {
        assertThat(LectorDeVentanaDeclarada.leer("El 31 de febrero entre las 9:00 a.m. y las 6:00 p.m.", PUBLICADO).vacia())
                .isTrue();
    }
}
