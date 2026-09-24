package com.aguavigia.ctg.infrastructure.telegram;

import com.aguavigia.ctg.domain.ChatTelegramId;
import com.aguavigia.ctg.domain.MensajeTelegram;
import com.aguavigia.ctg.domain.port.out.EnvioTelegramPort;
import com.aguavigia.ctg.domain.port.out.RecepcionTelegramPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * RF041 — habla con la API de bots de Telegram. Solo existe si hay {@code TELEGRAM_BOT_TOKEN}; sin él
 * entra {@link TelegramDesactivadoAdapter}.
 *
 * Recibe por sondeo ({@code getUpdates}) y no por webhook: un webhook exige una URL pública con HTTPS y
 * este proyecto corre en local, sin dominio (ADR-057, ADR-066). Por eso solo debe haber **una** instancia
 * sondeando: dos consumidores del mismo bot reciben el error 409 de Telegram.
 *
 * El token viaja en la URL de la API. Nunca se incluye en un mensaje de error ni en un log.
 */
@Component
@ConditionalOnExpression("!'${aguavigia.telegram.token:}'.isBlank()")
public class TelegramApiAdapter implements EnvioTelegramPort, RecepcionTelegramPort {

    /** Telegram rechaza mensajes de más de 4096 caracteres. */
    private static final int MAXIMO_CARACTERES = 4000;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper json;
    private final String raiz;

    /**
     * Desde qué {@code update_id} pedir. Vive en memoria: tras un reinicio Telegram vuelve a entregar lo que
     * no se confirmó (hasta 24 h), y los comandos son idempotentes, así que repetirlos no hace daño.
     */
    private final AtomicLong offset = new AtomicLong(0);

    public TelegramApiAdapter(@Value("${aguavigia.telegram.token}") String token,
                              @Value("${aguavigia.telegram.base-url:https://api.telegram.org}") String baseUrl,
                              ObjectMapper json) {
        this.json = json;
        this.raiz = baseUrl.replaceAll("/+$", "") + "/bot" + token;
    }

    @Override
    public boolean enviar(ChatTelegramId chat, String texto) {
        String cuerpo;
        try {
            cuerpo = json.writeValueAsString(java.util.Map.of(
                    "chat_id", chat.valor(),
                    "text", texto.length() > MAXIMO_CARACTERES ? texto.substring(0, MAXIMO_CARACTERES) : texto));
        } catch (IOException imposible) {
            throw new IllegalStateException("No se pudo armar el mensaje de Telegram", imposible);
        }
        HttpResponse<String> respuesta = llamar(HttpRequest.newBuilder(URI.create(raiz + "/sendMessage"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo)));
        int estado = respuesta.statusCode();
        if (estado == 200) {
            return true;
        }
        if (estado == 403 || (estado == 400 && respuesta.body().contains("chat not found"))) {
            return false;
        }
        throw new IllegalStateException("Telegram respondió " + estado + " al enviar un mensaje");
    }

    @Override
    public List<MensajeTelegram> recibirNuevos() {
        HttpResponse<String> respuesta = llamar(HttpRequest.newBuilder(URI.create(
                        raiz + "/getUpdates?timeout=0&offset=" + offset.get() + "&allowed_updates=%5B%22message%22%5D"))
                .timeout(Duration.ofSeconds(10))
                .GET());
        if (respuesta.statusCode() != 200) {
            throw new IllegalStateException("Telegram respondió " + respuesta.statusCode() + " al pedir mensajes");
        }
        JsonNode actualizaciones;
        try {
            actualizaciones = json.readTree(respuesta.body()).path("result");
        } catch (IOException ilegible) {
            throw new IllegalStateException("Telegram devolvió una respuesta ilegible", ilegible);
        }
        List<MensajeTelegram> mensajes = new ArrayList<>();
        long siguiente = offset.get();
        for (JsonNode actualizacion : actualizaciones) {
            siguiente = Math.max(siguiente, actualizacion.path("update_id").asLong() + 1);
            JsonNode mensaje = actualizacion.path("message");
            JsonNode chat = mensaje.path("chat");
            if ("private".equals(chat.path("type").asText()) && mensaje.hasNonNull("text") && chat.path("id").asLong() != 0) {
                mensajes.add(new MensajeTelegram(new ChatTelegramId(chat.path("id").asLong()), mensaje.path("text").asText()));
            }
        }
        offset.set(siguiente);
        return mensajes;
    }

    private HttpResponse<String> llamar(HttpRequest.Builder peticion) {
        try {
            return http.send(peticion.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException fallo) {
            throw new IllegalStateException("No se pudo hablar con Telegram", fallo);
        } catch (InterruptedException interrumpido) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Se interrumpió la llamada a Telegram", interrumpido);
        }
    }
}
