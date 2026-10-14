package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.CorteAgua;
import com.aguavigia.ctg.domain.CorteId;
import com.aguavigia.ctg.domain.EntidadNoEncontradaException;
import com.aguavigia.ctg.domain.EstadoCorte;
import com.aguavigia.ctg.domain.EstadoServicio;
import com.aguavigia.ctg.domain.EventoBitacora;
import com.aguavigia.ctg.domain.EventoId;
import com.aguavigia.ctg.domain.OrigenCorte;
import com.aguavigia.ctg.domain.Pagina;
import com.aguavigia.ctg.domain.ReporteId;
import com.aguavigia.ctg.domain.Sector;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.TipoEvento;
import com.aguavigia.ctg.domain.port.out.CorteAguaRepository;
import com.aguavigia.ctg.domain.port.out.EventoBitacoraRepository;
import com.aguavigia.ctg.domain.port.out.SectorRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Los tres casos de uso de consulta pública que solo se ejercitaban a través de los controladores. */
class ConsultasPublicasServiceTest {

    private static final Instant BASE = Instant.parse("2026-08-01T12:00:00Z");
    private static final SectorId MANGA = new SectorId("manga");

    private static Sector sector(String id, EstadoServicio estado) {
        return new Sector(new SectorId(id), id.toUpperCase(), 1000, estado);
    }

    private static CorteAgua corte(String id, int dias) {
        Instant inicio = BASE.plus(dias, ChronoUnit.DAYS);
        return CorteAgua.builder()
                .id(new CorteId(id))
                .sectoresAfectados(List.of(MANGA))
                .inicio(inicio)
                .finPrometido(inicio.plus(6, ChronoUnit.HOURS))
                .causa("Mantenimiento")
                .origen(OrigenCorte.VEEDOR)
                .estado(EstadoCorte.RESTABLECIDO)
                .finReal(inicio.plus(5, ChronoUnit.HOURS))
                .build();
    }

    // ── ConsultarHistorialDeCortesService ───────────────────────────────────────────────────────────

    @Test
    void elHistorialDebeIrDelCorteMasRecienteAlMasAntiguo() {
        SectorRepository sectores = mock(SectorRepository.class);
        CorteAguaRepository cortes = mock(CorteAguaRepository.class);
        given(sectores.buscarPorId(MANGA)).willReturn(Optional.of(sector("manga", null)));
        given(cortes.listarPorSector(MANGA)).willReturn(List.of(corte("c-viejo", 0), corte("c-nuevo", 20), corte("c-medio", 10)));

        Pagina<CorteAgua> pagina = new ConsultarHistorialDeCortesService(sectores, cortes).listar(MANGA, 0, 10);

        assertThat(pagina.contenido()).extracting(c -> c.id().valor()).containsExactly("c-nuevo", "c-medio", "c-viejo");
        assertThat(pagina.totalElementos()).isEqualTo(3);
    }

