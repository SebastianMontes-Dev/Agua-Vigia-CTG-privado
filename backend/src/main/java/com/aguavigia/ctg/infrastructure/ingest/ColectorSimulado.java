package com.aguavigia.ctg.infrastructure.ingest;

import com.aguavigia.ctg.domain.BoletinSimulado;
import com.aguavigia.ctg.domain.port.out.BuzonDeBoletinesSimuladosPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Modo simulación de la ingesta (`aguavigia.ingesta.modo=simulacion`, D38): ocupa el lugar de Acuacar y de la prensa. Su única fuente es
 * lo que el simulador le hace llegar por {@code POST /api/sim/boletines}, que entra por el mismo camino que un boletín en vivo —limpieza,
 * deduplicación por hash, prefiltro, extractor, compuertas y publicación—.
 *
 * No se reutiliza {@link ColectorLocalDeBoletines} porque ese lee un recurso de classpath una sola vez al arrancar y no admite boletines
 * que lleguen con el tiempo. Los boletines se guardan en memoria: la simulación es efímera y se reinicia con la base.
 */
@Component
@ConditionalOnProperty(prefix = "aguavigia.ingesta", name = "modo", havingValue = "simulacion")
public class ColectorSimulado implements FuenteDatosPort, BuzonDeBoletinesSimuladosPort {

    private static final String FUENTE = "acuacar";

    private final List<BoletinSimulado> boletines = new CopyOnWriteArrayList<>();

    public ColectorSimulado(@Value("${aguavigia.sim.habilitada:false}") boolean simulacionHabilitada) {
        // INGESTA_MODO=simulacion sin la simulación habilitada (la instancia real configurada por error) sustituiría Acuacar por un buzón que
        // nadie puede llenar: la ingesta quedaría muda sin avisar.
        if (!simulacionHabilitada) {
            throw new IllegalStateException("INGESTA_MODO=simulacion exige AGUAVIGIA_SIM_HABILITADA=true (y AGUAVIGIA_SIM_CLAVE): sin ellas nadie puede "
                    + "entregar boletines y la ingesta real quedaría muda");
        }
    }

    @Override
    public void encolar(BoletinSimulado boletin) {
        boletines.add(boletin);
    }

    @Override
    public void vaciar() {
        boletines.clear();
    }

    @Override
    public List<DocumentoCrudo> obtenerDesde(Instant desde) {
        // Sin filtrar por `desde`: la deduplicación por hash del orquestador evita repetir lo ya leído, y la marca (que solo avanza) descartaría en silencio
        // un boletín con fecha anterior si el reloj de la simulación retrocede.
        return boletines.stream()
                .sorted(Comparator.comparing(BoletinSimulado::fecha))
                .map(ColectorSimulado::aDocumento)
                .toList();
    }

    private static DocumentoCrudo aDocumento(BoletinSimulado boletin) {
        String enlace = boletin.enlace() == null || boletin.enlace().isBlank()
                ? "https://simulacion.local/boletin/" + boletin.id()
                : boletin.enlace();
        return DocumentoCrudo.de(FUENTE, enlace, boletin.fecha(), LimpiadorHtml.limpiar(boletin.titulo()),
                LimpiadorHtml.limpiar(boletin.contenidoHtml()), boletin.portada());
    }
}
