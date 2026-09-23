package com.aguavigia.ctg.infrastructure.persistence.mongo;

import com.aguavigia.ctg.domain.port.out.TransaccionPort;
import com.mongodb.MongoException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/**
 * `TransactionTemplate` sobre el `MongoTransactionManager` inyectado (ver
 * `MongoTransaccionConfig`): `MongoTemplate` y los `MongoRepository` ya inyectados en los adaptadores
 * de este paquete se unen solos a la sesión activa, sin que ninguno necesite conocer esta clase.
 *
 * Reintenta hasta {@link #MAX_INTENTOS} veces cuando Mongo etiqueta la falla como
 * `TransientTransactionError` — un conflicto de escritura entre dos transacciones concurrentes sobre
 * el mismo documento, previsto y documentado por el propio driver. Cualquier otra excepción revierte
 * y se propaga en el primer intento: no es segura de reintentar sola (podría ser una regla de
 * dominio violada, no un problema pasajero de Mongo).
 */
@Component
public class TransaccionMongoAdapter implements TransaccionPort {

    static final int MAX_INTENTOS = 3;

    private final TransactionTemplate transactionTemplate;

    public TransaccionMongoAdapter(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public <T> T ejecutar(Supplier<T> accion) {
        RuntimeException ultimaFallaTransitoria = null;
        for (int intento = 1; intento <= MAX_INTENTOS; intento++) {
            try {
                return transactionTemplate.execute(status -> accion.get());
            } catch (MongoException falla) {
                if (!falla.hasErrorLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL)) {
                    throw falla;
                }
                ultimaFallaTransitoria = falla;
            }
        }
        throw ultimaFallaTransitoria;
    }
}
