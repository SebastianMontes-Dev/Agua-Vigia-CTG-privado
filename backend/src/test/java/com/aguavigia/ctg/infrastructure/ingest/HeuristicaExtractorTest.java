package com.aguavigia.ctg.infrastructure.ingest;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El caso principal es el texto literal del boletín #2854 de Acuacar (21/08/2026), no un ejemplo
 * inventado: la versión anterior del extractor pasaba sus pruebas con textos de laboratorio y
 * devolvía {@code ["del entorno"]} sobre este, perdiendo los 20 barrios afectados.
 */
class HeuristicaExtractorTest {

    private static final Instant PUBLICADO = Instant.parse("2026-08-20T16:32:00Z");

    /** Recorte literal del #2854, con la redacción y la puntuación de la fuente. */
    private static final String BOLETIN_2854 = """
            #2854-AGUA DE CARTAGENA REPARA FUGA EN TUBERIA DE ACUEDUCTO EN AVENIDA EL CONSULADO \
            Habrá suspensión del servicio de acueducto a barrios del entorno. \
            Cartagena de Indias, 20 de agosto de 2026 . Aguas de Cartagena identificó una fuga en un \
            tramo de tubería del sistema de acueducto de 500 milímetros de diámetro, en el sector El \
            Consulado. Por tal motivo, la empresa ha programado la realización de trabajos de \
            reparación de la red de manera segura y eficiente, que conllevan a la suspensión temporal \
            del suministro de agua, mañana viernes 21 de agosto, entre las 9:00 a.m. y las 6:00 p.m. \
            en los siguientes barrios: Armenia, sector Sena, El Cairo, Escallón Villa, La Floresta, \
            Las Gaviotas, Tacarigua, Buenos Aires, Los Ángeles, Villa Sandra, Los Ejecutivos, \
            San Antonio.""";

    private final HeuristicaExtractor extractor = new HeuristicaExtractor();

    /** Boletines de una sola zona: un solo evento. Los de varias zonas se prueban con los boletines reales. */
    private EventoExtraido extraer(DocumentoCrudo documento) {
        List<EventoExtraido> eventos = extractor.extraerPorZonas(documento);
        assertThat(eventos).hasSize(1);
        return eventos.get(0);
    }

    private EventoExtraido extraerBoletin2854() {
        return extraer(DocumentoCrudo.de(
                "acuacar", "https://acuacar.com/2854", PUBLICADO, "#2854", BOLETIN_2854));
    }

    @Test
    void debeLeerLosBarriosDeLaEnumeracionYNoLaFraseDeResumen() {
        EventoExtraido evento = extraerBoletin2854();

        assertThat(evento.sectoresMencionados())
                .contains("Armenia", "El Cairo", "Escallón Villa", "La Floresta", "Las Gaviotas",
                        "Tacarigua", "Buenos Aires", "Los Ángeles", "Villa Sandra",
                        "Los Ejecutivos", "San Antonio")
                .doesNotContain("del entorno");
    }

    @Test
    void debeIgnorarLasMencionesGenericasDeBarrios() {
        EventoExtraido evento = extraerBoletin2854();

        assertThat(evento.sectoresMencionados())
                .noneMatch(nombre -> nombre.toLowerCase().contains("entorno"));
    }

    @Test
    void debeLeerLaVentanaPrometidaEnHoraDeCartagena() {
        EventoExtraido evento = extraerBoletin2854();

        // 21 de agosto, 9:00 a.m. y 6:00 p.m. en UTC-5.
        assertThat(evento.inicioDeclarado()).isEqualTo(Instant.parse("2026-08-21T14:00:00Z"));
        assertThat(evento.finPrometido()).isEqualTo(Instant.parse("2026-08-21T23:00:00Z"));
    }

