package com.aguavigia.ctg.api.error;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.assertj.core.api.Assertions.assertThat;

class ManejadorGlobalDeErroresTest {

    private final ManejadorGlobalDeErrores manejador = new ManejadorGlobalDeErrores();
    private final Logger registro = (Logger) LoggerFactory.getLogger(ManejadorGlobalDeErrores.class);
    private final ListAppender<ILoggingEvent> eventos = new ListAppender<>();

    @BeforeEach
    void capturarRegistro() {
        eventos.start();
        registro.addAppender(eventos);
    }

    @AfterEach
    void soltarRegistro() {
        registro.detachAppender(eventos);
    }

    @Test
    void noDebeRegistrarComoErrorLaDesconexionDeUnClienteSse() {
        manejador.clienteDesconectado(
                new AsyncRequestNotUsableException("Servlet container error notification for disconnected client"));

        assertThat(eventos.list)
                .as("un cliente que cierra el canal en vivo es lo normal, no un fallo del servidor")
                .noneMatch(evento -> evento.getLevel().isGreaterOrEqual(Level.WARN));
    }

    @Test
    void unaFotoMasGrandeQueElMaximoRespondeAlVeedor413ConSuTipoPropio() {
        ProblemDetail problema = manejador.archivoDemasiadoGrande(new MaxUploadSizeExceededException(10L * 1024 * 1024));

        assertThat(problema.getStatus()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE.value());
        assertThat(problema.getType()).hasToString("https://aguavigia.example/errores/archivo-demasiado-grande");
    }

    @Test
    void debeSeguirRegistrandoComoErrorUnFalloInesperado() {
        ProblemDetail problema = manejador.errorInesperado(new RuntimeException("fallo de verdad"));

        assertThat(problema.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(eventos.list).anyMatch(evento -> evento.getLevel() == Level.ERROR);
    }
}
