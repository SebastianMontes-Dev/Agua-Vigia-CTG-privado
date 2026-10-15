package com.aguavigia.ctg.infrastructure.persistence.sql;

import com.aguavigia.ctg.application.CopiarAnaliticaService;
import com.aguavigia.ctg.domain.port.in.CopiarAnaliticaUseCase;
import com.aguavigia.ctg.domain.port.out.AlmacenAnaliticoPort;
import com.aguavigia.ctg.domain.port.out.ConsultaAnaliticaPort;
import com.aguavigia.ctg.domain.port.out.CorteAguaRepository;
import com.aguavigia.ctg.domain.port.out.RelojPort;
import com.aguavigia.ctg.domain.port.out.SectorRepository;
import com.aguavigia.ctg.infrastructure.scheduling.EjecucionUnica;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * La copia analítica en PostgreSQL (ADR-100). Todo lo de SQL se crea aquí y solo si `aguavigia.sql.habilitado=true`: con el valor por
 * defecto (`false`) no existe ningún bean de JDBC ni de Flyway y el backend es exactamente el de antes.
 *
 * Que PostgreSQL no pueda tumbar el backend depende de tres cosas que viven en este paquete y en `application.yml`:
 * <ul>
 *   <li>el pool se crea con {@code initializationFailTimeout = -1}: arrancar sin PostgreSQL no falla, la conexión se intenta al usarla;</li>
 *   <li>no se registra ningún {@code PlatformTransactionManager}: el único del contexto sigue siendo el de Mongo, que
 *       {@code TransaccionMongoAdapter} recibe por tipo; el adaptador de escritura lleva el suyo, privado;</li>
 *   <li>Flyway no migra al arrancar sino en la primera copia que consigue conexión.</li>
 * </ul>
 */
@Configuration
public class SqlConfig {

    private static final String HABILITADO = "aguavigia.sql.habilitado";

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = HABILITADO, havingValue = "true")
    public DataSource analiticaDataSource(@Value("${aguavigia.sql.url}") String url,
                                          @Value("${aguavigia.sql.usuario}") String usuario,
                                          @Value("${aguavigia.sql.clave:}") String clave) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("analitica");
        config.setJdbcUrl(url);
        config.setUsername(usuario);
        config.setPassword(clave.isEmpty() ? null : clave);
        // -1: no conectar al crear el pool. Con PostgreSQL apagado el backend arranca igual y la copia se reintenta en cada ciclo.
        config.setInitializationFailTimeout(-1);
        // Sin conexiones en reposo ni espera larga: con la base caída cada intento falla rápido y no llena el log de reintentos.
        config.setMinimumIdle(0);
        config.setMaximumPoolSize(4);
        config.setConnectionTimeout(3_000);
        return new HikariDataSource(config);
    }

    @Bean
    @ConditionalOnProperty(name = HABILITADO, havingValue = "true")
    public AlmacenAnaliticoPort almacenAnalitico(DataSource analiticaDataSource) {
        return new AlmacenAnaliticoPostgresAdapter(analiticaDataSource);
    }

    @Bean
    @ConditionalOnProperty(name = HABILITADO, havingValue = "true")
    public ConsultaAnaliticaPort consultaAnaliticaPostgres(DataSource analiticaDataSource) {
        return new ConsultaAnaliticaPostgresAdapter(analiticaDataSource);
    }

    /** Con la copia apagada la API sigue teniendo puerto que consultar, y responde 503 en vez de no arrancar. */
    @Bean
    @ConditionalOnProperty(name = HABILITADO, havingValue = "false", matchIfMissing = true)
    public ConsultaAnaliticaPort consultaAnaliticaApagada() {
        return new ConsultaAnaliticaApagadaAdapter();
    }

    @Bean
    @ConditionalOnProperty(name = HABILITADO, havingValue = "true")
    public CopiarAnaliticaUseCase copiarAnaliticaService(SectorRepository sectores, CorteAguaRepository cortes,
                                                         AlmacenAnaliticoPort almacen, RelojPort reloj) {
        return new CopiarAnaliticaService(sectores, cortes, almacen, reloj);
    }

    @Bean
    @ConditionalOnProperty(name = HABILITADO, havingValue = "true")
    public CopiaAnaliticaJob copiaAnaliticaJob(CopiarAnaliticaUseCase copiar, EjecucionUnica ejecucionUnica,
                                               @Value("${aguavigia.sql.copia-intervalo-ms:300000}") long intervaloMs) {
        return new CopiaAnaliticaJob(copiar, ejecucionUnica, intervaloMs);
    }
}