    @Test
    void debeTomarLaFechaDelCorteYNoLaDeLaLineaDeFecha() {
        EventoExtraido evento = extraerBoletin2854();

        // El boletín se fecha el 20 pero los trabajos son «mañana viernes 21».
        assertThat(evento.inicioDeclarado()).isAfter(Instant.parse("2026-08-21T00:00:00Z"));
    }

    @Test
    void debeGraduarLaConfianzaSegunLaEvidenciaEncontrada() {
        EventoExtraido conVentana = extraerBoletin2854();

        assertThat(conVentana.confianza())
                .isEqualTo(HeuristicaExtractor.CONFIANZA_ENUMERACION_CON_VENTANA);
    }

    @Test
    void debeBajarLaConfianzaCuandoLaEnumeracionNoTraeHorario() {
        String sinHorario = "Habrá suspensión del servicio en los siguientes barrios: Manga, Bocagrande.";

        EventoExtraido evento = extraer(DocumentoCrudo.de(
                "acuacar", "https://acuacar.com/x", PUBLICADO, "aviso", sinHorario));

        assertThat(evento.confianza()).isEqualTo(HeuristicaExtractor.CONFIANZA_ENUMERACION);
        assertThat(evento.camposInferidos()).contains("inicioDeclarado", "finPrometido");
    }

    @Test
    void debeDeclararLosCamposQueNoSupoLeerEnVezDeInventarlos() {
        EventoExtraido evento = extraer(DocumentoCrudo.de(
                "acuacar", "https://acuacar.com/x", PUBLICADO, "aviso",
                "Suspensión del servicio en los siguientes barrios: Manga."));

        assertThat(evento.inicioDeclarado()).isNull();
        assertThat(evento.finPrometido()).isNull();
    }

    /** Lo que el veedor necesita contrastar: qué pasa, cuándo y en qué barrios, en una sola cita. */
    @Test
    void laCitaTextualDebeMostrarLaListaDeBarriosYNoLaFraseDeResumen() {
        EventoExtraido evento = extraerBoletin2854();

        assertThat(evento.citaTextual())
                .contains("siguientes barrios")
                .contains("Armenia")
                .doesNotContain("barrios del entorno");
    }

    @Test
    void laCitaTextualDebeSerLiteralDelBoletin() {
        EventoExtraido evento = extraerBoletin2854();

        String sinElipsis = evento.citaTextual().replace("…", "").strip();
        assertThat(BOLETIN_2854).contains(sinElipsis);
    }

    @Test
    void debeReconocerElRestablecimientoComoServicioNormal() {
        EventoExtraido evento = extraer(DocumentoCrudo.de(
                "acuacar", "https://acuacar.com/x", PUBLICADO, "aviso",
                "Se completó el restablecimiento del servicio en los siguientes barrios: Manga."));

        assertThat(evento.tipo()).isEqualTo("SERVICIO_NORMAL");
    }

    /**
     * Un aviso de restablecimiento suele recordar la suspensión que termina («tras la suspensión
     * temporal…»). Mencionarla no lo convierte en un corte nuevo: el barrido lo leería como
     * SIN_SERVICIO y dejaría el barrio sin agua en el mapa justo cuando Acuacar dice que volvió.
     */
    @Test
    void debeReconocerElRestablecimientoAunqueMencioneLaSuspensionQueTermina() {
        EventoExtraido evento = extraer(DocumentoCrudo.de(
                "acuacar", "https://acuacar.com/x", PUBLICADO, "aviso",
                "Tras la suspensión temporal del suministro, se completó el restablecimiento del "
                        + "servicio en los siguientes barrios: Manga, Bocagrande."));

        assertThat(evento.tipo()).isEqualTo("SERVICIO_NORMAL");
    }

    @Test
    void noDebeProponerNadaCuandoElBoletinNoNombraNingunBarrio() {
        EventoExtraido evento = extraer(DocumentoCrudo.de(
                "acuacar", "https://acuacar.com/x", PUBLICADO, "aviso",
                "Aguas de Cartagena informa que se presentarán intermitencias en algunos "
                        + "sectores de la ciudad durante la temporada seca."));

        assertThat(evento.esInterrupcionDeAcueducto()).isFalse();
    }

