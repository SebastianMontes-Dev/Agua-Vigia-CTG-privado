package com.aguavigia.ctg.domain;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Observabilidad mínima (D37): lo que hay que medir para calibrar los umbrales y los plazos, que hoy son valores iniciales sin datos que
 * los respalden. Son contadores de <em>este</em> proceso desde {@code desde}: un reinicio los pone a cero y con más de una réplica cada una
 * cuenta los suyos, igual que la salud de los colectores.
 *
 * <p>Los mapas usan el nombre del enum como clave; {@code cambiosDeEstado} usa «ESTADO/ORIGEN» y «SIN_DATOS/SIN_ORIGEN» para el regreso a «sin datos».
 */
public record MetricasDelSistema(Instant desde,
                                 Map<String, Long> cambiosDeEstado,
                                 long disputasAbiertas,
                                 Map<String, Long> quorumsRechazadosPorComposicion,
                                 Map<String, Long> reportesPorNivelDeVerificacion,
                                 Map<String, Long> fallosDeColectores,
                                 TiempoHastaElCambio tiempoHastaElCambioDeEstado) {

    public MetricasDelSistema {
        Objects.requireNonNull(desde, "Las métricas deben decir desde cuándo cuentan");
        cambiosDeEstado = copiaOrdenada(cambiosDeEstado);
        quorumsRechazadosPorComposicion = copiaOrdenada(quorumsRechazadosPorComposicion);
        reportesPorNivelDeVerificacion = copiaOrdenada(reportesPorNivelDeVerificacion);
        fallosDeColectores = copiaOrdenada(fallosDeColectores);
        Objects.requireNonNull(tiempoHastaElCambioDeEstado, "Falta el tiempo hasta el cambio de estado");
    }

    /** Copia inmodificable que conserva el orden de iteración del original ({@code Map.copyOf} lo baraja). */
    private static Map<String, Long> copiaOrdenada(Map<String, Long> mapa) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(mapa));
    }

    /**
     * Cuánto pasó entre el primer reporte de los vecinos que sostienen un estado y el momento en que el barrio lo publicó. Solo cuenta los
     * cambios que sostienen los vecinos o los sensores: uno oficial no tiene «primer reporte».
     */
    public record TiempoHastaElCambio(long cambios, long promedioSegundos, long maximoSegundos) {
    }
}
