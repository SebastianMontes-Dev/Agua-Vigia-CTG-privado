package com.aguavigia.ctg.domain.port.out;

import com.aguavigia.ctg.domain.SectorId;

import java.util.List;

/**
 * Acota a una por intervalo las evaluaciones de consenso de un mismo sector. En una avería masiva llegan
 * cientos de reportes por segundo al mismo sector y evaluar el consenso en cada uno hace trabajo idéntico
 * (el resultado casi nunca cambia). Quien no obtiene la reserva deja el sector pendiente y un barrido lo
 * evalúa enseguida, así que ningún reporte se queda sin evaluar: solo se agrupan.
 */
public interface ReservaDeEvaluacionPort {

    /** {@code true} si quien llama debe evaluar ahora; si no, otra petición evaluó hace menos de un intervalo. */
    boolean reservar(SectorId sectorId);

    void dejarPendiente(SectorId sectorId);

    /** Entrega y vacía los sectores pendientes: cada uno se entrega una sola vez, aunque haya varias réplicas. */
    List<SectorId> tomarPendientes();
}
