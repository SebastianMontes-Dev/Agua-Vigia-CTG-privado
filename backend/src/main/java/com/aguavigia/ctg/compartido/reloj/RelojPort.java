package com.aguavigia.ctg.compartido.reloj;

import java.time.Instant;

/** Instant.now() inyectable — sin esto las invariantes de VentanaTiempo no son testeables sin mockear el reloj real. */
public interface RelojPort {

    Instant ahora();
}
