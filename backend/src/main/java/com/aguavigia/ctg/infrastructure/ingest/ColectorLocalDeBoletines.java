package com.aguavigia.ctg.infrastructure.ingest;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

/**
 * Modo sin internet de la ingesta (`aguavigia.ingesta.modo=local`, ADR-082): en vez de consultar a Acuacar y
 * a los feeds de prensa, lee boletines **reales** de Acuacar guardados en
 * `ingesta-local/boletines-acuacar.json`, tal como los devuelve su API pública. La demo funciona igual con o
 * sin red.
 *
 * Pasa por el mismo camino que un boletín en vivo —limpieza, deduplicación por hash, prefiltro, extractor y
 * propuesta PENDIENTE—, así que sigue valiendo ADR-028: nada llega al mapa sin que el veedor lo apruebe. Y como
 * el hash de un boletín es el mismo en vivo que aquí, volver al modo en vivo no duplica propuestas.
 *
 * No inventa nada: el archivo trae la fecha de captura y su origen. Para renovarlo, ver `ingesta-local/README.md`.
 */
@Component
@ConditionalOnProperty(prefix = "aguavigia.ingesta", name = "modo", havingValue = "local")
public class ColectorLocalDeBoletines implements FuenteDatosPort {

    private static final Logger log = LoggerFactory.getLogger(ColectorLocalDeBoletines.class);

    static final String RECURSO = "ingesta-local/boletines-acuacar.json";
    private static final String FUENTE = "acuacar";
    private static final ZoneId ZONA_CARTAGENA = ZoneId.of("America/Bogota");

    private final List<DocumentoCrudo> boletines;

    @Autowired
    public ColectorLocalDeBoletines(ObjectMapper mapper) {
        this(mapper, RECURSO);
    }

    ColectorLocalDeBoletines(ObjectMapper mapper, String recurso) {
        this.boletines = cargar(mapper, recurso);
        log.info("Ingesta en modo local: {} boletines reales de Acuacar cargados de '{}'", boletines.size(), recurso);
    }

    @Override
    public List<DocumentoCrudo> obtenerDesde(Instant desde) {
        return boletines.stream()
                .filter(documento -> documento.publicadoEn().isAfter(desde))
                .toList();
    }

    private static List<DocumentoCrudo> cargar(ObjectMapper mapper, String recurso) {
        try (InputStream entrada = new ClassPathResource(recurso).getInputStream()) {
            Archivo archivo = mapper.readValue(entrada, Archivo.class);
            return archivo.boletines().stream()
                    .map(ColectorLocalDeBoletines::aDocumento)
                    .filter(documento -> documento != null)
                    .sorted(Comparator.comparing(DocumentoCrudo::publicadoEn))
                    .toList();
        } catch (IOException noSePudoLeer) {
            throw new UncheckedIOException("No se pudo leer el archivo de boletines locales '" + recurso + "'", noSePudoLeer);
        }
    }

    private static DocumentoCrudo aDocumento(Boletin boletin) {
        String texto = LimpiadorHtml.limpiar(boletin.contenido());
        if (texto == null || texto.isBlank()) {
            return null;
        }
        Instant publicadoEn = LocalDateTime.parse(boletin.fecha()).atZone(ZONA_CARTAGENA).toInstant();
        return DocumentoCrudo.de(FUENTE, boletin.enlace(), publicadoEn,
                LimpiadorHtml.limpiar(boletin.titulo()), texto, boletin.portada());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Archivo(List<Boletin> boletines) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Boletin(String fecha, String enlace, String titulo, String contenido, String portada) {
    }
}
