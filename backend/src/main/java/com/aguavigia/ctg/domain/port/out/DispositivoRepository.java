package com.aguavigia.ctg.domain.port.out;

import com.aguavigia.ctg.domain.Dispositivo;
import com.aguavigia.ctg.domain.DispositivoId;

import java.time.Instant;
import java.util.Optional;

public interface DispositivoRepository {

    Dispositivo guardar(Dispositivo dispositivo);

    /** Vacío si no existe: nunca se emitió, o venció tras 12 meses sin uso. */
    Optional<Dispositivo> buscarPorId(DispositivoId id);

    /** Marca el uso de hoy: alarga el plazo de retención, que cuenta desde el último uso. */
    void registrarVisto(DispositivoId id, Instant cuando);
}