    @Test
    void debeRecorrerTodasLasEnumeracionesDelBoletinYNoSoloLaPrimera() {
        String dosListas = "Suspensión del servicio en los siguientes barrios: Manga, Bocagrande. "
                + "Nelson Mandela, sectores: Los Olivos, Las Vegas.";

        EventoExtraido evento = extraer(DocumentoCrudo.de(
                "acuacar", "https://acuacar.com/x", PUBLICADO, "aviso", dosListas));

        assertThat(evento.sectoresMencionados())
                .contains("Manga", "Bocagrande", "Los Olivos", "Las Vegas");
    }

    // --- Restablecimiento frente a suspensión (plan 3.2) ---

    /** Lo que Acuacar escribió el 11/09/2026: «culminó… restablecer… se inició el restablecimiento progresivo del servicio». */
    @Test
    void elRestablecimientoProgresivoDelServicioEsServicioNormal() {
        EventoExtraido evento = extraer(DocumentoCrudo.de(
                "acuacar", "https://acuacar.com/x", PUBLICADO, "aviso",
                "Se inició el restablecimiento progresivo del servicio de acueducto en los siguientes "
                        + "barrios: Manga, Bocagrande, que tuvieron suspensión desde ayer."));

        assertThat(evento.tipo()).isEqualTo("SERVICIO_NORMAL");
        assertThat(evento.sectoresMencionados()).contains("Manga", "Bocagrande");
    }

    /**
     * El otro lado: un aviso que anuncia una suspensión también dice «restablecer las condiciones óptimas de operación»
     * (boletín #2830). Mencionar el restablecimiento no lo vuelve un aviso de restablecimiento.
     */
    @Test
    void unaSuspensionProgramadaQueHablaDeRestablecerNoEsUnRestablecimiento() {
        EventoExtraido evento = extraer(DocumentoCrudo.de(
                "acuacar", "https://acuacar.com/x", PUBLICADO, "aviso",
                "Habrá suspensión del servicio de acueducto para restablecer las condiciones óptimas de la red, "
                        + "mañana viernes 21 de agosto, entre las 9:00 a.m. y las 6:00 p.m. en los siguientes "
                        + "barrios: Manga, Bocagrande."));

        assertThat(evento.tipo()).isEqualTo("SUSPENSION_PROGRAMADA");
    }

    /** Hallazgo de seguridad: un aviso de que se va el agua que promete «un restablecimiento gradual» no es un restablecimiento. */
    @Test
    void unaInterrupcionQuePrometeRestablecerGradualmenteNoEsUnRestablecimiento() {
        EventoExtraido evento = extraer(DocumentoCrudo.de(
                "acuacar", "https://acuacar.com/x", PUBLICADO, "aviso",
                "Se interrumpirá el servicio de acueducto y el restablecimiento del servicio será gradual. "
                        + "Barrios afectados: Manga, Bocagrande."));

        assertThat(evento.tipo()).isEqualTo("SUSPENSION_PROGRAMADA");
    }

    // --- Aplazamientos y cancelaciones (defensivo: no aparecen en los 13 boletines reales) ---

    @Test
    void unAvisoDeAplazamientoNoEsUnCorteNuevo() {
        EventoExtraido evento = extraer(DocumentoCrudo.de(
                "acuacar", "https://acuacar.com/x", PUBLICADO, "aviso",
                "Aguas de Cartagena informa que la suspensión del servicio programada para mañana se aplaza "
                        + "por condiciones climáticas, en los siguientes barrios: Crespo, Manga."));

        assertThat(evento.tipo()).isEqualTo("AVISO_DE_ANULACION");
        assertThat(evento.sectoresMencionados()).contains("Crespo", "Manga");
    }

