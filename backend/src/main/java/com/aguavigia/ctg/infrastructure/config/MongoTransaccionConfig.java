package com.aguavigia.ctg.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;

/**
 * Único `PlatformTransactionManager` del backend (no hay JPA): habilita transacciones
 * multi-documento sobre el *replica set* local de un nodo (`ADR-063`), prerrequisito de la Fase 3 de
 * `plan-validacion-backend.md`. `docker-compose.prod.yml` sigue sin *replica set* a propósito — este
 * bean solo funciona donde Mongo corre como tal.
 */
@Configuration
public class MongoTransaccionConfig {

    @Bean
    public MongoTransactionManager mongoTransactionManager(MongoDatabaseFactory factoriaDeBaseDeDatos) {
        return new MongoTransactionManager(factoriaDeBaseDeDatos);
    }
}
