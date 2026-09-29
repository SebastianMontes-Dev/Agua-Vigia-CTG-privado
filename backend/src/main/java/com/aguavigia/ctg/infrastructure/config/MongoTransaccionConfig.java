package com.aguavigia.ctg.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;

/**
 * Único `PlatformTransactionManager` del backend (no hay JPA): habilita transacciones
 * multi-documento sobre el *replica set* local de un nodo (`ADR-063`), prerrequisito de la Fase 3 de
 * `plan-validacion-backend.md`. Este bean solo funciona donde Mongo corre como *replica set*
 * (`docker-compose.yml` lo inicia con `mongo-init-replica`).
 */
@Configuration
public class MongoTransaccionConfig {

    @Bean
    public MongoTransactionManager mongoTransactionManager(MongoDatabaseFactory factoriaDeBaseDeDatos) {
        return new MongoTransactionManager(factoriaDeBaseDeDatos);
    }
}
