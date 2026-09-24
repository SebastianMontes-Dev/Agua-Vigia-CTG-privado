package com.aguavigia.ctg.infrastructure.persistence.mongo;

import com.aguavigia.ctg.domain.ChatTelegramId;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.SuscripcionTelegram;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** Prueba de integración contra un MongoDB real (mismo patrón que SuscripcionMongoAdapterTest). */
@Testcontainers
@DataMongoTest
@Import(SuscripcionTelegramMongoAdapter.class)
class SuscripcionTelegramMongoAdapterTest {

    private static final Instant AHORA = Instant.parse("2026-09-24T15:00:00Z");
    private static final SectorId MANGA = new SectorId("manga");
    private static final SectorId BOCAGRANDE = new SectorId("bocagrande");

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7.0");

    @Autowired
    private SuscripcionTelegramMongoAdapter adaptador;

    @Autowired
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void limpiar() {
        mongoTemplate.getDb().getCollection("suscripciones_telegram").drop();
    }

    @Test
    void debeGuardarYRecuperarUnaSuscripcionPorChat() {
        ChatTelegramId chat = new ChatTelegramId(987654321L);
        adaptador.guardar(SuscripcionTelegram.nueva(chat, AHORA).siguiendo(MANGA).siguiendo(BOCAGRANDE));

        SuscripcionTelegram leida = adaptador.buscarPorChat(chat).orElseThrow();

        assertThat(leida.chat()).isEqualTo(chat);
        assertThat(leida.sectorIds()).containsExactly(MANGA, BOCAGRANDE);
        assertThat(leida.creadaEn()).isEqualTo(AHORA);
    }

    @Test
    void guardarDeNuevoElMismoChatActualizaSinDuplicar() {
        ChatTelegramId chat = new ChatTelegramId(5L);
        adaptador.guardar(SuscripcionTelegram.nueva(chat, AHORA).siguiendo(MANGA));
        adaptador.guardar(SuscripcionTelegram.nueva(chat, AHORA).siguiendo(BOCAGRANDE));

        assertThat(mongoTemplate.getDb().getCollection("suscripciones_telegram").countDocuments()).isEqualTo(1);
        assertThat(adaptador.buscarPorChat(chat).orElseThrow().sectorIds()).containsExactly(BOCAGRANDE);
    }

    @Test
    void debeEncontrarSoloLosChatsQueSiguenElSector() {
        adaptador.guardar(SuscripcionTelegram.nueva(new ChatTelegramId(1L), AHORA).siguiendo(MANGA));
        adaptador.guardar(SuscripcionTelegram.nueva(new ChatTelegramId(2L), AHORA).siguiendo(MANGA).siguiendo(BOCAGRANDE));
        adaptador.guardar(SuscripcionTelegram.nueva(new ChatTelegramId(3L), AHORA).siguiendo(BOCAGRANDE));

        assertThat(adaptador.buscarPorSector(MANGA)).extracting(s -> s.chat().valor()).containsExactlyInAnyOrder(1L, 2L);
        assertThat(adaptador.buscarPorSector(new SectorId("otro"))).isEmpty();
    }

    @Test
    void laBajaBorraElRegistroDelChat() {
        ChatTelegramId chat = new ChatTelegramId(9L);
        adaptador.guardar(SuscripcionTelegram.nueva(chat, AHORA).siguiendo(MANGA));

        adaptador.eliminar(chat);

        assertThat(adaptador.buscarPorChat(chat)).isEmpty();
        assertThat(mongoTemplate.getDb().getCollection("suscripciones_telegram").countDocuments()).isZero();
    }

    @Test
    void eliminarUnChatQueNoExisteNoFalla() {
        adaptador.eliminar(new ChatTelegramId(404L));

        assertThat(adaptador.buscarPorChat(new ChatTelegramId(404L))).isEmpty();
    }
}
