package com.aguavigia.ctg.infrastructure.ingest;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Los boletines llegan como HTML renderizado y el hash de deduplicación se calcula sobre el texto
 * limpio: si esta clase se comportara distinto entre versiones, el mismo boletín dejaría de
 * deduplicar y el veedor vería propuestas repetidas.
 */
class LimpiadorHtmlTest {

    /** Un cuerpo hostil o enorme no debe congelar el ciclo de ingesta: el texto tiene tope. */
    @Test
    void elTextoLimpioTieneUnTopeDeLongitud() {
        String enorme = "a ".repeat(2_000_000);

        assertThat(LimpiadorHtml.limpiar(enorme).length()).isLessThanOrEqualTo(LimpiadorHtml.LARGO_MAXIMO);
    }

    @Test
    void unBoletinNormalNoSeRecorta() {
        assertThat(LimpiadorHtml.limpiar("<p>Corte en <b>Manga</b></p>")).isEqualTo("Corte en Manga");
    }

    @Test
    void debeQuitarLasEtiquetas() {
        assertThat(LimpiadorHtml.limpiar("<p>Suspensión en <strong>Manga</strong></p>"))
                .isEqualTo("Suspensión en Manga");
    }

    @Test
    void debeDecodificarLasEntidadesHtml() {
        assertThat(LimpiadorHtml.limpiar("Da&ntilde;o en la matriz &amp; suspensi&oacute;n"))
                .isEqualTo("Daño en la matriz & suspensión");
    }

    @Test
    void debeColapsarLosEspaciosRepetidosQueDejanLasEtiquetas() {
        assertThat(LimpiadorHtml.limpiar("<div>  Manga  </div>\n\n<div>Crespo</div>"))
                .isEqualTo("Manga Crespo");
    }

    @Test
    void debeManejarEtiquetasAnidadasYConAtributos() {
        assertThat(LimpiadorHtml.limpiar("<div class=\"nota\"><a href=\"http://x\">Ver más</a></div>"))
                .isEqualTo("Ver más");
    }

    @Test
    void unHtmlNuloDebeDarCadenaVacia() {
        assertThat(LimpiadorHtml.limpiar(null)).isEmpty();
    }

    @Test
    void unTextoSinHtmlDebeQuedarIgual() {
        assertThat(LimpiadorHtml.limpiar("Suspensión en Manga")).isEqualTo("Suspensión en Manga");
    }
}
