package com.aguavigia.ctg.infrastructure.persistence.mongo;

import com.aguavigia.ctg.domain.Dispositivo;
import com.aguavigia.ctg.domain.DispositivoId;
import com.aguavigia.ctg.domain.port.out.DispositivoRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

/**
 * El `_id` del documento es el SHA-256 del id del dispositivo, no el id: quien copie la colección
 * no se lleva ninguna identidad que pueda presentar. (Sin el secreto de `config_sistema` tampoco
 * podría firmar una, pero no hay razón para guardar en claro algo que se puede guardar resumido.)
 */
@Component
public class DispositivoMongoAdapter implements DispositivoRepository {

    private final MongoTemplate mongoTemplate;

    public DispositivoMongoAdapter(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Dispositivo guardar(Dispositivo dispositivo) {
        DispositivoDocumento documento = new DispositivoDocumento();
        documento.setId(huella(dispositivo.id().valor()));
        documento.setCreadoEn(dispositivo.creadoEn());
        documento.setUltimoVisto(dispositivo.ultimoVisto());
        mongoTemplate.save(documento);
        return dispositivo;
    }

    @Override
    public Optional<Dispositivo> buscarPorId(DispositivoId id) {
        DispositivoDocumento documento = mongoTemplate.findById(huella(id.valor()), DispositivoDocumento.class);
        return documento == null
                ? Optional.empty()
                : Optional.of(new Dispositivo(id, documento.getCreadoEn(), documento.getUltimoVisto()));
    }

    /**
     * `updateFirst` y `$max`: no crea el documento si no existe (un dispositivo vencido no se resucita con un uso) y
     * nunca retrocede la fecha si dos peticiones llegan desordenadas.
     */
    @Override
    public void registrarVisto(DispositivoId id, Instant cuando) {
        mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(huella(id.valor()))),
                new Update().max("ultimoVisto", cuando), DispositivoDocumento.class);
    }

    static String huella(String valor) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha256.digest(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException imposibleEnCualquierJvm) {
            throw new IllegalStateException("SHA-256 no disponible", imposibleEnCualquierJvm);
        }
    }
}
