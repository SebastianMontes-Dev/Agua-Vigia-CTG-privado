package com.aguavigia.ctg.domain.port.out;

/** Corre un ciclo de ingesta ya, sin esperar al siguiente intervalo. */
public interface CicloDeIngestaPort {

    void ejecutarAhora();
}
