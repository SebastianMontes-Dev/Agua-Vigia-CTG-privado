package com.aguavigia.ctg.infrastructure.ingest;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class DocumentoCrudoTest {

    /** Una fuente hostil no puede colar `javascript:` en lo que el frontend pinta como enlace o imagen. */
    @Test
    void unEnlaceQueNoEsWebSeRechaza() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> DocumentoCrudo.de("acuacar", "javascript:alert(1)",
                        java.time.Instant.parse("2026-08-20T16:00:00Z"), "t", "texto"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unaImagenQueNoEsWebSeDescartaSinRechazarElDocumento() {
        DocumentoCrudo documento = DocumentoCrudo.de("acuacar", "https://acuacar.com/x",
                java.time.Instant.parse("2026-08-20T16:00:00Z"), "t", "texto", "data:text/html,<script>");

        org.assertj.core.api.Assertions.assertThat(documento.imagenUrl()).isNull();
    }

    @Test
    void unEnlaceSinUrlSeAcepta() {
        org.assertj.core.api.Assertions.assertThat(DocumentoCrudo.de("acuacar", null,
                java.time.Instant.parse("2026-08-20T16:00:00Z"), "t", "texto").urlOriginal()).isNull();
    }

    @Test
    void debeGenerarElMismoHashParaElMismoContenidoAunqueCambieElEspaciado() {
        DocumentoCrudo a = DocumentoCrudo.de("acuacar", "https://x/1", Instant.now(),
                "Suspensión programada", "El servicio se restablece a las 6pm");
        DocumentoCrudo b = DocumentoCrudo.de("google-news", "https://y/2", Instant.now(),
                "Suspensión   programada", "El servicio  se restablece a las 6pm  ");

        assertThat(a.hash()).isEqualTo(b.hash());
    }

    @Test
    void debeGenerarHashesDistintosParaContenidoDistinto() {
        DocumentoCrudo a = DocumentoCrudo.de("acuacar", "https://x/1", Instant.now(),
                "Suspensión en Manga", "Texto A");
        DocumentoCrudo b = DocumentoCrudo.de("acuacar", "https://x/2", Instant.now(),
                "Suspensión en Bocagrande", "Texto B");

        assertThat(a.hash()).isNotEqualTo(b.hash());
    }

    @Test
    void debeRechazarUnDocumentoSinFuente() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new DocumentoCrudo("", "url", Instant.now(), "t", "texto", "hash"));
    }

    @Test
    void debeRechazarUnDocumentoSinTexto() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                DocumentoCrudo.de("acuacar", "url", Instant.now(), "titulo", " "));
    }
}