    @Test
    void elHistorialDeUnSectorInexistenteDebeSerNoEncontradoSinConsultarCortes() {
        SectorRepository sectores = mock(SectorRepository.class);
        CorteAguaRepository cortes = mock(CorteAguaRepository.class);
        given(sectores.buscarPorId(any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> new ConsultarHistorialDeCortesService(sectores, cortes).listar(new SectorId("nada"), 0, 10))
                .isInstanceOf(EntidadNoEncontradaException.class)
                .hasMessageContaining("nada");
        verify(cortes, never()).listarPorSector(any());
    }

    @Test
    void elHistorialDebePaginarYCorregirUnaPaginaOTamanoInvalidos() {
        SectorRepository sectores = mock(SectorRepository.class);
        CorteAguaRepository cortes = mock(CorteAguaRepository.class);
        given(sectores.buscarPorId(MANGA)).willReturn(Optional.of(sector("manga", null)));
        given(cortes.listarPorSector(MANGA)).willReturn(IntStream.range(0, 5).mapToObj(i -> corte("c-" + i, i)).toList());
        var servicio = new ConsultarHistorialDeCortesService(sectores, cortes);

        Pagina<CorteAgua> segunda = servicio.listar(MANGA, 1, 2);
        assertThat(segunda.contenido()).extracting(c -> c.id().valor()).containsExactly("c-2", "c-1");

        Pagina<CorteAgua> corregida = servicio.listar(MANGA, -3, 0);
        assertThat(corregida.pagina()).isZero();
        assertThat(corregida.tamano()).isPositive();
    }

    // ── ConsultarSustentoDeEventoService ────────────────────────────────────────────────────────────

    private static EventoBitacora eventoConSustento(String id, int reportes) {
        return new EventoBitacora(new EventoId(id), TipoEvento.CORTE_CONFIRMADO_POR_CIUDADANOS, MANGA, null, BASE,
                "Consenso de vecinos", EstadoServicio.SIN_SERVICIO, null, null,
                IntStream.range(0, reportes).mapToObj(i -> new ReporteId("r-" + i)).toList());
    }

    @Test
    void elSustentoDebeDevolverLosReportesQueSostuvieronElCambio() {
        EventoBitacoraRepository eventos = mock(EventoBitacoraRepository.class);
        given(eventos.buscarPorId(new EventoId("e-1"))).willReturn(Optional.of(eventoConSustento("e-1", 3)));

        Pagina<ReporteId> pagina = new ConsultarSustentoDeEventoService(eventos).sustento(new EventoId("e-1"), 0, 10);

        assertThat(pagina.contenido()).extracting(ReporteId::valor).containsExactly("r-0", "r-1", "r-2");
        assertThat(pagina.totalElementos()).isEqualTo(3);
    }

    @Test
    void elSustentoDebePaginarLosReportes() {
        EventoBitacoraRepository eventos = mock(EventoBitacoraRepository.class);
        given(eventos.buscarPorId(new EventoId("e-1"))).willReturn(Optional.of(eventoConSustento("e-1", 5)));

        Pagina<ReporteId> pagina = new ConsultarSustentoDeEventoService(eventos).sustento(new EventoId("e-1"), 2, 2);

        assertThat(pagina.contenido()).extracting(ReporteId::valor).containsExactly("r-4");
        assertThat(pagina.totalElementos()).isEqualTo(5);
    }

    @Test
    void unEventoSinConsensoNoDebeTenerReportesDeSustento() {
        EventoBitacoraRepository eventos = mock(EventoBitacoraRepository.class);
        given(eventos.buscarPorId(new EventoId("e-2"))).willReturn(Optional.of(
                new EventoBitacora(new EventoId("e-2"), TipoEvento.CORTE_ANUNCIADO, MANGA, null, BASE, "Anunciado por Acuacar")));

        assertThat(new ConsultarSustentoDeEventoService(eventos).sustento(new EventoId("e-2"), 0, 10).contenido()).isEmpty();
    }

    @Test
    void elSustentoDeUnEventoInexistenteDebeSerNoEncontrado() {
        EventoBitacoraRepository eventos = mock(EventoBitacoraRepository.class);
        given(eventos.buscarPorId(any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> new ConsultarSustentoDeEventoService(eventos).sustento(new EventoId("no-existe"), 0, 10))
                .isInstanceOf(EntidadNoEncontradaException.class)
                .hasMessageContaining("no-existe");
    }

    // ── ListarSectoresAfectadosService ──────────────────────────────────────────────────────────────

    /** «Afectado» es tener un estado conocido distinto de CON_SERVICIO: sin dato no se afirma nada (ADR-014). */
    @Test
    void soloDebeListarLosSectoresConUnEstadoConocidoDistintoDeConServicio() {
        SectorRepository sectores = mock(SectorRepository.class);
        given(sectores.listarTodos()).willReturn(List.of(
                sector("manga", EstadoServicio.SIN_SERVICIO),
                sector("bocagrande", EstadoServicio.CON_SERVICIO),
                sector("crespo", null),
                sector("getsemani", EstadoServicio.PRESION_BAJA),
                sector("el-pozon", EstadoServicio.CORTE_PROGRAMADO)));

        List<Sector> afectados = new ListarSectoresAfectadosService(sectores).listar();

        assertThat(afectados).extracting(s -> s.id().valor()).containsExactly("manga", "getsemani", "el-pozon");
    }

    @Test
    void sinSectoresAfectadosDebeDevolverUnaListaVacia() {
        SectorRepository sectores = mock(SectorRepository.class);
        given(sectores.listarTodos()).willReturn(List.of(sector("manga", EstadoServicio.CON_SERVICIO), sector("crespo", null)));

        assertThat(new ListarSectoresAfectadosService(sectores).listar()).isEmpty();
    }
}
