package com.aguavigia.ctg.infrastructure.persistence.mongo;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * BUG-091 / RNF006 — un documento crudo que falló al procesarse, con su motivo. `RNF006` exige que
 * ningún fallo se descarte en silencio; antes de esto, `PipelineOrquestador.procesar` solo dejaba un
 * `log.warn` — evita perder el documento (se reintenta, ver su javadoc), pero no deja rastro
 * consultable de qué falló ni por qué.
 *
 * `_id` es el hash del documento (mismo hash que usa el deduplicador): un documento roto que se
 * reintenta cada ciclo actualiza su propia fila (contador de reintentos), no crea una nueva. Si
 * finalmente se procesa con éxito, su fila se borra — esto es una cola de lo que sigue roto *ahora*,
 * no un histórico permanente como `eventos_bitacora`.
 *
 * No pasa por un puerto de dominio, mismo criterio que {@code MarcaDeIngestaDocumento}: es
 * contabilidad del pipeline, no una regla del acueducto.
 */
@Getter
@Setter
@Document(collection = "documentos_fallidos")
public class DocumentoFallidoDocumento {

    @Id
    private String hash;

    private String fuente;
    private String urlOriginal;
    private String titulo;
    private String motivo;
    private Instant primerIntento;
    private Instant ultimoIntento;
    private int reintentos;

    public DocumentoFallidoDocumento() {
    }

    public DocumentoFallidoDocumento(String hash, String fuente, String urlOriginal, String titulo,
                                      String motivo, Instant primerIntento, Instant ultimoIntento, int reintentos) {
        this.hash = hash;
        this.fuente = fuente;
        this.urlOriginal = urlOriginal;
        this.titulo = titulo;
        this.motivo = motivo;
        this.primerIntento = primerIntento;
        this.ultimoIntento = ultimoIntento;
        this.reintentos = reintentos;
    }
}