    @Test
    void unaSuspensionCanceladaTampocoEsUnCorteNuevo() {
        EventoExtraido evento = extraer(DocumentoCrudo.de(
                "acuacar", "https://acuacar.com/x", PUBLICADO, "aviso",
                "Se cancela la suspensión anunciada en los siguientes barrios: Crespo."));

        assertThat(evento.tipo()).isEqualTo("AVISO_DE_ANULACION");
    }

    // --- Formatos horarios (plan 3.2) ---

    private EventoExtraido conTexto(String texto) {
        return extraer(DocumentoCrudo.de("acuacar", "https://acuacar.com/x", PUBLICADO, "aviso", texto));
    }

    /** «desde las 7:00 a. m. del miércoles 29 de julio hasta la 1:00 a. m. del jueves 30 de julio» (boletín #2838). */
    @Test
    void debeLeerUnRangoConFechaEnCadaExtremo() {
        EventoExtraido evento = conTexto("Habrá suspensión del servicio desde las 7:00 a. m. del miércoles 29 de julio "
                + "hasta la 1:00 a. m. del jueves 30 de julio en los siguientes barrios: Manga.");

        assertThat(evento.inicioDeclarado()).isEqualTo(Instant.parse("2026-07-29T12:00:00Z"));
        assertThat(evento.finPrometido()).isEqualTo(Instant.parse("2026-07-30T06:00:00Z"));
    }

    @Test
    void debeLeerElFinDelMismoDiaYLaMedianoche() {
        EventoExtraido evento = conTexto("Suspensión del servicio desde las 12:00 de la medianoche del miércoles "
                + "30 de septiembre hasta las 11:00 p. m. del mismo día en los siguientes barrios: Manga.");

        assertThat(evento.inicioDeclarado()).isEqualTo(Instant.parse("2026-09-30T05:00:00Z"));
        assertThat(evento.finPrometido()).isEqualTo(Instant.parse("2026-10-01T04:00:00Z"));
    }

    @Test
    void debeLeerUnRangoSinPalabraEntreYConHoras24() {
        EventoExtraido evento = conTexto("Suspensión del servicio el 21 de agosto, de 08:00 a 16:00 horas, "
                + "en los siguientes barrios: Manga.");

        assertThat(evento.inicioDeclarado()).isEqualTo(Instant.parse("2026-08-21T13:00:00Z"));
        assertThat(evento.finPrometido()).isEqualTo(Instant.parse("2026-08-21T21:00:00Z"));
    }

    @Test
    void debeLeerUnRangoConDeYAAmPm() {
        EventoExtraido evento = conTexto("Suspensión del servicio el 21 de agosto, de 8:00 a.m. a 4:00 p.m., "
                + "en los siguientes barrios: Manga.");

        assertThat(evento.inicioDeclarado()).isEqualTo(Instant.parse("2026-08-21T13:00:00Z"));
        assertThat(evento.finPrometido()).isEqualTo(Instant.parse("2026-08-21T21:00:00Z"));
    }

    @Test
    void unRangoQueCruzaLaMedianocheTerminaAlDiaSiguiente() {
        EventoExtraido evento = conTexto("Suspensión del servicio el 21 de agosto entre las 10:00 p. m. y las "
                + "6:00 a. m. en los siguientes barrios: Manga.");

        assertThat(evento.inicioDeclarado()).isEqualTo(Instant.parse("2026-08-22T03:00:00Z"));
        assertThat(evento.finPrometido()).isEqualTo(Instant.parse("2026-08-22T11:00:00Z"));
    }

    @Test
    void unaEnumeracionConProgramadosAntesDeLosDosPuntosSigueSiendoEnumeracion() {
        EventoExtraido evento = conTexto("Suspensión del servicio. Barrios y sectores programados: Manga, Bocagrande.");

        assertThat(evento.sectoresMencionados()).contains("Manga", "Bocagrande");
    }
}
