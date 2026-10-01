package com.aguavigia.ctg.domain.port.in;

public interface EmitirTokenDeDispositivoUseCase {

    /** Crea un dispositivo nuevo y devuelve su token firmado. Cada llamada es una identidad distinta. */
    String emitir();
}
