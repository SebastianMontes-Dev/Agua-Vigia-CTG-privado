package com.aguavigia.ctg.infrastructure.persistence.mongo;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.convert.ConfigurableTypeInformationMapper;
import org.springframework.data.convert.SimpleTypeInformationMapper;
import org.springframework.data.mongodb.core.convert.DbRefResolver;
import org.springframework.data.mongodb.core.convert.DefaultMongoTypeMapper;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Caracteriza el campo {@code _class} que Spring Data escribe en cada documento (docs/reduccion/invariantes.md, «El campo _class»).
 * La reducción mueve los {@code @Document} de paquete: este test fija los dos hechos de los que depende cómo se hace sin cambiar la base.
 * <ol>
 *   <li>Un documento escrito por el código viejo, cuyo {@code _class} nombra una clase que ya no existe, se sigue leyendo como el tipo declarado.</li>
 *   <li>Un mapeador de tipos configurado escribe en los documentos nuevos el nombre de clase de siempre, aunque la clase viva en otro paquete.</li>
 * </ol>
 */
class ClaseGuardadaEnLosDocumentosTest {

    /** Lo que hoy llevan los documentos: el nombre completo de la clase de antes de moverla. */
    private static final String CLASE_DE_ANTES = "com.aguavigia.ctg.infrastructure.persistence.mongo.EjemploDocumento";

    static class EjemploDocumento {
        String id;
        String nombre;
    }

    private static MappingMongoConverter conversor(DefaultMongoTypeMapper mapeador) {
        DbRefResolver sinReferencias = NoOpDbRefResolver.INSTANCE;
        var contexto = new MongoMappingContext();
        contexto.setInitialEntitySet(java.util.Set.of(EjemploDocumento.class));
        contexto.afterPropertiesSet();
        var conversor = new MappingMongoConverter(sinReferencias, contexto);
        conversor.setTypeMapper(mapeador);
        conversor.afterPropertiesSet();
        return conversor;
    }

    @Test
    void unDocumentoViejoConUnaClaseQueYaNoExisteSeSigueLeyendoComoElTipoDeclarado() {
        var conversor = conversor(new DefaultMongoTypeMapper());
        var guardado = new Document("_id", "a-1").append("nombre", "Manga").append("_class", "com.aguavigia.ctg.paquete.que.ya.no.Existe");

        EjemploDocumento leido = conversor.read(EjemploDocumento.class, guardado);

        assertThat(leido.nombre).isEqualTo("Manga");
    }

    @Test
    void sinHacerNadaElValorDeClassEsElNombreActualDeLaClase() {
        var conversor = conversor(new DefaultMongoTypeMapper());
        var ejemplo = new EjemploDocumento();
        ejemplo.id = "a-1";
        ejemplo.nombre = "Manga";
        var escrito = new Document();

        conversor.write(ejemplo, escrito);

        assertThat(escrito.getString("_class")).isEqualTo(EjemploDocumento.class.getName());
    }

    @Test
    void unMapeadorConfiguradoEscribeElNombreDeClaseDeSiempreAunqueLaClaseSeHayaMovido() {
        var mapeador = new DefaultMongoTypeMapper(DefaultMongoTypeMapper.DEFAULT_TYPE_KEY,
                List.of(new ConfigurableTypeInformationMapper(Map.of(EjemploDocumento.class, CLASE_DE_ANTES)),
                        new SimpleTypeInformationMapper()));
        var conversor = conversor(mapeador);
        var ejemplo = new EjemploDocumento();
        ejemplo.id = "a-1";
        ejemplo.nombre = "Manga";
        var escrito = new Document();

        conversor.write(ejemplo, escrito);

        assertThat(escrito.getString("_class")).isEqualTo(CLASE_DE_ANTES);
        assertThat(conversor.read(EjemploDocumento.class, escrito).nombre).isEqualTo("Manga");
    }
}
