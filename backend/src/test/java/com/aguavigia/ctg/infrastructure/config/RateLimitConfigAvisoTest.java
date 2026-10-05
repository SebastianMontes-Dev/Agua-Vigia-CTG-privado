package com.aguavigia.ctg.infrastructure.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.aguavigia.ctg.infrastructure.ratelimit.RateLimitProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Una instancia con los topes multiplicados no es la real: que arranque diciéndolo en el log evita que una variable de carga
 * olvidada en el `.env` afloje la instancia real sin que nadie lo note.
 */
class RateLimitConfigAvisoTest {

    private static final List<RateLimitProperties.Regla> REGLAS = List.of(new RateLimitProperties.Regla("/api/dispositivos", 10, 3600));

    private Logger logger;
    private ListAppender<ILoggingEvent> captura;

    @BeforeEach
    void capturar() {
        logger = (Logger) LoggerFactory.getLogger(RateLimitConfig.class);
        captura = new ListAppender<>();
        captura.start();
        logger.addAppender(captura);
    }

    @AfterEach
    void soltar() {
        logger.detachAppender(captura);
    }

    @SuppressWarnings("unchecked")
    private void arrancarCon(RateLimitProperties propiedades) {
        new RateLimitConfig(propiedades, mock(RedisTemplate.class));
    }

    @Test
    void conLosTopesSinMultiplicarNoAvisaDeNada() {
        arrancarCon(new RateLimitProperties(REGLAS, 1, 1));

        assertThat(captura.list).noneMatch(e -> e.getLevel() == Level.WARN);
    }

    @Test
    void conUnFactorMayorQueUnoAvisaConsuValorYDeQueNoEsLaInstanciaReal() {
        arrancarCon(new RateLimitProperties(REGLAS, 100, 1));

        assertThat(captura.list).anySatisfy(e -> {
            assertThat(e.getLevel()).isEqualTo(Level.WARN);
            assertThat(e.getFormattedMessage()).contains("100").contains("no es la instancia real");
        });
    }

    @Test
    void conUnFactorDeCuentasMayorQueUnoAvisaQueLasClavesYLosCorreosEstanAflojados() {
        arrancarCon(new RateLimitProperties(REGLAS, 1, 50));

        assertThat(captura.list).anySatisfy(e -> {
            assertThat(e.getLevel()).isEqualTo(Level.WARN);
            assertThat(e.getFormattedMessage()).contains("50").containsIgnoringCase("claves");
        });
    }
}
