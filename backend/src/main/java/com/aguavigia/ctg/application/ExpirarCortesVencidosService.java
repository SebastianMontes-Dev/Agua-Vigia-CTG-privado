package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.CorteAgua;
import com.aguavigia.ctg.domain.CorteId;
import com.aguavigia.ctg.domain.EventoBitacoraFactory;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.port.in.ExpirarCortesVencidosUseCase;
import com.aguavigia.ctg.domain.port.in.RecalcularSectorUseCase;
import com.aguavigia.ctg.domain.port.in.RegistrarEventoBitacoraUseCase;
import com.aguavigia.ctg.domain.port.out.CorteAguaRepository;
import com.aguavigia.ctg.domain.port.out.RelojPort;
import com.aguavigia.ctg.domain.port.out.TransaccionPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Un corte que nadie cerró ni confirmó deja de afirmar algo pasado el plazo de expiración (D3): el resolutor
 * ya lo ignora desde ese instante, pero el corte seguía «abierto» en la base. Aquí se marca EXPIRADO, que lo
 * saca del Índice de Cumplimiento (no tiene hora real) sin borrar que existió, y se anexa a la bitácora por
 * cada barrio que seguía pendiente. El barrio se recalcula al final para que vuelva a «sin datos» sin esperar.
 */
public class ExpirarCortesVencidosService implements ExpirarCortesVencidosUseCase {

    private static final Logger log = LoggerFactory.getLogger(ExpirarCortesVencidosService.class);

    private final CorteAguaRepository cortes;
    private final RegistrarEventoBitacoraUseCase registrarEvento;
    private final RecalcularSectorUseCase recalcular;
    private final RelojPort reloj;
    private final TransaccionPort transaccion;
    private final Duration expiraTrasFin;

    public ExpirarCortesVencidosService(CorteAguaRepository cortes, RegistrarEventoBitacoraUseCase registrarEvento,
                                        RecalcularSectorUseCase recalcular, RelojPort reloj,
                                        TransaccionPort transaccion, Duration expiraTrasFin) {
        this.cortes = cortes;
        this.registrarEvento = registrarEvento;
        this.recalcular = recalcular;
        this.reloj = reloj;
        this.transaccion = transaccion;
        this.expiraTrasFin = expiraTrasFin;
    }

    @Override
    public int expirarVencidos() {
        Instant ahora = reloj.ahora();
        int expirados = 0;
        for (CorteAgua corte : cortes.listarAbiertos()) {
            if (ahora.isBefore(corte.ventana().finPrometido().plus(expiraTrasFin))) {
                continue;
            }
            try {
                if (expirar(corte.id(), ahora)) {
                    expirados++;
                }
            } catch (RuntimeException fallo) {
                // Un corte que falla no puede dejar abiertos a los demás: la próxima pasada lo reintenta.
                log.warn("No se pudo expirar el corte '{}': {}", corte.id().valor(), fallo.toString());
            }
        }
        return expirados;
    }

    /**
     * El corte se vuelve a leer dentro de la transacción: la lista de abiertos es de antes, y un veedor pudo anularlo
     * o cerrarlo desde entonces. Solo se expira si sigue abierto; si ya no, quien lo cambió primero gana.
     *
     * @return si este corte se expiró
     */
    private boolean expirar(CorteId id, Instant ahora) {
        Optional<List<SectorId>> expirado = transaccion.ejecutar(() -> {
            CorteAgua fresco = cortes.buscarPorId(id).filter(CorteAgua::estaAbierto).orElse(null);
            if (fresco == null) {
                return Optional.empty();
            }
            CorteAgua expirada = fresco.expirar();
            List<SectorId> pendientes = fresco.sectoresAfectados().stream()
                    .filter(sector -> fresco.cierreDe(sector).isEmpty())
                    .toList();
            cortes.guardar(expirada);
            pendientes.forEach(sector -> registrarEvento.registrar(EventoBitacoraFactory.corteExpirado(expirada, sector, ahora)));
            return Optional.of(pendientes);
        });
        if (expirado.isEmpty()) {
            return false;
        }
        List<SectorId> pendientes = expirado.get();

        for (SectorId sector : pendientes) {
            try {
                recalcular.recalcular(sector);
            } catch (RuntimeException fallo) {
                // El corte ya quedó expirado; la puesta al día periódica recalcula el barrio.
                log.warn("No se pudo recalcular el barrio '{}' tras expirar el corte '{}': {}",
                        sector.valor(), id.valor(), fallo.toString());
            }
        }
        return true;
    }
}
