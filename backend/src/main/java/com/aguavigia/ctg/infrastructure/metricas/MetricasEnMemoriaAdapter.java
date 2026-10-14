package com.aguavigia.ctg.infrastructure.metricas;

import com.aguavigia.ctg.domain.EstadoServicio;
import com.aguavigia.ctg.domain.MetricasDelSistema;
import com.aguavigia.ctg.domain.NivelDeVerificacion;
import com.aguavigia.ctg.domain.OrigenEstado;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.TipoReporte;
import com.aguavigia.ctg.domain.port.out.MetricasDelSistemaPort;
import com.aguavigia.ctg.domain.port.out.RelojPort;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Contadores en memoria de la observabilidad mínima (D37). En memoria y no en Mongo a propósito, como la salud de los colectores: son
 * telemetría de <em>este</em> proceso para calibrar umbrales, no un dato del acueducto; un reinicio los pone a cero ({@code desde} lo dice) y,
 * con más de una réplica, cada una cuenta los suyos (el despliegue del proyecto es de instancia única).
 *
 * <p>Un quórum rechazado se cuenta por <em>episodio</em> (barrio y tipo), no por recálculo: el recálculo corre con cada reporte y una ráfaga
 * de composición inválida lo vería cientos de veces. El episodio termina cuando el barrio cambia de estado.
 */
@Component
public class MetricasEnMemoriaAdapter implements MetricasDelSistemaPort {

    private final Instant desde;
    private final Map<String, LongAdder> cambiosDeEstado = new ConcurrentHashMap<>();
    private final LongAdder disputas = new LongAdder();
    private final Map<String, LongAdder> quorumsRechazados = new ConcurrentHashMap<>();
    private final Set<String> episodiosRechazados = ConcurrentHashMap.newKeySet();
    private final Map<String, LongAdder> reportesPorNivel = new ConcurrentHashMap<>();
    private final Map<String, LongAdder> fallosDeColectores = new ConcurrentHashMap<>();
    private final LongAdder cambiosMedidos = new LongAdder();
    private final LongAdder segundosAcumulados = new LongAdder();
    private final AtomicLong maximoSegundos = new AtomicLong();

    public MetricasEnMemoriaAdapter(RelojPort reloj) {
        this.desde = reloj.ahora();
    }

    @Override
    public void cambioDeEstado(SectorId sector, EstadoServicio estado, OrigenEstado origen) {
        contar(cambiosDeEstado, (estado == null ? "SIN_DATOS" : estado.name()) + "/" + (origen == null ? "SIN_ORIGEN" : origen.name()));
        // Un cambio cierra los episodios de quórum rechazado de ese barrio: el siguiente rechazo es otro.
        String prefijo = sector.valor() + "|";
        episodiosRechazados.removeIf(episodio -> episodio.startsWith(prefijo));
    }

    @Override
    public void disputaAbierta() {
        disputas.increment();
    }

    @Override
    public void quorumRechazadoPorComposicion(SectorId sector, TipoReporte tipo) {
        if (episodiosRechazados.add(sector.valor() + "|" + tipo.name())) {
            contar(quorumsRechazados, tipo.name());
        }
    }

    @Override
    public void reporteRecibido(NivelDeVerificacion nivel) {
        contar(reportesPorNivel, nivel.name());
    }

    @Override
    public void falloDeColector(String colector) {
        contar(fallosDeColectores, colector);
    }

    @Override
    public void tiempoHastaElCambioDeEstado(Duration duracion) {
        if (duracion == null || duracion.isNegative()) {
            return;
        }
        long segundos = duracion.toSeconds();
        cambiosMedidos.increment();
        segundosAcumulados.add(segundos);
        maximoSegundos.accumulateAndGet(segundos, Math::max);
    }

    @Override
    public MetricasDelSistema instantanea() {
        long cambios = cambiosMedidos.sum();
        long promedio = cambios == 0 ? 0 : Math.round((double) segundosAcumulados.sum() / cambios);
        return new MetricasDelSistema(desde, copia(cambiosDeEstado), disputas.sum(), copia(quorumsRechazados), copia(reportesPorNivel),
                copia(fallosDeColectores), new MetricasDelSistema.TiempoHastaElCambio(cambios, promedio, maximoSegundos.get()));
    }

    private static void contar(Map<String, LongAdder> contadores, String clave) {
        contadores.computeIfAbsent(clave, k -> new LongAdder()).increment();
    }

    private static Map<String, Long> copia(Map<String, LongAdder> contadores) {
        Map<String, Long> copia = new TreeMap<>();
        contadores.forEach((clave, contador) -> copia.put(clave, contador.sum()));
        return copia;
    }
}
