package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.Coordenada;
import com.aguavigia.ctg.domain.HuellaDispositivo;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.TipoReporte;
import com.aguavigia.ctg.domain.port.in.RegistrarLecturaDePresionUseCase;
import com.aguavigia.ctg.domain.port.in.RegistrarReporteUseCase;
import com.aguavigia.ctg.domain.port.out.SectorRepository;

/**
 * M13 — telemetría IoT pasiva. Un sensor que reporta presión baja es, para el dominio, un reporte
 * más, con su propia huella y con el cupo de sensor (RF006): por eso reusa
 * {@link RegistrarReporteUseCase}. El umbral que decide qué es «presión baja» vivía en el
 * controlador, que es lo que un controlador no debe decidir.
 */
public class RegistrarLecturaDePresionService implements RegistrarLecturaDePresionUseCase {

    private final SectorRepository sectores;
    private final RegistrarReporteUseCase registrarReporte;
    private final double umbralPresionBajaPsi;

    public RegistrarLecturaDePresionService(SectorRepository sectores,
                                             RegistrarReporteUseCase registrarReporte,
                                             double umbralPresionBajaPsi) {
        this.sectores = sectores;
        this.registrarReporte = registrarReporte;
        this.umbralPresionBajaPsi = umbralPresionBajaPsi;
    }

    @Override
    public void registrar(String sensorId, SectorId sectorId, Double presionPsi, Coordenada coordenada) {
        // Sin identificador de sensor no hay huella con la que aplicar el cupo: antes se construía
        // la huella literal "IoT-null" y todos los sensores mal configurados compartían cupo.
        if (sensorId == null || sensorId.isBlank()) {
            throw new IllegalArgumentException("El sensor debe declarar su identificador");
        }
        sectores.buscarPorId(sectorId).orElseThrow(
                () -> new IllegalArgumentException("No existe el sector '" + sectorId.valor() + "'"));

        if (presionPsi == null || presionPsi >= umbralPresionBajaPsi) {
            return;
        }
        registrarReporte.registrar(sectorId, TipoReporte.PRESION_BAJA, coordenada,
                HuellaDispositivo.deSensor(sensorId), true);
    }
}
