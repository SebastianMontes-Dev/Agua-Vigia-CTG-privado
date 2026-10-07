package com.aguavigia.ctg.infrastructure.ingest;

import com.aguavigia.ctg.domain.BoletinSimulado;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ColectorSimuladoTest {

    private static final Instant T0 = Instant.parse("2026-11-02T13:00:00Z");

    private static BoletinSimulado boletin(long id, Instant fecha, String contenido) {
        return new BoletinSimulado(id, fecha, null, "[SIMULACIÓN] Suspensión " + id, contenido, null);
    }

    @Test
    void sinBoletinesNoDevuelveNada() {
        assertThat(new ColectorSimulado(true).obtenerDesde(Instant.EPOCH)).isEmpty();
    }

    @Test
    void devuelveTodosLosEncoladosEnOrdenCronologicoSinFiltrarPorLaMarca() {
        ColectorSimulado colector = new ColectorSimulado(true);
        colector.encolar(boletin(2, T0.plusSeconds(60), "<p>Segundo</p>"));
        colector.encolar(boletin(1, T0, "<p>Primero</p>"));
        colector.encolar(boletin(3, T0.plusSeconds(120), "<p>Tercero</p>"));

        List<DocumentoCrudo> leidos = colector.obtenerDesde(T0);

        // La deduplicación por hash evita repetir lo ya leído; filtrar por fecha perdía boletines si el reloj retrocedía (o llegaba uno con fecha vieja).
        assertThat(leidos).extracting(DocumentoCrudo::texto).containsExactly("Primero", "Segundo", "Tercero");
    }

    /** INGESTA_MODO=simulacion en la instancia real dejaría la ingesta muda (un buzón que nadie puede llenar): se dice al arrancar. */
    @Test
    void sinLaSimulacionHabilitadaNoArranca() {
        assertThatThrownBy(() -> new ColectorSimulado(false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AGUAVIGIA_SIM_HABILITADA");
    }

    /** Sale con la forma de un boletín de Acuacar: fuente `acuacar`, HTML limpio y un enlace que dice que es simulado. */
    @Test
    void convierteElBoletinAlDocumentoDeAcuacarConElHtmlLimpio() {
        ColectorSimulado colector = new ColectorSimulado(true);
        colector.encolar(boletin(5, T0, "<p>Manga: <b>10:00</b> a 16:00</p>"));

        DocumentoCrudo documento = colector.obtenerDesde(Instant.EPOCH).get(0);

        assertThat(documento.fuente()).isEqualTo("acuacar");
        assertThat(documento.texto()).isEqualTo("Manga: 10:00 a 16:00");
        assertThat(documento.titulo()).isEqualTo("[SIMULACIÓN] Suspensión 5");
        assertThat(documento.urlOriginal()).isEqualTo("https://simulacion.local/boletin/5");
        assertThat(documento.publicadoEn()).isEqualTo(T0);
    }

    @Test
    void respetaElEnlaceYLaPortadaSiLosTrae() {
        ColectorSimulado colector = new ColectorSimulado(true);
        colector.encolar(new BoletinSimulado(9, T0, "https://simulacion.local/x", "[SIMULACIÓN] t", "<p>c</p>",
                "https://simulacion.local/portada.png"));

        DocumentoCrudo documento = colector.obtenerDesde(Instant.EPOCH).get(0);

        assertThat(documento.urlOriginal()).isEqualTo("https://simulacion.local/x");
        assertThat(documento.imagenUrl()).isEqualTo("https://simulacion.local/portada.png");
    }

    /** Un boletín repetido produce el mismo hash: el deduplicador lo descarta igual que a uno en vivo. */
    @Test
    void elMismoBoletinDosVecesTieneElMismoHash() {
        ColectorSimulado colector = new ColectorSimulado(true);
        colector.encolar(boletin(1, T0, "<p>Igual</p>"));
        colector.encolar(boletin(1, T0.plusSeconds(5), "<p>Igual</p>"));

        List<DocumentoCrudo> leidos = colector.obtenerDesde(Instant.EPOCH);

        assertThat(leidos).hasSize(2);
        assertThat(leidos.get(0).hash()).isEqualTo(leidos.get(1).hash());
    }

    @Test
    void reiniciarVaciaElBuzon() {
        ColectorSimulado colector = new ColectorSimulado(true);
        colector.encolar(boletin(1, T0, "<p>x</p>"));

        colector.vaciar();

        assertThat(colector.obtenerDesde(Instant.EPOCH)).isEmpty();
    }
}
