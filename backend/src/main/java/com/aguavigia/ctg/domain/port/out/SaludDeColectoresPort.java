package com.aguavigia.ctg.domain.port.out;

import com.aguavigia.ctg.domain.SaludDeColector;

import java.util.List;

/** Solo lectura de la telemetría del pipeline de ingesta (RNF007). Vacía mientras no haya corrido un ciclo. */
public interface SaludDeColectoresPort {

    List<SaludDeColector> salud();
}
