package com.aguavigia.ctg.infrastructure.persistence.mongo;

import com.aguavigia.ctg.domain.port.out.BloqueoDeAdministradoresPort;
import com.aguavigia.ctg.domain.port.out.RelojPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Bloqueo de exclusión mutua sobre un único documento de Mongo (`findAndModify` atómico), mismo
 * patrón que ya usa {@code SectorMongoAdapter.cambiarEstadoSiEs}. Se eligió Mongo y no el bloqueo
 * distribuido en Redis que ya usa el proyecto ({@code EjecucionUnicaRedis}) porque ese, con Redis
 * caído, **omite la tarea** — correcto para un job de fondo que se reintenta en el próximo ciclo,
 * pero no para una petición HTTP de un administrador, que debe fallar con un error claro y
 * reintentable, no desaparecer en silencio. Con este bloqueo, si Mongo no responde, la petición ya
 * iba a fallar de todas formas (es donde vive `usuarios`), así que no hace falta ningún manejo
 * especial de caída (`ADR-062`).
 */
@Component
public class BloqueoDeAdministradoresMongoAdapter implements BloqueoDeAdministradoresPort {

    private static final Logger log = LoggerFactory.getLogger(BloqueoDeAdministradoresMongoAdapter.class);
    private static final String ID = "administradores";

    /**
     * Una acción de administración es una escritura, no un lote: sobra margen sin dejar el bloqueo
     * vigente mucho tiempo si el proceso muere a mitad de camino.
     */
    private static final Duration DURACION_MAXIMA = Duration.ofSeconds(10);

    private final MongoTemplate mongoTemplate;
    private final RelojPort reloj;

    public BloqueoDeAdministradoresMongoAdapter(MongoTemplate mongoTemplate, RelojPort reloj) {
        this.mongoTemplate = mongoTemplate;
        this.reloj = reloj;
    }

    @Override
    public <T> T ejecutarExclusivo(Supplier<T> accion) {
        String token = adquirir().orElseThrow(() -> new IllegalStateException(
                "Otro cambio de administradores está en curso. Intenta de nuevo en unos segundos."));
        try {
            return accion.get();
        } finally {
            liberar(token);
        }
    }

    /**
     * `findAndModify` con el filtro exigiendo `expiraEn` ausente o vencido: solo así se puede
     * escribir un `expiraEn` futuro con nuestro `token`. Con `upsert(true)` para el primer uso
     * (documento inexistente); si el documento existe pero sigue vigente, el filtro no lo encuentra
     * y el upsert intenta insertar uno nuevo con el mismo `_id` — Mongo lo rechaza con clave
     * duplicada, que es como esta operación dice "ya está tomado" sin un tercer resultado posible
     * en `findAndModify`.
     */
    Optional<String> adquirir() {
        Instant ahora = reloj.ahora();
        String token = UUID.randomUUID().toString();

        Query condicion = Query.query(new Criteria().andOperator(
                Criteria.where("_id").is(ID),
                new Criteria().orOperator(
                        Criteria.where("expiraEn").exists(false),
                        Criteria.where("expiraEn").lt(ahora))));
        Update cambio = new Update().set("expiraEn", ahora.plus(DURACION_MAXIMA)).set("token", token);

        try {
            BloqueoDocumento adquirido = mongoTemplate.findAndModify(condicion, cambio,
                    FindAndModifyOptions.options().upsert(true).returnNew(true), BloqueoDocumento.class);
            return adquirido != null ? Optional.of(token) : Optional.empty();
        } catch (DuplicateKeyException yaTomadoPorOtroCambio) {
            return Optional.empty();
        }
    }

    /**
     * Fuerza `expiraEn` al pasado en vez de borrar el documento: la siguiente {@link #adquirir()}
     * lo encuentra igual (vencido) y no vuelve a chocar con el `upsert` de una primera adquisición.
     * Solo suelta si `token` sigue siendo el vigente — si ya expiró y otro cambio lo tomó, soltar el
     * de ese otro sería el mismo bug que se está corrigiendo.
     */
    void liberar(String token) {
        Query condicion = Query.query(Criteria.where("_id").is(ID).and("token").is(token));
        Update cambio = new Update().set("expiraEn", reloj.ahora().minusSeconds(1));

        BloqueoDocumento liberado = mongoTemplate.findAndModify(condicion, cambio,
                FindAndModifyOptions.options().returnNew(true), BloqueoDocumento.class);
        if (liberado == null) {
            log.warn("No se pudo soltar el bloqueo de administradores (token '{}'): ya no era el "
                    + "vigente, probablemente por haber expirado y sido tomado por otro cambio", token);
        }
    }
}
