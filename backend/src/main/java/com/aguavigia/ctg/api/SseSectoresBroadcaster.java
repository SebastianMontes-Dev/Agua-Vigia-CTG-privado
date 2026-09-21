package com.aguavigia.ctg.api;

import com.aguavigia.ctg.domain.LimiteDePeticionesExcedidoException;
import com.aguavigia.ctg.domain.port.out.RelojPort;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Canal en vivo de los sectores. No empuja el estado: avisa de que cambió y el cliente pide
 * `GET /api/sectores`, que sí se cachea (HTTP y Redis). Empujar el listado (~25 KB) a cada cliente
 * en cada cambio costaba ~1,25 GB por evento con 50 000 conexiones abiertas.
 *
 * Backplane (estado-del-backend.md #6.1): la actualización se publica en el canal Redis
 * {@link #CANAL} y cada instancia suscrita avisa a sus propios clientes. Nadie depende de
 * deserializar el estado de otra instancia.
 *
 * Qué evita cada pieza a esa escala:
 * - Conjunto concurrente, no CopyOnWriteArrayList: cada alta o baja copiaba el arreglo entero, y
 *   una reconexión masiva era cuadrática.
 * - Coalescencia: {@link #onMessage} solo marca «hay algo que difundir» y el barrido periódico envía
 *   un aviso por cliente por intervalo, por muchos cambios que hayan ocurrido (una avería masiva).
 * - Difusión en hilos virtuales: un cliente lento bloquea su propia escritura, no la de los demás.
 * - Tope de conexiones: por encima de él se responde 429 con `Retry-After` en vez de agotar memoria.
 * - Caducidad con jitter y `retry` aleatorio: sin él toda la flota se reconectaba a la vez cada 10
 *   minutos, y tras un reinicio, en el mismo instante.
 * - Latido: mantiene abiertas las conexiones a través de proxies con timeout de inactividad.
 */
@Component
public class SseSectoresBroadcaster implements MessageListener {

    static final String CANAL = "aguavigia:sse:sectores";

    private static final long CADUCIDAD_BASE_MS = 600_000L;
    private static final long CADUCIDAD_JITTER_MS = 120_000L;
    private static final long REINTENTO_BASE_MS = 3_000L;
    private static final long REINTENTO_JITTER_MS = 7_000L;
    private static final long SEGUNDOS_PARA_REINTENTAR_AL_LLENARSE = 30L;

    private final RelojPort reloj;
    private final RedisTemplate<String, String> redisTemplate;
    private final Executor difusion;
    private final int maxConexiones;
    private final Set<SseEmitter> emisores = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean pendiente = new AtomicBoolean(false);
    private final AtomicReference<Instant> ultimoCambio = new AtomicReference<>();

    @Autowired
    public SseSectoresBroadcaster(RelojPort reloj, RedisTemplate<String, String> redisTemplate,
                                   @Value("${aguavigia.sse.max-conexiones:20000}") int maxConexiones) {
        this(reloj, redisTemplate, Executors.newVirtualThreadPerTaskExecutor(), maxConexiones);
    }

    SseSectoresBroadcaster(RelojPort reloj, RedisTemplate<String, String> redisTemplate, Executor difusion,
                           int maxConexiones) {
        this.reloj = reloj;
        this.redisTemplate = redisTemplate;
        this.difusion = difusion;
        this.maxConexiones = maxConexiones;
    }

    public SseEmitter registrar() {
        if (emisores.size() >= maxConexiones) {
            throw new LimiteDePeticionesExcedidoException(
                    "Demasiadas conexiones en vivo abiertas en este momento. Consulta GET /api/sectores o reintenta en unos segundos.",
                    SEGUNDOS_PARA_REINTENTAR_AL_LLENARSE);
        }
        SseEmitter emitter = new SseEmitter(CADUCIDAD_BASE_MS + ThreadLocalRandom.current().nextLong(CADUCIDAD_JITTER_MS));
        emisores.add(emitter);

        emitter.onCompletion(() -> emisores.remove(emitter));
        emitter.onTimeout(() -> emisores.remove(emitter));
        emitter.onError(e -> emisores.remove(emitter));

        try {
            emitter.send(SseEmitter.event()
                    .reconnectTime(REINTENTO_BASE_MS + ThreadLocalRandom.current().nextLong(REINTENTO_JITTER_MS))
                    .name("sectores")
                    .data(aviso(ultimoCambio.get() != null ? ultimoCambio.get() : reloj.ahora())));
        } catch (IOException | IllegalStateException e) {
            emisores.remove(emitter);
        }
        return emitter;
    }

    public int conexionesActivas() {
        return emisores.size();
    }

    /**
     * Publica en Redis en vez de avisar directo a `emisores`: así se enteran todas las instancias
     * suscritas, no solo la que procesó el cambio.
     */
    public void notificarActualizacion() {
        redisTemplate.convertAndSend(CANAL, reloj.ahora().toString());
    }

    /** Solo marca que hay un cambio por difundir: el envío lo hace {@link #difundirPendiente()}. */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        ultimoCambio.set(reloj.ahora());
        pendiente.set(true);
    }

    @Scheduled(fixedDelayString = "${aguavigia.sse.intervalo-difusion-ms:1000}")
    public void difundirPendiente() {
        if (!pendiente.compareAndSet(true, false)) {
            return;
        }
        String json = aviso(ultimoCambio.get());
        for (SseEmitter emitter : emisores) {
            enviar(emitter, SseEmitter.event().name("sectores").data(json));
        }
    }

    @Scheduled(fixedDelayString = "${aguavigia.sse.intervalo-latido-ms:25000}")
    public void enviarLatido() {
        for (SseEmitter emitter : emisores) {
            enviar(emitter, SseEmitter.event().comment("latido"));
        }
    }

    private void enviar(SseEmitter emitter, SseEmitter.SseEventBuilder evento) {
        difusion.execute(() -> {
            try {
                emitter.send(evento);
            } catch (IOException | IllegalStateException clienteCaido) {
                emisores.remove(emitter);
            }
        });
    }

    private static String aviso(Instant cuando) {
        return "{\"actualizadoEn\":\"" + cuando + "\"}";
    }

    @PreDestroy
    void cerrar() {
        if (difusion instanceof ExecutorService servicio) {
            servicio.shutdown();
        }
    }
}
