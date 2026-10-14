package com.aguavigia.ctg.domain;

/**
 * RateLimitingInterceptor (rate limiting HTTP genérico, `ADR-018`) superó su límite. Antes esta
 * regla escribía su 429 a mano, sin pasar por `ManejadorGlobalDeErrores` — era el único punto de
 * la API que no respondía en RFC 7807 (CLAUDE.md § Arquitectura). `segundosParaReintentar` viaja
 * en la excepción para que el manejador siga pudiendo fijar la cabecera `Retry-After`.
 */
public class LimiteDePeticionesExcedidoException extends RuntimeException {

    private final long segundosParaReintentar;

    public LimiteDePeticionesExcedidoException(String mensaje, long segundosParaReintentar) {
        super(mensaje);
        this.segundosParaReintentar = segundosParaReintentar;
    }

    public long segundosParaReintentar() {
        return segundosParaReintentar;
    }
}
