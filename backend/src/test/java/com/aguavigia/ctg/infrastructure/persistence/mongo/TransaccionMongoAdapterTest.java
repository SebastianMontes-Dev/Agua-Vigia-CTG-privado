package com.aguavigia.ctg.infrastructure.persistence.mongo;

import com.mongodb.MongoException;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pruebas puras (sin Mongo real) de la política de reintento: verifican cuántas veces se invoca
 * {@code accion} y si se hizo commit o rollback, con un {@link PlatformTransactionManager} de
 * mentira que solo cuenta llamadas. La atomicidad real contra Mongo se prueba en
 * {@link TransaccionMongoAdapterIntegrationTest}.
 */
class TransaccionMongoAdapterTest {

    @Test
    void debeConfirmarYDevolverElResultadoCuandoLaAccionNoLanza() {
        GestorDeTransaccionesFalso gestor = new GestorDeTransaccionesFalso();
        TransaccionMongoAdapter adaptador = new TransaccionMongoAdapter(gestor);

        String resultado = adaptador.ejecutar(() -> "listo");

        assertThat(resultado).isEqualTo("listo");
        assertThat(gestor.commits).isEqualTo(1);
        assertThat(gestor.rollbacks).isEqualTo(0);
        assertThat(gestor.intentos).isEqualTo(1);
    }

    @Test
    void debeRevertirYPropagarCuandoLaAccionLanzaUnaFallaNoTransitoria() {
        GestorDeTransaccionesFalso gestor = new GestorDeTransaccionesFalso();
        TransaccionMongoAdapter adaptador = new TransaccionMongoAdapter(gestor);

        assertThatThrownBy(() -> adaptador.ejecutar(() -> {
            throw new IllegalStateException("regla de negocio violada");
        })).isInstanceOf(IllegalStateException.class).hasMessage("regla de negocio violada");

        assertThat(gestor.commits).isEqualTo(0);
        assertThat(gestor.rollbacks).isEqualTo(1);
        assertThat(gestor.intentos).isEqualTo(1);
    }

    @Test
    void debeReintentarAntesFallasTransitoriasDeMongoYConfirmarAlLograrlo() {
        GestorDeTransaccionesFalso gestor = new GestorDeTransaccionesFalso();
        TransaccionMongoAdapter adaptador = new TransaccionMongoAdapter(gestor);
        AtomicInteger llamadas = new AtomicInteger();

        String resultado = adaptador.ejecutar(() -> {
            if (llamadas.getAndIncrement() < 2) {
                MongoException fallaTransitoria = new MongoException("conflicto de escritura");
                fallaTransitoria.addLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL);
                throw fallaTransitoria;
            }
            return "listo al tercer intento";
        });

        assertThat(resultado).isEqualTo("listo al tercer intento");
        assertThat(gestor.intentos).isEqualTo(3);
        assertThat(gestor.commits).isEqualTo(1);
        assertThat(gestor.rollbacks).isEqualTo(2);
    }

    @Test
    void noDebeReintentarUnaFallaDeMongoQueNoEstaMarcadaComoTransitoria() {
        GestorDeTransaccionesFalso gestor = new GestorDeTransaccionesFalso();
        TransaccionMongoAdapter adaptador = new TransaccionMongoAdapter(gestor);

        assertThatThrownBy(() -> adaptador.ejecutar(() -> {
            throw new MongoException("falla permanente, sin etiqueta de transitoria");
        })).isInstanceOf(MongoException.class);

        assertThat(gestor.intentos).isEqualTo(1);
        assertThat(gestor.rollbacks).isEqualTo(1);
    }

    @Test
    void debeAgotarLosReintentosYPropagarSiLaFallaTransitoriaPersiste() {
        GestorDeTransaccionesFalso gestor = new GestorDeTransaccionesFalso();
        TransaccionMongoAdapter adaptador = new TransaccionMongoAdapter(gestor);

        assertThatThrownBy(() -> adaptador.ejecutar(() -> {
            MongoException fallaTransitoria = new MongoException("siempre en conflicto");
            fallaTransitoria.addLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL);
            throw fallaTransitoria;
        })).isInstanceOf(MongoException.class);

        assertThat(gestor.intentos).isEqualTo(TransaccionMongoAdapter.MAX_INTENTOS);
        assertThat(gestor.commits).isEqualTo(0);
    }

    /** Cuenta cuántas transacciones se abrieron, confirmaron o revirtieron; nunca toca Mongo. */
    private static class GestorDeTransaccionesFalso implements PlatformTransactionManager {
        int intentos = 0;
        int commits = 0;
        int rollbacks = 0;

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) throws TransactionException {
            intentos++;
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) throws TransactionException {
            commits++;
        }

        @Override
        public void rollback(TransactionStatus status) throws TransactionException {
            rollbacks++;
        }
    }
}
