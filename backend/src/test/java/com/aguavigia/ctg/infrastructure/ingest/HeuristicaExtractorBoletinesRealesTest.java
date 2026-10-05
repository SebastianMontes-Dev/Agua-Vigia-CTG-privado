package com.aguavigia.ctg.infrastructure.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Los 13 boletines reales de Acuacar guardados en `ingesta-local/` como fixtures (plan 3.2): lo que la fuente escribe de
 * verdad, no ejemplos de laboratorio. Cada prueba nombra el boletín por su número.
 */
class HeuristicaExtractorBoletinesRealesTest {

    private static Map<String, DocumentoCrudo> boletines;
    private final HeuristicaExtractor extractor = new HeuristicaExtractor();

    @BeforeAll
    static void cargar() {
        var numero = java.util.regex.Pattern.compile("#\\d+");
        boletines = new ColectorLocalDeBoletines(new ObjectMapper())
                .obtenerDesde(Instant.parse("2020-01-01T00:00:00Z")).stream()
                .collect(Collectors.toMap(d -> {
                    var m = numero.matcher(d.titulo());
                    return m.find() ? m.group() : d.titulo();
                }, Function.identity()));
    }

    private List<EventoExtraido> zonasDe(String numero) {
        assertThat(boletines).as("boletín " + numero).containsKey(numero);
        return extractor.extraerPorZonas(boletines.get(numero));
    }

    /** Dos «Grupo N Desde las… hasta las… Barrios y sectores:», cada uno con su propio horario. */
    @Test
    void elBoletin2865TieneDosZonasConSuPropiaVentana() {
        List<EventoExtraido> zonas = zonasDe("#2865");

        assertThat(zonas).hasSize(2);
        EventoExtraido grupo1 = zonas.get(0);
        EventoExtraido grupo2 = zonas.get(1);

        assertThat(grupo1.inicioDeclarado()).isEqualTo(Instant.parse("2026-09-09T16:00:00Z"));
        assertThat(grupo1.finPrometido()).isEqualTo(Instant.parse("2026-09-10T09:00:00Z"));
        assertThat(grupo2.inicioDeclarado()).isEqualTo(Instant.parse("2026-09-10T13:00:00Z"));
        assertThat(grupo2.finPrometido()).isEqualTo(Instant.parse("2026-09-11T09:00:00Z"));

        assertThat(grupo1.sectoresMencionados()).contains("María Auxiliadora", "Boston", "El Líbano")
                .doesNotContain("San Fernando", "Ternera");
        assertThat(grupo2.sectoresMencionados()).contains("San Fernando", "Villa León")
                .doesNotContain("María Auxiliadora", "Boston");
        assertThat(grupo1.esInterrupcionDeAcueducto()).isTrue();
        assertThat(grupo1.tipo()).isEqualTo("SUSPENSION_PROGRAMADA");
    }

    /** La cita de cada zona es literal y trae su horario, para que el veedor contraste esa zona y no otra. */
    @Test
    void laCitaDeCadaZonaDelBoletin2865EsLiteralYTraeSuHorario() {
        String texto = boletines.get("#2865").texto();
        List<EventoExtraido> zonas = zonasDe("#2865");

        for (EventoExtraido zona : zonas) {
            assertThat(texto).contains(zona.citaTextual().replace("…", "").strip());
        }
        assertThat(zonas.get(0).citaTextual()).contains("9 de septiembre");
        assertThat(zonas.get(1).citaTextual()).contains("10 de septiembre");
    }

