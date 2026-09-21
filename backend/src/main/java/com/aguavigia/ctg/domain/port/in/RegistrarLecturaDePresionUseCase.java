package com.aguavigia.ctg.domain.port.in;

import com.aguavigia.ctg.domain.Coordenada;
import com.aguavigia.ctg.domain.SectorId;

/** M13 — una lectura de presión de un sensor de la red. Solo una presión baja se convierte en reporte. */
public interface RegistrarLecturaDePresionUseCase {

    /**
     * @param presionPsi nula cuando el sensor no la mide en esta lectura: no genera reporte
     * @throws IllegalArgumentException si falta el sensor o el sector no existe
     */
    void registrar(String sensorId, SectorId sectorId, Double presionPsi, Coordenada coordenada);
}
