package com.aguavigia.ctg.infrastructure.telegram;

import com.aguavigia.ctg.domain.ChatTelegramId;
import com.aguavigia.ctg.domain.MensajeTelegram;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Prueba el adaptador contra un servidor HTTP falso que imita la API de bots de Telegram. NO prueba contra el
 * servicio real de Telegram: eso exige un token y solo se comprueba al conectar el bot (docs/ingenieria/telegram.md).
 */
class TelegramApiAdapterTest {

    private static final String TOKEN = "123456:token-de-prueba";

    private HttpServer servidor;
    private TelegramApiAdapter adaptador;
    private final List<String> rutas = new ArrayList<>();
    private final AtomicReference<String> cuerpoRecibido = new AtomicReference<>();
    private final AtomicInteger estado = new AtomicInteger(200);
    private final AtomicReference<String> respuesta = new AtomicReference<>("{\"ok\":true,\"result\":[]}");

    @BeforeEach
    void levantar() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", intercambio -> {
            rutas.add(intercambio.getRequestURI().toString());
            cuerpoRecibido.set(new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] cuerpo = respuesta.get().getBytes(StandardCharsets.UTF_8);
            intercambio.sendResponseHeaders(estado.get(), cuerpo.length);
            intercambio.getResponseBody().write(cuerpo);
            intercambio.close();
        });
        servidor.start();
        adaptador = new TelegramApiAdapter(TOKEN, "http://127.0.0.1:" + servidor.getAddress().getPort() + "/", new ObjectMapper());
    }

    @AfterEach
    void apagar() {
        servidor.stop(0);
    }

    @Test
    void enviarHaceUnPostASendMessageConElChatYElTexto() {
        boolean enviado = adaptador.enviar(new ChatTelegramId(42L), "Hola");

        assertThat(enviado).isTrue();
        assertThat(rutas).containsExactly("/bot" + TOKEN + "/sendMessage");
        assertThat(cuerpoRecibido.get()).contains("\"chat_id\":42").contains("\"text\":\"Hola\"");
    }

    @Test
    void enviarDevuelveFalsoCuandoElUsuarioBloqueoAlBot() {
        estado.set(403);
        respuesta.set("{\"ok\":false,\"error_code\":403,\"description\":\"Forbidden: bot was blocked by the user\"}");

        assertThat(adaptador.enviar(new ChatTelegramId(42L), "Hola")).isFalse();
    }

    @Test
    void enviarDevuelveFalsoCuandoElChatYaNoExiste() {
        estado.set(400);
        respuesta.set("{\"ok\":false,\"error_code\":400,\"description\":\"Bad Request: chat not found\"}");

        assertThat(adaptador.enviar(new ChatTelegramId(42L), "Hola")).isFalse();
    }

    @Test
    void enviarLanzaExcepcionSinElTokenCuandoTelegramFallaDeFormaPasajera() {
        estado.set(500);

        assertThatThrownBy(() -> adaptador.enviar(new ChatTelegramId(42L), "Hola"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("500")
                .hasMessageNotContaining(TOKEN);
    }

    @Test
    void enviarRecortaLosMensajesQueSuperanElMaximoDeTelegram() {
        adaptador.enviar(new ChatTelegramId(42L), "x".repeat(5000));

        assertThat(cuerpoRecibido.get()).contains("x".repeat(4000)).doesNotContain("x".repeat(4001));
    }

    @Test
    void recibirDevuelveSoloLosTextosDeChatsPrivadosYAvanzaElOffset() {
        respuesta.set("""
                {"ok":true,"result":[
                  {"update_id":10,"message":{"chat":{"id":111,"type":"private"},"text":"/suscribir manga"}},
                  {"update_id":11,"message":{"chat":{"id":-5,"type":"group"},"text":"/ayuda"}},
                  {"update_id":12,"message":{"chat":{"id":222,"type":"private"}}},
                  {"update_id":13,"edited_message":{"chat":{"id":333,"type":"private"},"text":"editado"}}
                ]}""");

        List<MensajeTelegram> mensajes = adaptador.recibirNuevos();
        adaptador.recibirNuevos();

        assertThat(mensajes).containsExactly(new MensajeTelegram(new ChatTelegramId(111L), "/suscribir manga"));
        assertThat(rutas.get(0)).contains("offset=0").contains("timeout=0");
        assertThat(rutas.get(1)).contains("offset=14");
    }

    @Test
    void recibirLanzaExcepcionSiTelegramRechazaElSondeo() {
        estado.set(409);

        assertThatThrownBy(() -> adaptador.recibirNuevos())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("409")
                .hasMessageNotContaining(TOKEN);
    }

    @Test
    void unaRespuestaIlegibleLanzaExcepcionSinPerderElOffset() {
        respuesta.set("no es json");

        assertThatThrownBy(() -> adaptador.recibirNuevos()).isInstanceOf(IllegalStateException.class);
    }
}
