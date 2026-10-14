package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.Coordenada;
import com.aguavigia.ctg.domain.EstadoServicio;
import com.aguavigia.ctg.domain.EstrategiaConsenso;
import com.aguavigia.ctg.domain.HuellaDispositivo;
import com.aguavigia.ctg.domain.MarcasDeEstado;
import com.aguavigia.ctg.domain.OrigenEstado;
import com.aguavigia.ctg.domain.PropuestaId;
import com.aguavigia.ctg.domain.PropuestaIngesta;
import com.aguavigia.ctg.domain.ReglasDeEstado;
import com.aguavigia.ctg.domain.ReporteCiudadano;
import com.aguavigia.ctg.domain.ReporteId;
import com.aguavigia.ctg.domain.ResolutorDeEstadoSector;
import com.aguavigia.ctg.domain.Sector;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.TipoReporte;
import com.aguavigia.ctg.domain.port.in.RegistrarEventoBitacoraUseCase;
import com.aguavigia.ctg.domain.port.out.ContadorReportesPort;
import com.aguavigia.ctg.domain.port.out.CorteAguaRepository;
import com.aguavigia.ctg.domain.port.out.PropuestaIngestaRepository;
import com.aguavigia.ctg.domain.port.out.ReporteCiudadanoRepository;
import com.aguavigia.ctg.domain.port.out.ReservaDeEvaluacionPort;
import com.aguavigia.ctg.domain.port.out.SectorRepository;
import com.aguavigia.ctg.domain.port.out.TransaccionPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * Caracteriza lo que antes era un «parpadeo» entre el consenso de vecinos y el barrido por ventana: los dos
 * escribían {@code estadoActual} sin una regla común, y un boletín vencido hace menos de un día devolvía el
 * barrio a CON_SERVICIO cada minuto aunque los vecinos ya hubieran demostrado lo contrario. Ahora los dos son
 * solo motivos para recalcular el barrio, así que no pueden contradecirse. Usa un repositorio de sectores en
 * memoria para que se pisen de verdad, sin mocks que decidan por ellos.
 */
class ConsensoContraVentanaTest {

    private static final SectorId MANGA = new SectorId("manga");
    private static final Instant INICIO = Instant.parse("2026-08-21T14:00:00Z");
    private static final Instant FIN = Instant.parse("2026-08-21T23:00:00Z");

    private final AtomicReference<Instant> ahora = new AtomicReference<>();
    private SectoresEnMemoria sectores;
    private EvaluarConsensoService consenso;
    private ActualizarEstadosPorVentanaService barrido;

    private TipoReporte tipoDeLosReportes = TipoReporte.SIN_AGUA;
    private int reportesEnLaVentana;

    @BeforeEach
    void montar() {
        sectores = new SectoresEnMemoria(ahora::get, new Sector(MANGA, "MANGA", 1000, EstadoServicio.CON_SERVICIO));
        TransaccionPort pasoDirecto = new TransaccionPort() {
            @Override
            public <T> T ejecutar(java.util.function.Supplier<T> accion) {
                return accion.get();
            }
        };

        ReporteCiudadanoRepository reportes = mock(ReporteCiudadanoRepository.class);
        ContadorReportesPort contador = mock(ContadorReportesPort.class);
        ReservaDeEvaluacionPort reserva = mock(ReservaDeEvaluacionPort.class);
        given(reserva.reservar(any())).willReturn(true);
        given(contador.contarRecientes(any(), any())).willAnswer(i -> (long) reportesEnLaVentana);
        given(reportes.contarVotosRecientes(any(), any())).willAnswer(i ->
                reportesEnLaVentana == 0 ? Map.of() : Map.of(tipoDeLosReportes, (long) reportesEnLaVentana));
        given(reportes.listarRecientesPorSector(any(), any())).willAnswer(i -> IntStream.range(0, reportesEnLaVentana)
                .mapToObj(n -> reporte("r" + n)).toList());
        EstrategiaConsenso tresReportes = sector -> 3;

        PropuestaIngesta boletin = new PropuestaIngesta(
                new PropuestaId("p1"), MANGA, EstadoServicio.SIN_SERVICIO, "acuacar",
                "https://acuacar.com/2854", "cita", 0.85, INICIO.minusSeconds(3600),
                INICIO, FIN).aprobar();
        PropuestaIngestaRepository propuestas = mock(PropuestaIngestaRepository.class);
        given(propuestas.listarAprobadasConVentanaVigente(any())).willReturn(List.of(boletin));
        given(propuestas.listarAprobadasPorSector(any())).willReturn(List.of(boletin));
        CorteAguaRepository cortes = mock(CorteAguaRepository.class);
        given(cortes.listarPorSector(any())).willReturn(List.of());
        ResolutorDeEstadoSector resolutor = new ResolutorDeEstadoSector(ReglasDeEstado.porDefecto());
        RecalcularSectorService recalcular = new RecalcularSectorService(sectores, cortes, propuestas, reportes,
                tresReportes, resolutor, mock(RegistrarEventoBitacoraUseCase.class), ahora::get, pasoDirecto,
                Duration.ofMinutes(30), 2);

        consenso = new EvaluarConsensoService(sectores, contador, reserva, tresReportes, ReglasDeEstado.porDefecto(),
                recalcular, 30);
        barrido = new ActualizarEstadosPorVentanaService(propuestas, cortes, recalcular, ahora::get, Duration.ofHours(72));
    }

    private ReporteCiudadano reporte(String id) {
        return new ReporteCiudadano(new ReporteId(id), MANGA, tipoDeLosReportes, null,
                new HuellaDispositivo("h-" + id), ahora.get())
                .conIdentidad(com.aguavigia.ctg.domain.NivelDeVerificacion.CUENTA_VERIFICADA, "red-" + id);
    }

