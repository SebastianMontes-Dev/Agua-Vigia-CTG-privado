package com.aguavigia.ctg.domain.port.out;

import com.aguavigia.ctg.domain.EstadoServicio;
import com.aguavigia.ctg.domain.MetricasDelSistema;
import com.aguavigia.ctg.domain.NivelDeVerificacion;
import com.aguavigia.ctg.domain.OrigenEstado;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.TipoReporte;

import java.time.Duration;

/**
 * Contadores propios de la observabilidad mínima (D37). Quien instrumenta solo avisa de lo que pasó; qué se cuenta y cómo se guarda es del
 * adaptador. Ningún método puede lanzar ni bloquear: medir no puede tumbar un reporte ni un recálculo.
 */
public interface MetricasDelSistemaPort {

    /** Un barrio publicó un estado nuevo ({@code estado} y {@code origen} nulos: volvió a «sin datos»). */
    void cambioDeEstado(SectorId sector, EstadoServicio estado, OrigenEstado origen);

    /** Un barrio entró en disputa (los vecinos contradicen lo oficial). */
    void disputaAbierta();

    /** Un quórum llegó al umbral pero su composición no lo valida (verificación o diversidad de redes). Se llama en cada recálculo. */
    void quorumRechazadoPorComposicion(SectorId sector, TipoReporte tipo);

    void reporteRecibido(NivelDeVerificacion nivel);

    void falloDeColector(String colector);

    /** Del primer reporte que sostiene un estado de los vecinos a su publicación. */
    void tiempoHastaElCambioDeEstado(Duration duracion);

    MetricasDelSistema instantanea();

    /** Para quien no mide (pruebas y construcciones que no lo piden): no cuenta nada. */
    MetricasDelSistemaPort NINGUNA = new MetricasDelSistemaPort() {
        @Override
        public void cambioDeEstado(SectorId sector, EstadoServicio estado, OrigenEstado origen) {
        }

        @Override
        public void disputaAbierta() {
        }

        @Override
        public void quorumRechazadoPorComposicion(SectorId sector, TipoReporte tipo) {
        }

        @Override
        public void reporteRecibido(NivelDeVerificacion nivel) {
        }

        @Override
        public void falloDeColector(String colector) {
        }

        @Override
        public void tiempoHastaElCambioDeEstado(Duration duracion) {
        }

        @Override
        public MetricasDelSistema instantanea() {
            throw new UnsupportedOperationException("Esta construcción no mide nada");
        }
    };
}
