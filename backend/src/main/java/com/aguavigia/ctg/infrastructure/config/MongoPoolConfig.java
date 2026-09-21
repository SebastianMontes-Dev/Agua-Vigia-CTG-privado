package com.aguavigia.ctg.infrastructure.config;

import org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Tiempos y tamaño del pool de MongoDB. Los valores por defecto del driver (2 minutos de espera por
 * una conexión, sin timeout de lectura) dejaban las peticiones encoladas mucho más allá de los 15 s
 * tras los que nginx ya cortaba: el cliente veía un 504 y el servidor seguía trabajando para nadie.
 * Va en código y no en la URI para que valga igual con cualquier `MONGODB_URI`.
 */
@Configuration
@EnableConfigurationProperties(MongoPoolConfig.Propiedades.class)
public class MongoPoolConfig {

    @ConfigurationProperties(prefix = "aguavigia.mongo")
    public record Propiedades(int maxPool, int esperaConexionMs, int timeoutLecturaMs, int seleccionServidorMs) {

        public Propiedades {
            maxPool = maxPool > 0 ? maxPool : 100;
            esperaConexionMs = esperaConexionMs > 0 ? esperaConexionMs : 2000;
            timeoutLecturaMs = timeoutLecturaMs > 0 ? timeoutLecturaMs : 5000;
            seleccionServidorMs = seleccionServidorMs > 0 ? seleccionServidorMs : 5000;
        }
    }

    @Bean
    public MongoClientSettingsBuilderCustomizer personalizadorDePool(Propiedades propiedades) {
        return builder -> builder
                .applyToConnectionPoolSettings(pool -> pool
                        .maxSize(propiedades.maxPool())
                        .maxWaitTime(propiedades.esperaConexionMs(), TimeUnit.MILLISECONDS))
                .applyToSocketSettings(socket -> socket
                        .readTimeout(propiedades.timeoutLecturaMs(), TimeUnit.MILLISECONDS))
                .applyToClusterSettings(cluster -> cluster
                        .serverSelectionTimeout(propiedades.seleccionServidorMs(), TimeUnit.MILLISECONDS));
    }
}
