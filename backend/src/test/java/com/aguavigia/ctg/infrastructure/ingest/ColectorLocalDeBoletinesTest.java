package com.aguavigia.ctg.infrastructure.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ADR-082: el archivo `ingesta-local/boletines-acuacar.json` son boletines reales de Acuacar. Estas pruebas leen
 * ese archivo de verdad, así que también avisan si alguien lo rompe o lo vacía.
 */
class ColectorLocalDeBoletinesTest {

    private final ColectorLocalDeBoletines colector = new ColectorLocalDeBoletines(new ObjectMapper());

    private List<DocumentoCrudo> todos() {
        return colector.obtenerDesde(Instant.parse("2020-01-01T00:00:00Z"));
    }

    @Test
    void debeCargarLosBoletinesRealesDeAcuacarEnOrdenCronologico() {
        List<DocumentoCrudo> documentos = todos();

        assertThat(documentos).hasSizeGreaterThanOrEqualTo(10);
        assertThat(documentos).extracting(DocumentoCrudo::fuente).containsOnly("acuacar");
        assertThat(documentos).extracting(DocumentoCrudo::urlOriginal)
                .allSatisfy(url -> assertThat(url).startsWith("https://www.acuacar.com/"));
        assertThat(documentos).extracting(DocumentoCrudo::publicadoEn).isSorted();
    }

    @Test
    void cadaBoletinDebeLlegarLimpioDeHtmlYConHashParaDeduplicar() {
        assertThat(todos()).allSatisfy(documento -> {
            assertThat(documento.texto()).isNotBlank().doesNotContain("<p>").doesNotContain("</");
            assertThat(documento.titulo()).isNotBlank().doesNotContain("&#8211;");
            assertThat(documento.hash()).hasSize(64);
        });
    }

    @Test
    void soloDebeEntregarLosPublicadosDespuesDeLaMarca() {
        List<DocumentoCrudo> todos = todos();
        Instant marca = todos.get(todos.size() - 1).publicadoEn();

        assertThat(colector.obtenerDesde(marca)).isEmpty();
        assertThat(colector.obtenerDesde(marca.minusSeconds(1))).hasSize(1);
    }

    private ApplicationContextRunner contexto() {
        return new ApplicationContextRunner()
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withUserConfiguration(ColectorLocalDeBoletines.class);
    }

    @Test
    void elModoLocalSoloDebeActivarseSiSePideExplicitamente() {
        contexto().run(ctx -> assertThat(ctx).doesNotHaveBean(ColectorLocalDeBoletines.class));
        contexto().withPropertyValues("aguavigia.ingesta.modo=en-vivo")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(ColectorLocalDeBoletines.class));
        contexto().withPropertyValues("aguavigia.ingesta.modo=local")
                .run(ctx -> assertThat(ctx).hasSingleBean(ColectorLocalDeBoletines.class));
    }

    @Test
    void unArchivoQueNoExisteDebeFallarConUnMensajeQueDigaCual() {
        assertThatThrownBy(() -> new ColectorLocalDeBoletines(new ObjectMapper(), "ingesta-local/no-existe.json"))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("ingesta-local/no-existe.json");
    }
}
