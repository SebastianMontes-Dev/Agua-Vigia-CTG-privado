package com.aguavigia.ctg.infrastructure.scheduling;

import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Escenario E145 (docs/reduccion/escenarios-de-negocio.md): cada tarea programada conserva su frecuencia y su nombre de propiedad.
 * Las busca por el nombre simple de la clase y del método, no por paquete, así que sigue valiendo cuando una fase de la reducción
 * mueva la clase; lo que falla es cambiar una frecuencia, una propiedad o el conjunto de tareas sin querer.
 */
class TareasProgramadasTest {

    private static final Map<String, String> ESPERADAS = Map.of(
            "EvaluacionPendienteJob#barrer", "fixedDelay=${aguavigia.consenso.barrido-ms:1000}",
            "PuestaAlDiaDeEstadosJob#ponerAlDiaEnUnaReplica",
            "initialDelay=${aguavigia.estado.puesta-al-dia-ms:300000} fixedDelay=${aguavigia.estado.puesta-al-dia-ms:300000}",
            "PipelineOrquestador#ejecutarCicloEnUnaReplica",
            "initialDelay=${aguavigia.ingesta.retraso-inicial-ms:60000} fixedDelay=${aguavigia.ingesta.intervalo-ms:600000}",
            "PlanificadorDeVentanas#revisarVentanasEnUnaReplica", "fixedDelay=${aguavigia.ingesta.ventanas-intervalo-ms:60000}",
            "LimpiezaFotosHuerfanasJob#limpiarEnUnaReplica", "cron=${aguavigia.mantenimiento.fotos-huerfanas.cron:0 0 3 * * *}",
            "PurgaEvidenciaAntiguaJob#purgarEnUnaReplica", "cron=${aguavigia.mantenimiento.retencion-evidencia.cron:0 30 3 * * *}",
            "SseSectoresBroadcaster#difundirPendiente", "fixedDelay=${aguavigia.sse.intervalo-difusion-ms:1000}",
            "SseSectoresBroadcaster#enviarLatido", "fixedDelay=${aguavigia.sse.intervalo-latido-ms:25000}");

    private static final Map<String, String> ESPERADAS_TELEGRAM = Map.of(
            "TelegramSondeoJob#sondear", "fixedDelay=${aguavigia.telegram.intervalo-ms:3000}");

    @Test
    void lasTareasProgramadasConservanSuFrecuenciaYSusPropiedades() {
        Map<String, String> reales = new TreeMap<>();
        var clases = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.aguavigia.ctg");
        for (var clase : clases) {
            for (JavaMethod metodo : clase.getMethods()) {
                if (metodo.isAnnotatedWith(Scheduled.class)) {
                    reales.put(clase.getSimpleName() + "#" + metodo.getName(), descripcion(metodo.reflect().getAnnotation(Scheduled.class)));
                }
            }
        }

        Map<String, String> esperadas = new TreeMap<>(ESPERADAS);
        esperadas.putAll(ESPERADAS_TELEGRAM);
        assertThat(reales)
                .as("las tareas programadas y su frecuencia (invariantes §4 de docs/reduccion)")
                .containsExactlyEntriesOf(esperadas);
    }

    private static String descripcion(Scheduled tarea) {
        var partes = new ArrayList<String>();
        if (!tarea.cron().isEmpty()) partes.add("cron=" + tarea.cron());
        if (!tarea.initialDelayString().isEmpty()) partes.add("initialDelay=" + tarea.initialDelayString());
        if (!tarea.fixedDelayString().isEmpty()) partes.add("fixedDelay=" + tarea.fixedDelayString());
        if (!tarea.fixedRateString().isEmpty()) partes.add("fixedRate=" + tarea.fixedRateString());
        return String.join(" ", partes);
    }
}
