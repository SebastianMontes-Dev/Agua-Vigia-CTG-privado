package com.aguavigia.ctg.domain.port.in;

import com.aguavigia.ctg.domain.Coordenada;
import com.aguavigia.ctg.domain.SectorId;

/** M13 — una lectura de presión de un sensor de la red. Una presión baja vota «presión baja»; una normal, «servicio restablecido»; entre los dos umbrales no vota. */
public interface RegistrarLecturaDePresionUseCase {

    /**
     * @param presionPsi nula cuando el sensor no la mide en esta lectura: no vota
     * @throws IllegalArgumentException si falta el sensor o el sector no existe
     */
    void registrar(String sensorId, SectorId sectorId, Double presionPsi, Coordenada coordenada);
}
