package com.aguavigia.ctg.infrastructure.ingest;

import com.aguavigia.ctg.domain.Sector;
import com.aguavigia.ctg.domain.SectorId;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADR-082, de extremo a extremo y sin mocks: los boletines reales guardados, el prefiltro y el extractor reales,
 * y el catálogo de los 213 barrios de `data/geoespacial`. Comprueba que el modo sin internet le da al veedor
 * propuestas con qué trabajar, no un colector «arriba» que no produce nada.
 */
class IngestaLocalDeExtremoAExtremoTest {

    private static final Path GEOJSON = Path.of("..", "data", "geoespacial", "barrios-cartagena.geojson");

    private static List<Sector> catalogo() throws IOException {
        JsonNode raiz = new ObjectMapper().readTree(Files.readString(GEOJSON));
        List<Sector> sectores = new ArrayList<>();
        for (JsonNode barrio : raiz.get("features")) {
            String nombre = barrio.get("properties").get("NOMBRE").asText();
            String slug = Normalizer.normalize(nombre, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                    .toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
            sectores.add(new Sector(new SectorId(slug + "-" + sectores.size()), nombre, 1000, null));
        }
        return sectores;
    }

    @Test
    void losBoletinesGuardadosDebenProducirPropuestasParaBarriosReales() throws IOException {
        var colector = new ColectorLocalDeBoletines(new ObjectMapper());
        var extractor = new HeuristicaExtractor();
        var emparejador = new EmparejadorDeSectores(catalogo());

        int propuestas = 0;
        int conVentana = 0;
        for (DocumentoCrudo documento : colector.obtenerDesde(Instant.parse("2020-01-01T00:00:00Z"))) {
            if (!PrefiltroDeterminista.posibleInterrupcionDeAcueducto(documento.texto())) {
                continue;
            }
            for (EventoExtraido evento : extractor.extraerPorZonas(documento)) {
                if (!evento.esInterrupcionDeAcueducto()) {
                    continue;
                }
                int sectores = emparejador.emparejar(evento.sectoresMencionados()).sectores().size();
                propuestas += sectores;
                if (sectores > 0 && evento.inicioDeclarado() != null) {
                    conVentana += sectores;
                }
                assertThat(evento.citaTextual()).as("ADR-006: toda propuesta cita la frase del boletín").isNotBlank();
            }
        }

        System.out.println("[ingesta local] propuestas=" + propuestas + " con ventana declarada=" + conVentana);
        assertThat(propuestas).as("propuestas que salen de los boletines guardados").isGreaterThanOrEqualTo(3);
    }
}