    /** La promesa vencida no prueba que volvió el agua: el barrio sigue sin servicio, por confirmar, sin parpadear. */
    @Test
    void elBarridoNoDevuelveElBarrioAConServicioSoloPorqueVencioLaPromesa() {
        ahora.set(FIN.plusSeconds(60));
        barrido.aplicarVentanasVencidas();

        assertThat(sectores.estadoDe(MANGA)).isEqualTo(EstadoServicio.SIN_SERVICIO);
        assertThat(sectores.marcasDe(MANGA).porConfirmar()).isTrue();
    }

    /** Los vecinos que dicen que sigue sin agua coinciden con el boletín: no hay nada que cambiar ni que pisar. */
    @Test
    void elConsensoYElBarridoNoSePisanDespuesDelFinDeLaVentana() {
        ahora.set(FIN.plusSeconds(60));
        barrido.aplicarVentanasVencidas();

        ahora.set(FIN.plusSeconds(600));
        tipoDeLosReportes = TipoReporte.SIN_AGUA;
        reportesEnLaVentana = 3;
        assertThat(consenso.evaluar(MANGA).alcanzado()).isFalse();
        assertThat(sectores.estadoDe(MANGA)).isEqualTo(EstadoServicio.SIN_SERVICIO);

        ahora.set(FIN.plusSeconds(660));
        barrido.aplicarVentanasVencidas();

        assertThat(sectores.estadoDe(MANGA)).isEqualTo(EstadoServicio.SIN_SERVICIO);
    }

    /** Pasada la promesa basta el quórum reducido; y lo que los vecinos confirmaron el barrido no lo deshace. */
    @Test
    void elBarridoNoDeshaceUnRestablecimientoQueLosVecinosConfirmaronDespuesDeLaPromesa() {
        ahora.set(FIN.plusSeconds(60));
        barrido.aplicarVentanasVencidas();

        ahora.set(FIN.plusSeconds(600));
        tipoDeLosReportes = TipoReporte.SERVICIO_RESTABLECIDO;
        reportesEnLaVentana = 2;
        assertThat(consenso.evaluar(MANGA).alcanzado()).isTrue();
        assertThat(sectores.estadoDe(MANGA)).isEqualTo(EstadoServicio.CON_SERVICIO);
        assertThat(sectores.marcasDe(MANGA).origen()).isEqualTo(OrigenEstado.VECINOS);

        // Los reportes salen de la ventana de 30 minutos, pero el barrio recuerda lo que confirmaron.
        ahora.set(FIN.plusSeconds(3600));
        reportesEnLaVentana = 0;
        barrido.aplicarVentanasVencidas();

        assertThat(sectores.estadoDe(MANGA)).isEqualTo(EstadoServicio.CON_SERVICIO);
    }

    /** Repositorio de sectores mínimo: guardar y compare-and-set de verdad, para que los escritores se pisen. */
    private static final class SectoresEnMemoria implements SectorRepository {
        private final Map<SectorId, Sector> almacen = new LinkedHashMap<>();
        private final java.util.function.Supplier<Instant> reloj;

        SectoresEnMemoria(java.util.function.Supplier<Instant> reloj, Sector... iniciales) {
            this.reloj = reloj;
            for (Sector sector : iniciales) {
                almacen.put(sector.id(), sector);
            }
        }

        EstadoServicio estadoDe(SectorId id) {
            return almacen.get(id).estadoActual();
        }

        MarcasDeEstado marcasDe(SectorId id) {
            return almacen.get(id).marcas();
        }

        @Override
        public Optional<Sector> buscarPorId(SectorId id) {
            return Optional.ofNullable(almacen.get(id));
        }

        @Override
        public Optional<Sector> buscarPorCoordenada(Coordenada coordenada) {
            return Optional.empty();
        }

        @Override
        public List<Sector> listarTodos() {
            return new ArrayList<>(almacen.values());
        }

        @Override
        public Sector guardar(Sector sector) {
            almacen.put(sector.id(), sector);
            return sector;
        }

        @Override
        public boolean cambiarEstadoSiEs(SectorId id, EstadoServicio esperado, EstadoServicio nuevo) {
            Sector actual = almacen.get(id);
            if (actual == null || actual.estadoActual() != esperado) {
                return false;
            }
            almacen.put(id, actual.conEstado(nuevo));
            return true;
        }

        @Override
        public boolean publicarSiEs(SectorId id, EstadoServicio esperado, EstadoServicio nuevo, MarcasDeEstado marcas) {
            Sector actual = almacen.get(id);
            if (actual == null || actual.estadoActual() != esperado) {
                return false;
            }
            Instant ahora = reloj.get();
            boolean cambia = actual.estadoActual() != nuevo;
            almacen.put(id, new Sector(id, actual.nombre(), actual.poblacion(), nuevo,
                    cambia ? ahora : actual.estadoActualizadoEn(), cambia ? ahora : actual.estadoVerificadoEn(), marcas));
            return true;
        }

        @Override
        public boolean abrirDisputaSiEs(SectorId id, EstadoServicio esperado, MarcasDeEstado marcas) {
            Sector actual = almacen.get(id);
            if (actual == null || actual.estadoActual() != esperado || actual.marcas().enDisputa()) {
                return false;
            }
            almacen.put(id, new Sector(id, actual.nombre(), actual.poblacion(), esperado, actual.estadoActualizadoEn(),
                    actual.estadoVerificadoEn(), marcas));
            return true;
        }

        @Override
        public boolean confirmarEstado(SectorId id, EstadoServicio estado) {
            Sector actual = almacen.get(id);
            return actual != null && actual.estadoActual() == estado;
        }
    }
}
