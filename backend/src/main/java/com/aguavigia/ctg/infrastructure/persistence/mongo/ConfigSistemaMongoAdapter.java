package com.aguavigia.ctg.infrastructure.persistence.mongo;

import com.aguavigia.ctg.domain.port.out.SecretosDelSistemaPort;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Los secretos que genera el propio sistema viven en `config_sistema`, no en memoria ni en un
 * archivo: un reinicio, o una segunda instancia sobre la misma base, no deben invalidar los tokens ya
 * emitidos. Se crean con un upsert `$setOnInsert`, que es atómico: dos arranques simultáneos acaban
 * con el mismo secreto en vez de uno cada uno.
 *
 * Quien pueda leer esta colección puede firmar tokens de dispositivo, igual que quien lea `.env` puede
 * firmar sesiones del panel: la base ya es el activo que hay que proteger.
 */
@Component
public class ConfigSistemaMongoAdapter implements SecretosDelSistemaPort {

    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final MongoTemplate mongoTemplate;
    private final Map<String, String> enMemoria = new ConcurrentHashMap<>();

    public ConfigSistemaMongoAdapter(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public String obtenerOCrear(String nombre) {
        return enMemoria.computeIfAbsent(nombre, this::leerOCrear);
    }

    private String leerOCrear(String nombre) {
        Query porNombre = Query.query(Criteria.where("_id").is(nombre));
        Update alInsertar = new Update().setOnInsert("valor", nuevoSecreto());
        try {
            return mongoTemplate.findAndModify(porNombre, alInsertar,
                    FindAndModifyOptions.options().upsert(true).returnNew(true),
                    ConfigSistemaDocumento.class).getValor();
        } catch (DuplicateKeyException otraInstanciaLoCreoAntes) {
            return mongoTemplate.findOne(porNombre, ConfigSistemaDocumento.class).getValor();
        }
    }

    private static String nuevoSecreto() {
        byte[] bytes = new byte[32];
        ALEATORIO.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
