package com.aguavigia.ctg.api.dto;

import com.aguavigia.ctg.domain.MetricasDelSistema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Map;

@Schema(description = "Contadores de calibración (D37) de este proceso desde `desde`. Un reinicio los pone a cero; con varias réplicas, cada una cuenta los suyos.")
public record MetricasDelSistemaRespuesta(
        @Schema(description = "Desde cuándo cuentan estos contadores") Instant desde,
        @Schema(description = "Cambios de estado publicados, por «ESTADO/ORIGEN» (SIN_DATOS/SIN_ORIGEN es el regreso a «sin datos»)")
        Map<String, Long> cambiosDeEstado,
        @Schema(description = "Barrios que entraron en disputa (los vecinos contradicen lo oficial)") long disputasAbiertas,
        @Schema(description = "Quórums que llegaron al umbral pero no a la composición (verificación o redes distintas), por tipo de reporte y por episodio")
        Map<String, Long> quorumsRechazadosPorComposicion,
        @Schema(description = "Reportes recibidos por nivel de verificación: NINGUNA, UBICACION_VERIFICADA o CUENTA_VERIFICADA")
        Map<String, Long> reportesPorNivelDeVerificacion,
        @Schema(description = "Fallos de cada colector de ingesta, por nombre") Map<String, Long> fallosDeColectores,
        @Schema(description = "Del primer reporte que sostiene un estado de los vecinos a su publicación")
        TiempoHastaElCambio tiempoHastaElCambioDeEstado) {

    @Schema(description = "Cambios medidos y cuánto tardaron, en segundos")
    public record TiempoHastaElCambio(long cambios, long promedioSegundos, long maximoSegundos) {
    }

    public static MetricasDelSistemaRespuesta de(MetricasDelSistema m) {
        return new MetricasDelSistemaRespuesta(m.desde(), m.cambiosDeEstado(), m.disputasAbiertas(),
                m.quorumsRechazadosPorComposicion(), m.reportesPorNivelDeVerificacion(), m.fallosDeColectores(),
                new TiempoHastaElCambio(m.tiempoHastaElCambioDeEstado().cambios(), m.tiempoHastaElCambioDeEstado().promedioSegundos(),
                        m.tiempoHastaElCambioDeEstado().maximoSegundos()));
    }
}
