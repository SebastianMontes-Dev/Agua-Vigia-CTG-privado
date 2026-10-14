package com.aguavigia.ctg.infrastructure.config;

import com.mongodb.MongoClientSettings;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class MongoPoolConfigTest {

    private MongoClientSettings aplicar(MongoPoolConfig.Propiedades propiedades) {
        MongoClientSettings.Builder builder = MongoClientSettings.builder();
        new MongoPoolConfig().personalizadorDePool(propiedades).customize(builder);
        return builder.build();
    }

    @Test
    void debeAcotarElPoolYLaEsperaParaFallarRapido() {
        MongoClientSettings ajustes = aplicar(new MongoPoolConfig.Propiedades(40, 1500, 4000, 3000));

        assertThat(ajustes.getConnectionPoolSettings().getMaxSize()).isEqualTo(40);
        assertThat(ajustes.getConnectionPoolSettings().getMaxWaitTime(TimeUnit.MILLISECONDS)).isEqualTo(1500);
    }

    @Test
    void debeFijarUnTimeoutDeLecturaYDeSeleccionDeServidor() {
        MongoClientSettings ajustes = aplicar(new MongoPoolConfig.Propiedades(100, 2000, 4000, 3000));

        assertThat(ajustes.getSocketSettings().getReadTimeout(TimeUnit.MILLISECONDS)).isEqualTo(4000);
        assertThat(ajustes.getClusterSettings().getServerSelectionTimeout(TimeUnit.MILLISECONDS)).isEqualTo(3000);
    }
}
