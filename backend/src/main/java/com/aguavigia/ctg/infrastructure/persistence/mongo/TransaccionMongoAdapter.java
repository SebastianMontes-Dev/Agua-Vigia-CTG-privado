package com.aguavigia.ctg.infrastructure.persistence.mongo;

import com.aguavigia.ctg.domain.port.out.TransaccionPort;
import com.mongodb.MongoException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/**
 * `TransactionTemplate` sobre el `MongoTransactionManager` inyectado (ver
 * `MongoTransaccionConfig`): `MongoTemplate` y los `MongoRepository` ya inyectados en los adaptadores
 * de este paquete se unen solos a la sesión activa, sin que ninguno necesite conocer esta clase.
 *
 * Reintenta hasta {@link #MAX_INTENTOS} veces cuando Mongo etiqueta la falla (o alguna de sus causas, porque Spring
 * la traduce) como `TransientTransactionError` — un conflicto de escritura entre dos transacciones concurrentes sobre
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
        // Ya dentro de una transacción (un caso de uso que llama a otro): se une a ella y no reintenta. Si Mongo marca
        // la falla como transitoria esa transacción ya está abortada, y repetir aquí gastaría los intentos sobre una
        // transacción muerta; quien reintenta es la exterior, desde el principio.
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return accion.get();
        }
        RuntimeException ultimaFallaTransitoria = null;
        for (int intento = 1; intento <= MAX_INTENTOS; intento++) {
            try {
                return transactionTemplate.execute(status -> accion.get());
            } catch (RuntimeException falla) {
                if (!esFallaTransitoria(falla)) {
                    throw falla;
                }
                ultimaFallaTransitoria = falla;
            }
        }
        throw ultimaFallaTransitoria;
    }

    /**
     * `MongoTemplate` y los repositorios traducen la `MongoException` a una excepción de Spring antes de que llegue
     * aquí; la etiqueta queda en alguna causa de la cadena, no en la excepción de arriba.
     */
    private static boolean esFallaTransitoria(Throwable falla) {
        for (Throwable actual = falla; actual != null; actual = actual.getCause()) {
            if (actual instanceof MongoException mongo
                    && mongo.hasErrorLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL)) {
                return true;
            }
            if (actual.getCause() == actual) {
                break;
            }
        }
        return false;
    }
}
