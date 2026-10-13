package com.aguavigia.ctg.compartido.error;

/** RF006 — un dispositivo superó el límite de reportes permitidos para un sector en la ventana vigente. */
public class LimiteReportesExcedidoException extends RuntimeException {

    public LimiteReportesExcedidoException(String mensaje) {
        super(mensaje);
    }
}
