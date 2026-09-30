package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.EstadoServicio;
import com.aguavigia.ctg.domain.EstrategiaConsenso;
import com.aguavigia.ctg.domain.HuellaDispositivo;
import com.aguavigia.ctg.domain.MarcasDeEstado;
import com.aguavigia.ctg.domain.PropuestaId;
import com.aguavigia.ctg.domain.PropuestaIngesta;
import com.aguavigia.ctg.domain.ReporteCiudadano;
import com.aguavigia.ctg.domain.ReporteId;
import com.aguavigia.ctg.domain.Coordenada;
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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * Caracteriza el «parpadeo» entre el consenso de vecinos y el barrido por ventana: los dos escriben
 * {@code estadoActual} sin una regla común. Un boletín con ventana vencida hace menos de un día sigue
 * «vigente» para el barrido, que cada minuto devuelve el barrio a CON_SERVICIO aunque los vecinos ya
 * hayan demostrado lo contrario; el siguiente reporte lo vuelve a mover. Usa un repositorio de
 * sectores en memoria para que el consenso y el barrido se pisen de verdad, sin mocks que decidan
 * por ellos.
 */
class ConsensoContraVentanaTest {

    private static final SectorId MANGA = new SectorId("manga");
    private static final Instant INICIO = Instant.parse("2026-08-21T14:00:00Z");
    private static final Instant FIN = Instant.parse("2026-08-21T23:00:00Z");

    private final AtomicReference<Instant> ahora = new AtomicReference<>();
    private SectoresEnMemoria sectores;
    private EvaluarConsensoService consenso;
    private ActualizarEstadosPorVentanaService barrido;

    @BeforeEach
    void montar() {
        sectores = new SectoresEnMemoria(new Sector(MANGA, "MANGA", 1000, EstadoServicio.CON_SERVICIO));
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
        given(contador.contarRecientes(any(), any())).willReturn(3L);
        given(reportes.contarVotosRecientes(any(), any())).willReturn(Map.of(TipoReporte.SIN_AGUA, 3L));
        given(reportes.listarRecientesPorSector(any(), any())).willAnswer(i -> List.of(
                reporte("r1"), reporte("r2"), reporte("r3")));
        EstrategiaConsenso tresReportes = sector -> 3;
        consenso = new EvaluarConsensoService(sectores, reportes, contador, reserva, tresReportes,
                mock(RegistrarEventoBitacoraUseCase.class), ahora::get, pasoDirecto, 30);

        PropuestaIngesta boletin = new PropuestaIngesta(
                new PropuestaId("p1"), MANGA, EstadoServicio.SIN_SERVICIO, "acuacar",
                "https://acuacar.com/2854", "cita", 0.85, INICIO.minusSeconds(3600),
                INICIO, FIN).aprobar();
        PropuestaIngestaRepository propuestas = mock(PropuestaIngestaRepository.class);
        given(propuestas.listarAprobadasConVentanaVigente(any())).willReturn(List.of(boletin));
        CorteAguaRepository cortes = mock(CorteAguaRepository.class);
        given(cortes.listarPorSectores(any())).willReturn(List.of());
        barrido = new ActualizarEstadosPorVentanaService(
                propuestas, sectores, mock(RegistrarEventoBitacoraUseCase.class), cortes, ahora::get, pasoDirecto);
    }

    private ReporteCiudadano reporte(String id) {
        return new ReporteCiudadano(new ReporteId(id), MANGA, TipoReporte.SIN_AGUA, null,
                new HuellaDispositivo("h-" + id), ahora.get());
    }

    @Disabled("F1 (plan 1.3): lo resuelve ResolutorDeEstadoSector; reactivar al migrar el barrido y el consenso")
    @Test
    void elBarridoNoDebeDeshacerUnEstadoQueLosVecinosFijaronDespuesDelFinDeLaVentana() {
        // El boletín prometió hasta las 18:00 (23:00 UTC). A las 18:01 el barrido devuelve el barrio a CON_SERVICIO.
        ahora.set(FIN.plusSeconds(60));
        barrido.aplicarVentanasVencidas();
        assertThat(sectores.estadoDe(MANGA)).isEqualTo(EstadoServicio.CON_SERVICIO);

        // A las 18:10 tres vecinos dicen que sigue sin agua: el consenso lo pasa a SIN_SERVICIO.
        ahora.set(FIN.plusSeconds(600));
        assertThat(consenso.evaluar(MANGA).alcanzado()).isTrue();
        assertThat(sectores.estadoDe(MANGA)).isEqualTo(EstadoServicio.SIN_SERVICIO);

        // A las 18:11 corre el barrido otra vez: no hay nada nuevo de la fuente, no debe pisar a los vecinos.
        ahora.set(FIN.plusSeconds(660));
        barrido.aplicarVentanasVencidas();

        assertThat(sectores.estadoDe(MANGA)).isEqualTo(EstadoServicio.SIN_SERVICIO);
    }

    /** Repositorio de sectores mínimo: guardar y compare-and-set de verdad, para que los escritores se pisen. */
    private static final class SectoresEnMemoria implements SectorRepository {
        private final Map<SectorId, Sector> almacen = new LinkedHashMap<>();

        SectoresEnMemoria(Sector... iniciales) {
            for (Sector sector : iniciales) {
                almacen.put(sector.id(), sector);
            }
        }

        EstadoServicio estadoDe(SectorId id) {
            return almacen.get(id).estadoActual();
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
            almacen.put(id, new Sector(id, actual.nombre(), actual.poblacion(), nuevo, null, null, marcas));
            return true;
        }

        @Override
        public boolean confirmarEstado(SectorId id, EstadoServicio estado) {
            Sector actual = almacen.get(id);
            return actual != null && actual.estadoActual() == estado;
        }
    }
}
