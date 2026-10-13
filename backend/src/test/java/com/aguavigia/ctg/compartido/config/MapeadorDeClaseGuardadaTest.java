package com.aguavigia.ctg.compartido.config;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El mecanismo de ADR-099 para no cambiar la base al mover los {@code @Document}: lo que se guarda en {@code _class} lo fija la
 * anotación, y lo que ya estaba guardado se sigue leyendo aunque la clase haya cambiado de paquete.
 */
class MapeadorDeClaseGuardadaTest {

    private static final String NOMBRE_DE_ANTES = "com.aguavigia.ctg.infrastructure.persistence.mongo.MovidoDocumento";

    @ClaseGuardadaComo(NOMBRE_DE_ANTES)
    static class Movido {
        String id;
        String nombre;
    }

    static class SinAnotar {
        String id;
        String nombre;
    }

    private static MappingMongoConverter conversor() {
        var contexto = new MongoMappingContext();
        contexto.setInitialEntitySet(Set.of(Movido.class, SinAnotar.class));
        contexto.afterPropertiesSet();
        var conversor = new MappingMongoConverter(NoOpDbRefResolver.INSTANCE, contexto);
        conversor.setTypeMapper(MongoClaseGuardadaConfig.mapeador());
        conversor.afterPropertiesSet();
        return conversor;
    }

    @Test
    void unaClaseAnotadaEscribeElNombreQueTeniaAntesDeMoverse() {
        var movido = new Movido();
        movido.id = "a-1";
        var escrito = new Document();

        conversor().write(movido, escrito);

        assertThat(escrito.getString("_class")).isEqualTo(NOMBRE_DE_ANTES);
    }

    @Test
    void unaClaseSinAnotarSigueEscribiendoSuNombreCompleto() {
        var sinAnotar = new SinAnotar();
        sinAnotar.id = "a-1";
        var escrito = new Document();

        conversor().write(sinAnotar, escrito);

        assertThat(escrito.getString("_class")).isEqualTo(SinAnotar.class.getName());
    }

    @Test
    void unDocumentoViejoSeLeeAunqueSuClaseYaNoExista() {
        var guardado = new Document("_id", "a-1").append("nombre", "Manga").append("_class", NOMBRE_DE_ANTES);

        Movido leido = conversor().read(Movido.class, guardado);

        assertThat(leido.nombre).isEqualTo("Manga");
    }

    @Test
    void escribirYLeerDeVueltaConservaLosDatos() {
        var movido = new Movido();
        movido.id = "a-1";
        movido.nombre = "Manga";
        var conversor = conversor();
        var escrito = new Document();

        conversor.write(movido, escrito);
        Movido leido = conversor.read(Movido.class, escrito);

        assertThat(leido.nombre).isEqualTo("Manga");
        assertThat(leido.id).isEqualTo("a-1");
    }
}
