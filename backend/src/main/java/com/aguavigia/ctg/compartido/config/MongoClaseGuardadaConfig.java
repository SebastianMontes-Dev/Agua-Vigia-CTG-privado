package com.aguavigia.ctg.compartido.config;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.convert.SimpleTypeInformationMapper;
import org.springframework.data.mongodb.core.convert.DefaultMongoTypeMapper;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;

import java.util.List;

/**
 * Instala {@link MapeadorDeClaseGuardada} en el conversor de Mongo, delante del mapeador por defecto. Lo hace un
 * {@link BeanPostProcessor} para no sustituir el conversor que ya configura Spring Boot.
 */
@Configuration
public class MongoClaseGuardadaConfig {

    public static DefaultMongoTypeMapper mapeador() {
        return new DefaultMongoTypeMapper(DefaultMongoTypeMapper.DEFAULT_TYPE_KEY,
                List.of(new MapeadorDeClaseGuardada(), new SimpleTypeInformationMapper()));
    }

    @Bean
    static BeanPostProcessor fijarLaClaseGuardada() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessBeforeInitialization(Object bean, String nombre) {
                if (bean instanceof MappingMongoConverter conversor) {
                    conversor.setTypeMapper(mapeador());
                }
                return bean;
            }
        };
    }
}
