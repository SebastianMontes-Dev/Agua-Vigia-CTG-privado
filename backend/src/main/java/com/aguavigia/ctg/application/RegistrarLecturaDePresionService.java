package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.Coordenada;
import com.aguavigia.ctg.domain.HuellaDispositivo;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.TipoReporte;
import com.aguavigia.ctg.domain.port.in.RegistrarLecturaDePresionUseCase;
import com.aguavigia.ctg.domain.port.in.RegistrarReporteUseCase;
import com.aguavigia.ctg.domain.port.out.SectorRepository;

/**
 * M13 — telemetría IoT pasiva. Un sensor que reporta presión baja o normal es, para el dominio, un reporte
 * más, con su propia huella y con el cupo de sensor (RF006): por eso reusa
 * {@link RegistrarReporteUseCase}. El umbral que decide qué es «presión baja» vivía en el
 * controlador, que es lo que un controlador no debe decidir.
 */
public class RegistrarLecturaDePresionService implements RegistrarLecturaDePresionUseCase {

    private final SectorRepository sectores;
    private final RegistrarReporteUseCase registrarReporte;
    private final double umbralPresionBajaPsi;
    private final double umbralPresionNormalPsi;

    /**
     * @param umbralPresionBajaPsi   por debajo de esto el sensor vota «presión baja»
     * @param umbralPresionNormalPsi desde esto el sensor vota «servicio restablecido»; entre los dos umbrales no vota
     *                               (histéresis), para que un sensor que oscila en torno a un solo valor no haga
     *                               parpadear el barrio
     */
    public RegistrarLecturaDePresionService(SectorRepository sectores,
                                             RegistrarReporteUseCase registrarReporte,
                                             double umbralPresionBajaPsi,
                                             double umbralPresionNormalPsi) {
        if (umbralPresionNormalPsi < umbralPresionBajaPsi) {
            throw new IllegalArgumentException(
                    "El umbral de presión normal no puede ser menor que el de presión baja");
        }
        this.sectores = sectores;
        this.registrarReporte = registrarReporte;
        this.umbralPresionBajaPsi = umbralPresionBajaPsi;
        this.umbralPresionNormalPsi = umbralPresionNormalPsi;
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

        if (presionPsi == null) {
            return;
        }
        if (presionPsi < umbralPresionBajaPsi) {
            votar(sensorId, sectorId, TipoReporte.PRESION_BAJA, coordenada);
        } else if (presionPsi >= umbralPresionNormalPsi) {
            votar(sensorId, sectorId, TipoReporte.SERVICIO_RESTABLECIDO, coordenada);
        }
    }

    private void votar(String sensorId, SectorId sectorId, TipoReporte tipo, Coordenada coordenada) {
        registrarReporte.registrar(sectorId, tipo, coordenada, HuellaDispositivo.deSensor(sensorId), true);
    }
}