    /** Una ventana global de 40 h al abrir y dos «Horario de suspensión» por día: las zonas son las de cada día. */
    @Test
    void elBoletin2878TomaLosDosHorariosPorDiaYNoLaVentanaGlobal() {
        List<EventoExtraido> zonas = zonasDe("#2878");

        assertThat(zonas).hasSize(2);
        assertThat(zonas.get(0).inicioDeclarado()).isEqualTo(Instant.parse("2026-09-29T12:00:00Z"));
        assertThat(zonas.get(0).finPrometido()).isEqualTo(Instant.parse("2026-09-30T08:00:00Z"));
        assertThat(zonas.get(1).inicioDeclarado()).isEqualTo(Instant.parse("2026-09-30T05:00:00Z"));
        assertThat(zonas.get(1).finPrometido()).isEqualTo(Instant.parse("2026-10-01T04:00:00Z"));

        assertThat(zonas.get(0).sectoresMencionados()).contains("Bocagrande", "Castillogrande", "Crespo");
        assertThat(zonas.get(1).sectoresMencionados()).doesNotContain("Bocagrande", "Crespo");
    }

    @Test
    void elBoletin2838LeeSuVentanaConFechaEnCadaExtremo() {
        List<EventoExtraido> zonas = zonasDe("#2838");

        assertThat(zonas).hasSize(1);
        EventoExtraido evento = zonas.get(0);
        assertThat(evento.inicioDeclarado()).isEqualTo(Instant.parse("2026-07-29T12:00:00Z"));
        assertThat(evento.finPrometido()).isEqualTo(Instant.parse("2026-07-30T06:00:00Z"));
        assertThat(evento.sectoresMencionados()).contains("Albornoz", "Nelson Mandela");
        assertThat(evento.confianza()).isEqualTo(HeuristicaExtractor.CONFIANZA_ENUMERACION_CON_VENTANA);
    }

    @Test
    void elBoletin2854SigueDandoUnaSolaZonaConSuHorario() {
        List<EventoExtraido> zonas = zonasDe("#2854");

        assertThat(zonas).hasSize(1);
        assertThat(zonas.get(0).inicioDeclarado()).isEqualTo(Instant.parse("2026-08-21T14:00:00Z"));
        assertThat(zonas.get(0).finPrometido()).isEqualTo(Instant.parse("2026-08-21T23:00:00Z"));
        assertThat(zonas.get(0).sectoresMencionados()).contains("Armenia", "El Cairo", "Tacarigua");
    }

    @Test
    void elBoletin2830LeeElHorarioDelDiaQueAnuncia() {
        EventoExtraido evento = zonasDe("#2830").get(0);

        assertThat(evento.inicioDeclarado()).isEqualTo(Instant.parse("2026-07-16T13:00:00Z"));
        assertThat(evento.finPrometido()).isEqualTo(Instant.parse("2026-07-17T01:00:00Z"));
    }

    /**
     * «Se inició el restablecimiento progresivo del servicio… en los barrios que tuvieron suspensión»: no nombra
     * barrios, así que no se puede atribuir a ninguno; pero tampoco es una suspensión nueva.
     */
    @Test
    void elBoletin2868EsUnRestablecimientoSinBarriosNombrados() {
        EventoExtraido evento = zonasDe("#2868").get(0);

        assertThat(evento.tipo()).isEqualTo("SERVICIO_NORMAL");
        assertThat(evento.esInterrupcionDeAcueducto()).isFalse();
    }

    /** Un parte de avance no es un corte nuevo: ninguno de los dos partes de la jornada (#2840, #2841) propone nada. */
    @Test
    void losPartesDeAvanceNoProponenCortes() {
        for (String numero : List.of("#2840", "#2841")) {
            assertThat(zonasDe(numero)).as(numero).allMatch(z -> !z.esInterrupcionDeAcueducto());
        }
    }

    /** Ningún boletín real produce una zona sin cita, y todas las ventanas leídas son coherentes (fin posterior al inicio). */
    @Test
    void todosLosBoletinesRealesDanZonasCoherentes() {
        for (Map.Entry<String, DocumentoCrudo> entrada : boletines.entrySet()) {
            for (EventoExtraido zona : extractor.extraerPorZonas(entrada.getValue())) {
                assertThat(zona.citaTextual()).as(entrada.getKey()).isNotBlank();
                if (zona.inicioDeclarado() != null && zona.finPrometido() != null) {
                    assertThat(zona.finPrometido()).as(entrada.getKey()).isAfter(zona.inicioDeclarado());
                }
            }
        }
    }
}
