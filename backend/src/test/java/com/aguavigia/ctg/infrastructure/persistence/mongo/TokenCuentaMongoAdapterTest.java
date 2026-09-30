package com.aguavigia.ctg.infrastructure.persistence.mongo;

import com.aguavigia.ctg.domain.TipoTokenCuenta;
import com.aguavigia.ctg.domain.TokenCuenta;
import com.aguavigia.ctg.domain.UsuarioId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import com.aguavigia.ctg.domain.port.out.RelojPort;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** Prueba de integración contra un MongoDB real (Testcontainers). Requiere Docker. */
@Testcontainers
@DataMongoTest
@Import({TokenCuentaMongoAdapter.class, TokenCuentaMongoAdapterTest.RelojFijo.class})
class TokenCuentaMongoAdapterTest {

    private static final Instant T0 = Instant.parse("2026-09-01T12:00:00Z");
    private static final Instant AHORA = Instant.parse("2026-09-01T13:00:00Z");
    private static final UsuarioId ANA = new UsuarioId("u-ana");
    private static final UsuarioId BETO = new UsuarioId("u-beto");

    @TestConfiguration
    static class RelojFijo {
        @Bean
        RelojPort reloj() {
            return () -> AHORA;
        }
    }

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7.0");

    @Autowired
    private TokenCuentaMongoAdapter adaptador;
    @Autowired
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void limpiar() {
        mongoTemplate.getDb().getCollection("tokens_cuenta").drop();
    }

    @Test
    void debeGuardarYBuscarUnTokenPorSuHash() {
        TokenCuenta original = TokenCuenta.nuevo("hash-1", TipoTokenCuenta.VERIFICACION_CORREO, ANA, T0);

        adaptador.guardar(original);

        assertThat(adaptador.buscarPorHash("hash-1")).contains(original);
    }

    @Test
    void marcarUsadoSiVigenteDebeGanarUnaSolaVezAunqueSeIntenteDosVeces() {
        adaptador.guardar(TokenCuenta.nuevo("hash-carrera", TipoTokenCuenta.INVITACION, ANA, T0));

        assertThat(adaptador.marcarUsadoSiVigente("hash-carrera", AHORA)).isTrue();
        assertThat(adaptador.marcarUsadoSiVigente("hash-carrera", AHORA)).isFalse();
        assertThat(adaptador.buscarPorHash("hash-carrera").orElseThrow().usadoEn()).isEqualTo(AHORA);
    }

    @Test
    void marcarUsadoSiVigenteNoDebeGastarUnTokenVencidoNiUnoInexistente() {
        adaptador.guardar(TokenCuenta.nuevo("hash-viejo", TipoTokenCuenta.INVITACION, ANA, T0.minusSeconds(30L * 24 * 3600)));

        assertThat(adaptador.marcarUsadoSiVigente("hash-viejo", AHORA)).isFalse();
        assertThat(adaptador.marcarUsadoSiVigente("no-existe", AHORA)).isFalse();
    }

    @Test
    void unHashDesconocidoNoDebeEncontrarNada() {
        assertThat(adaptador.buscarPorHash("no-existe")).isEmpty();
    }

    @Test
    void debeConservarElInstanteDeUsoAlGuardarUnTokenYaUsado() {
        TokenCuenta usado = TokenCuenta.nuevo("hash-2", TipoTokenCuenta.INVITACION, ANA, T0).marcarUsado(T0.plusSeconds(30));

        adaptador.guardar(usado);

        assertThat(adaptador.buscarPorHash("hash-2").orElseThrow().usadoEn()).isEqualTo(T0.plusSeconds(30));
    }

    /** El vencimiento se guarda en `expiraEn` (el índice TTL de Mongo lo usa) y sale de la vigencia del tipo. */
    @Test
    void debeGuardarElVencimientoSegunLaVigenciaDelTipo() {
        adaptador.guardar(TokenCuenta.nuevo("hash-3", TipoTokenCuenta.RESTABLECER_CLAVE, ANA, T0));

        TokenCuentaDocumento documento = mongoTemplate.findById("hash-3", TokenCuentaDocumento.class);
        assertThat(documento.getExpiraEn()).isEqualTo(T0.plus(TipoTokenCuenta.RESTABLECER_CLAVE.vigencia()));
    }

    @Test
    void invalidarVigentesDebeMarcarComoUsadosSoloLosDeEsaCuentaYEseTipo() {
        adaptador.guardar(TokenCuenta.nuevo("ana-verif-1", TipoTokenCuenta.VERIFICACION_CORREO, ANA, T0));
        adaptador.guardar(TokenCuenta.nuevo("ana-verif-2", TipoTokenCuenta.VERIFICACION_CORREO, ANA, T0.plusSeconds(5)));
        adaptador.guardar(TokenCuenta.nuevo("ana-restablecer", TipoTokenCuenta.RESTABLECER_CLAVE, ANA, T0));
        adaptador.guardar(TokenCuenta.nuevo("beto-verif", TipoTokenCuenta.VERIFICACION_CORREO, BETO, T0));

        adaptador.invalidarVigentes(ANA, TipoTokenCuenta.VERIFICACION_CORREO);

        assertThat(adaptador.buscarPorHash("ana-verif-1").orElseThrow().usadoEn()).isEqualTo(AHORA);
        assertThat(adaptador.buscarPorHash("ana-verif-2").orElseThrow().usadoEn()).isEqualTo(AHORA);
        assertThat(adaptador.buscarPorHash("ana-restablecer").orElseThrow().usadoEn()).isNull();
        assertThat(adaptador.buscarPorHash("beto-verif").orElseThrow().usadoEn()).isNull();
    }

    /** Un enlace ya gastado conserva la fecha en que se usó: invalidar no debe reescribirla. */
    @Test
    void invalidarVigentesNoDebeTocarLosQueYaSeUsaron() {
        TokenCuenta usado = TokenCuenta.nuevo("ana-usado", TipoTokenCuenta.INVITACION, ANA, T0).marcarUsado(T0.plusSeconds(60));
        adaptador.guardar(usado);

        adaptador.invalidarVigentes(ANA, TipoTokenCuenta.INVITACION);

        assertThat(adaptador.buscarPorHash("ana-usado").orElseThrow().usadoEn()).isEqualTo(T0.plusSeconds(60));
    }

    @Test
    void invalidarSinTokensNoDebeFallar() {
        adaptador.invalidarVigentes(ANA, TipoTokenCuenta.RESTABLECER_CLAVE);
    }
}
