package com.aguavigia.ctg.infrastructure.metricas;

import com.aguavigia.ctg.domain.EstadoServicio;
import com.aguavigia.ctg.domain.MetricasDelSistema;
import com.aguavigia.ctg.domain.NivelDeVerificacion;
import com.aguavigia.ctg.domain.OrigenEstado;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.TipoReporte;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class MetricasEnMemoriaAdapterTest {

    private static final Instant ARRANQUE = Instant.parse("2026-10-06T15:00:00Z");
    private static final SectorId MANGA = new SectorId("manga");

    private final MetricasEnMemoriaAdapter metricas = new MetricasEnMemoriaAdapter(() -> ARRANQUE);

    @Test
    void sinNadaQueContarTodoEstaEnCeroYDicePorQueMomentoCuenta() {
        MetricasDelSistema m = metricas.instantanea();

        assertThat(m.desde()).isEqualTo(ARRANQUE);
        assertThat(m.cambiosDeEstado()).isEmpty();
        assertThat(m.disputasAbiertas()).isZero();
        assertThat(m.quorumsRechazadosPorComposicion()).isEmpty();
        assertThat(m.reportesPorNivelDeVerificacion()).isEmpty();
        assertThat(m.fallosDeColectores()).isEmpty();
        assertThat(m.tiempoHastaElCambioDeEstado().cambios()).isZero();
    }

    @Test
    void cuentaLosCambiosDeEstadoPorEstadoYOrigen() {
        metricas.cambioDeEstado(MANGA, EstadoServicio.SIN_SERVICIO, OrigenEstado.VECINOS);
        metricas.cambioDeEstado(MANGA, EstadoServicio.SIN_SERVICIO, OrigenEstado.VECINOS);
        metricas.cambioDeEstado(MANGA, EstadoServicio.CON_SERVICIO, OrigenEstado.VEEDOR);

        assertThat(metricas.instantanea().cambiosDeEstado())
                .containsEntry("SIN_SERVICIO/VECINOS", 2L)
                .containsEntry("CON_SERVICIO/VEEDOR", 1L);
    }

    /** Volver a «sin datos» también es un cambio, y no tiene estado ni origen: se cuenta con nombres propios, no se pierde. */
    @Test
    void elRegresoASinDatosSeCuentaConNombrePropio() {
        metricas.cambioDeEstado(MANGA, null, null);

        assertThat(metricas.instantanea().cambiosDeEstado()).containsEntry("SIN_DATOS/SIN_ORIGEN", 1L);
    }

    @Test
    void cuentaLasDisputasLosReportesPorNivelYLosFallosDeColectores() {
        metricas.disputaAbierta();
        metricas.disputaAbierta();
        metricas.reporteRecibido(NivelDeVerificacion.NINGUNA);
        metricas.reporteRecibido(NivelDeVerificacion.NINGUNA);
        metricas.reporteRecibido(NivelDeVerificacion.CUENTA_VERIFICADA);
        metricas.falloDeColector("acuacar");

        MetricasDelSistema m = metricas.instantanea();
        assertThat(m.disputasAbiertas()).isEqualTo(2);
        assertThat(m.reportesPorNivelDeVerificacion()).containsEntry("NINGUNA", 2L).containsEntry("CUENTA_VERIFICADA", 1L);
        assertThat(m.fallosDeColectores()).containsEntry("acuacar", 1L);
    }

    /**
     * El recálculo corre con cada reporte: una ráfaga con composición inválida lo vería cientos de veces. Se cuenta el episodio (barrio y tipo),
     * no cada recálculo, y el episodio termina cuando el barrio cambia de estado.
     */
    @Test
    void unQuorumRechazadoSeCuentaUnaVezPorEpisodioYElCambioDeEstadoLoCierra() {
        for (int i = 0; i < 50; i++) {
            metricas.quorumRechazadoPorComposicion(MANGA, TipoReporte.SIN_AGUA);
        }
        assertThat(metricas.instantanea().quorumsRechazadosPorComposicion()).containsEntry("SIN_AGUA", 1L);

        metricas.quorumRechazadoPorComposicion(new SectorId("crespo"), TipoReporte.SIN_AGUA);
        assertThat(metricas.instantanea().quorumsRechazadosPorComposicion()).containsEntry("SIN_AGUA", 2L);

        metricas.cambioDeEstado(MANGA, EstadoServicio.SIN_SERVICIO, OrigenEstado.ACUACAR);
        metricas.quorumRechazadoPorComposicion(MANGA, TipoReporte.SIN_AGUA);
        assertThat(metricas.instantanea().quorumsRechazadosPorComposicion()).containsEntry("SIN_AGUA", 3L);
    }

    @Test
    void midePromedioYMaximoDelTiempoHastaElCambioDeEstado() {
        metricas.tiempoHastaElCambioDeEstado(Duration.ofSeconds(60));
        metricas.tiempoHastaElCambioDeEstado(Duration.ofSeconds(300));
        metricas.tiempoHastaElCambioDeEstado(Duration.ofSeconds(120));

        MetricasDelSistema.TiempoHastaElCambio t = metricas.instantanea().tiempoHastaElCambioDeEstado();
        assertThat(t.cambios()).isEqualTo(3);
        assertThat(t.promedioSegundos()).isEqualTo(160);
        assertThat(t.maximoSegundos()).isEqualTo(300);
    }

    @Test
    void unaDuracionNegativaNoEnsuciaLaMedida() {
        metricas.tiempoHastaElCambioDeEstado(Duration.ofSeconds(-5));

        assertThat(metricas.instantanea().tiempoHastaElCambioDeEstado().cambios()).isZero();
    }

    @Test
    void soportaEscriturasConcurrentesSinPerderCuentas() {
        IntStream.range(0, 8_000).parallel().forEach(i -> {
            metricas.disputaAbierta();
            metricas.reporteRecibido(NivelDeVerificacion.NINGUNA);
            metricas.tiempoHastaElCambioDeEstado(Duration.ofSeconds(i % 10));
        });

        MetricasDelSistema m = metricas.instantanea();
        assertThat(m.disputasAbiertas()).isEqualTo(8_000);
        assertThat(m.reportesPorNivelDeVerificacion()).containsEntry("NINGUNA", 8_000L);
        assertThat(m.tiempoHastaElCambioDeEstado().cambios()).isEqualTo(8_000);
    }

    @Test
    void laInstantaneaEsUnaCopia() {
        metricas.disputaAbierta();
        MetricasDelSistema antes = metricas.instantanea();
        metricas.disputaAbierta();

        assertThat(antes.disputasAbiertas()).isEqualTo(1);
        assertThat(metricas.instantanea().disputasAbiertas()).isEqualTo(2);
    }
}
