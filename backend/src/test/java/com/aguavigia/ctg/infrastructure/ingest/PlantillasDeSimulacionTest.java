package com.aguavigia.ctg.infrastructure.ingest;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Las plantillas de boletín del simulador (scripts/simulacion/lib/plantilla-boletin.mjs) tienen que producir lo que el guion espera del
 * extractor real: un corte con ventana y enumeración (confianza 0,85), una mención suelta (0,45, a la cola del veedor) y un aplazamiento
 * (que se reconoce y no crea un corte). Los textos son los que generan las plantillas; si cambian allí, cambian aquí.
 */
class PlantillasDeSimulacionTest {

    private static final Instant PUBLICADO = Instant.parse("2026-11-02T13:20:00Z");
    private final HeuristicaExtractor extractor = new HeuristicaExtractor();

    private static final String AVISO = "Aguas de Cartagena no publicó este boletín: es una SIMULACIÓN generada por el sistema para ensayar AguaVigía.";

    private static DocumentoCrudo boletin(String titulo, String html) {
        return DocumentoCrudo.de("acuacar", "https://simulacion.local/boletin/1", PUBLICADO,
                LimpiadorHtml.limpiar(titulo), LimpiadorHtml.limpiar(html));
    }

    private static final String CORTE = "<div class=\"gspb_text\"><strong>Cartagena de Indias, 2 de noviembre de 2026.</strong> Aguas de "
            + "Cartagena informa a la comunidad que este lunes 2 de noviembre, entre las 10:00 a. m. y las 4:00 p. m., ejecutará trabajos "
            + "programados en la red de acueducto.<br><br>Durante la ejecución de estos trabajos se presentará suspensión del servicio de "
            + "acueducto en los siguientes barrios y sectores:<br><br>Manga, Nelson Mandela, San Fernando.<br><br>" + AVISO + "</div>";

    private static final String MENCION_SUELTA = "<div class=\"gspb_text\">Aguas de Cartagena adelanta labores de mantenimiento preventivo "
            + "en distintos puntos de la ciudad, y podría presentarse una suspensión temporal del servicio de acueducto en el barrio Manga "
            + "por trabajos de la empresa.<br><br>" + AVISO + "</div>";

    private static final String APLAZAMIENTO = "<div class=\"gspb_text\">Aguas de Cartagena informa a la comunidad que se aplaza la "
            + "suspensión del servicio de acueducto programada para el martes 3 de noviembre en el barrio Crespo. Una nueva fecha será "
            + "anunciada oportunamente.<br><br>" + AVISO + "</div>";

    @Test
    void elBoletinDeCorteSeLeeConLaEnumeracionYLaVentana() {
        DocumentoCrudo documento = boletin("#2901 – [SIMULACIÓN] AGUAS DE CARTAGENA SUSPENDERÁ EL SERVICIO DE ACUEDUCTO EN VARIOS BARRIOS", CORTE);

        assertThat(PrefiltroDeterminista.posibleInterrupcionDeAcueducto(documento.texto())).isTrue();
        List<EventoExtraido> eventos = extractor.extraerPorZonas(documento);

        assertThat(eventos).hasSize(1);
        EventoExtraido evento = eventos.get(0);
        assertThat(evento.esInterrupcionDeAcueducto()).isTrue();
        assertThat(evento.sectoresMencionados()).containsExactlyInAnyOrder("Manga", "Nelson Mandela", "San Fernando");
        assertThat(evento.confianza()).isEqualTo(0.85);
        assertThat(evento.inicioDeclarado()).isEqualTo(Instant.parse("2026-11-02T15:00:00Z"));
        assertThat(evento.finPrometido()).isEqualTo(Instant.parse("2026-11-02T21:00:00Z"));
    }

    @Test
    void laMencionSueltaSeLeeConConfianzaBajaYSinVentana() {
        DocumentoCrudo documento = boletin("#2902 – [SIMULACIÓN] AGUAS DE CARTAGENA REALIZA LABORES DE MANTENIMIENTO EN LA CIUDAD", MENCION_SUELTA);

        assertThat(PrefiltroDeterminista.posibleInterrupcionDeAcueducto(documento.texto())).isTrue();
        List<EventoExtraido> eventos = extractor.extraerPorZonas(documento);

        assertThat(eventos).hasSize(1);
        assertThat(eventos.get(0).sectoresMencionados()).contains("Manga");
        assertThat(eventos.get(0).confianza()).isEqualTo(0.45);
        assertThat(eventos.get(0).inicioDeclarado()).isNull();
    }

    @Test
    void elAplazamientoSeReconoceYNoEsUnCorteNuevo() {
        DocumentoCrudo documento = boletin("#2903 – [SIMULACIÓN] AGUAS DE CARTAGENA INFORMA QUE SE APLAZA LA SUSPENSIÓN DEL SERVICIO DE ACUEDUCTO", APLAZAMIENTO);

        assertThat(PrefiltroDeterminista.posibleInterrupcionDeAcueducto(documento.texto())).isTrue();
        List<EventoExtraido> eventos = extractor.extraerPorZonas(documento);

        assertThat(eventos).hasSize(1);
        assertThat(eventos.get(0).tipo()).isEqualTo("AVISO_DE_ANULACION");
    }
}
