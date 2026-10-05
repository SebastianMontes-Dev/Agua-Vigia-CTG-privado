package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.AvisoDeIngesta;
import com.aguavigia.ctg.domain.CompuertaDePublicacion;
import com.aguavigia.ctg.domain.EstadoRevision;
import com.aguavigia.ctg.domain.EstadoServicio;
import com.aguavigia.ctg.domain.PropuestaId;
import com.aguavigia.ctg.domain.PropuestaIngesta;
import com.aguavigia.ctg.domain.Sector;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.port.in.RevisarPropuestaIngestaUseCase;
import com.aguavigia.ctg.domain.port.out.PropuestaIngestaRepository;
import com.aguavigia.ctg.domain.port.out.RelojPort;
import com.aguavigia.ctg.domain.port.out.SectorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class RegistrarPropuestaIngestaServiceTest {

    private static final Instant AHORA = Instant.parse("2026-08-09T15:30:00Z");
    private static final Instant INICIO = AHORA.plus(Duration.ofHours(18));
    private static final Instant FIN = INICIO.plus(Duration.ofHours(9));
    private static final SectorId MANGA = new SectorId("manga");

    private PropuestaIngestaRepository propuestas;
    private SectorRepository sectores;
    private RevisarPropuestaIngestaUseCase revisar;
    private RegistrarPropuestaIngestaService servicio;

    @BeforeEach
    void montar() {
        propuestas = mock(PropuestaIngestaRepository.class);
        sectores = mock(SectorRepository.class);
        revisar = mock(RevisarPropuestaIngestaUseCase.class);
        RelojPort reloj = () -> AHORA;
        servicio = new RegistrarPropuestaIngestaService(propuestas, sectores, revisar, reloj,
                CompuertaDePublicacion.porDefecto());

        given(propuestas.guardar(any())).willAnswer(invocacion -> invocacion.getArgument(0));
        given(sectores.buscarPorId(any())).willAnswer(invocacion ->
                Optional.of(new Sector(invocacion.getArgument(0), "Sector", 1000, EstadoServicio.CON_SERVICIO)));
        given(revisar.aprobar(any())).willAnswer(invocacion -> PROPUESTA_APROBADA);
    }

    private static final PropuestaIngesta PROPUESTA_APROBADA = new PropuestaIngesta(
            new PropuestaId("aprobada"), MANGA, EstadoServicio.SIN_SERVICIO,
            "acuacar", "https://acuacar.com/x", "cita del boletin", 0.85, AHORA,
            EstadoRevision.APROBADA, null, null);

    private Optional<PropuestaIngesta> registrar(String fuente, double confianza) {
        return servicio.registrar(MANGA, EstadoServicio.SIN_SERVICIO, fuente,
                "https://acuacar.com/x", "cita del boletin", confianza, null, null, null, null, null);
    }

    private Optional<PropuestaIngesta> registrar(String fuente) {
        return registrar(fuente, 0.6);
    }

    private Optional<PropuestaIngesta> registrar() {
        return registrar("rss");
    }

    private static List<SectorId> sectores(int cuantos) {
        return IntStream.range(0, cuantos).mapToObj(i -> new SectorId("barrio-" + i)).toList();
    }

    private AvisoDeIngesta aviso(List<SectorId> sectoresDelAviso, double confianza, Instant inicio, Instant fin,
                                 boolean aliasAmbiguo) {
        return new AvisoDeIngesta(sectoresDelAviso, EstadoServicio.CORTE_PROGRAMADO, "acuacar",
                "https://acuacar.com/x", "cita del boletin", confianza, inicio, fin, null, AHORA, "titular", aliasAmbiguo);
    }

    @Test
    void debeEncolarComoPendienteLoQueVieneDePrensa() {
        Optional<PropuestaIngesta> resultado = registrar("rss");

        assertThat(resultado).isPresent();
        assertThat(resultado.get().estadoRevision()).isEqualTo(EstadoRevision.PENDIENTE);
        assertThat(resultado.get().sectorId()).isEqualTo(MANGA);
        assertThat(resultado.get().estadoPropuesto()).isEqualTo(EstadoServicio.SIN_SERVICIO);
        assertThat(resultado.get().citaTextual()).isEqualTo("cita del boletin");
        assertThat(resultado.get().detectadaEn()).isEqualTo(AHORA);
        assertThat(resultado.get().motivoDeRevision()).containsIgnoringCase("prensa");
        verify(revisar, never()).aprobar(any());
    }

    /**
     * Acuacar es el operador: su boletín fiable se publica sin esperar al veedor. Se delega en el mismo
     * caso de uso que usa el panel para que el camino automático no pueda divergir del manual.
     */
    @Test
    void debePublicarSinRevisionUnBoletinFiableDeAcuacar() {
        Optional<PropuestaIngesta> resultado = registrar("acuacar", 0.85);

        assertThat(resultado).isPresent();
        assertThat(resultado.get().estadoRevision()).isEqualTo(EstadoRevision.APROBADA);
        verify(revisar).aprobar(any());
    }

    /** Hallazgo 5 del plan: un boletín de 0,45 —una mención suelta— se publicaba solo, sin mirar la confianza. */
    @Test
    void unBoletinDeAcuacarDeBajaConfianzaDebeEsperarAlVeedorConSuMotivo() {
        Optional<PropuestaIngesta> resultado = registrar("acuacar", 0.45);

        assertThat(resultado).isPresent();
        assertThat(resultado.get().estadoRevision()).isEqualTo(EstadoRevision.PENDIENTE);
        assertThat(resultado.get().motivoDeRevision()).contains("confianza");
        verify(revisar, never()).aprobar(any());
    }

    /** Un nombre extraído de una nota de prensa no tiene por qué ser un barrio de Cartagena. */
    @Test
    void noDebeRegistrarNadaSiElSectorNoExiste() {
        given(sectores.buscarPorId(MANGA)).willReturn(Optional.empty());

        assertThat(registrar()).isEmpty();
        verify(propuestas, never()).guardar(any());
    }

    /**
     * Los cuatro feeds cubren las mismas noticias con hashes distintos, así que el deduplicador por
     * documento no las une: sin este chequeo, un solo corte le deja al veedor cuatro propuestas
     * idénticas.
     */
    @Test
    void noDebeDuplicarUnaPropuestaPendienteIdentica() {
        given(propuestas.existePendiente(MANGA, EstadoServicio.SIN_SERVICIO)).willReturn(true);

        assertThat(registrar()).isEmpty();
        verify(propuestas, never()).guardar(any());
    }

    /**
     * La marca de ingesta y el deduplicador de Redis (7 días) son las barreras de un boletín ya leído, y ninguna sobrevive a todo: un
     * Redis vaciado o el respaldo local releyendo el archivo desde 2020 lo vuelven a traer. Una propuesta aprobada, o anulada por el
     * veedor, tampoco bloquea a la pendiente: sin este chequeo cada pasada duplica propuestas y eventos de una bitácora de solo anexado.
     */
    @Test
    void unBoletinYaRegistradoNoSeVuelveARegistrarEnNingunEstadoDeRevision() {
        given(propuestas.existeDelBoletin(eq(MANGA), eq("https://acuacar.com/x"), eq(EstadoServicio.SIN_SERVICIO), any()))
                .willReturn(true);

        assertThat(registrar("acuacar", 0.95)).isEmpty();
        verify(propuestas, never()).guardar(any());
        verify(revisar, never()).aprobar(any());
    }

    /**
     * M3 de la auditoría: en modo `auto` el respaldo local entrega los mismos 13 boletines en cada ciclo mientras Acuacar no responde.
     * Dos pasadas seguidas con el mismo boletín (la segunda ve lo que la primera guardó) dejan una sola propuesta y una sola
     * publicación, que es lo que evita duplicar eventos en una bitácora de solo anexado. No hace falta derivar el id del evento del
     * boletín: la barrera está aquí, antes de crear nada, y los ciclos se serializan con `EjecucionUnica`.
     */
    @Test
    void dosCiclosSeguidosConElMismoBoletinDelRespaldoNoDuplicanNiPublicanDosVeces() {
        var yaGuardadas = new java.util.ArrayList<PropuestaIngesta>();
        given(propuestas.guardar(any())).willAnswer(invocacion -> {
            yaGuardadas.add(invocacion.getArgument(0));
            return invocacion.getArgument(0);
        });
        given(propuestas.existeDelBoletin(any(), any(), any(), any())).willAnswer(invocacion ->
                yaGuardadas.stream().anyMatch(p -> p.sectorId().equals(invocacion.getArgument(0))
                        && p.urlOriginal().equals(invocacion.getArgument(1))
                        && p.estadoPropuesto() == invocacion.getArgument(2)));

        var primera = registrar("acuacar", 0.95);
        var segunda = registrar("acuacar", 0.95);

        assertThat(primera).isPresent();
        assertThat(segunda).isEmpty();
        assertThat(yaGuardadas).hasSize(1);
        verify(revisar, times(1)).aprobar(any());
    }

    /** Publicar es cosa de {@link RevisarPropuestaIngestaUseCase}; este servicio nunca escribe el sector. */
    @Test
    void nuncaDebeTocarElEstadoDelSectorDirectamente() {
        registrar("rss");
        registrar("acuacar", 0.85);

        verify(sectores, never()).guardar(any());
    }

    // --- por zona (D5) ---

    @Test
    void unAvisoFiableYConSentidoPublicaTodosSusSectores() {
        List<PropuestaIngesta> registradas = servicio.registrarAviso(aviso(sectores(5), 0.85, INICIO, FIN, false));

        assertThat(registradas).hasSize(5);
        verify(revisar, times(5)).aprobar(any());
    }

    @Test
    void unAvisoConDemasiadosBarriosVaEntero_AlVeedor_ConElMotivo() {
        List<PropuestaIngesta> registradas = servicio.registrarAviso(aviso(sectores(41), 0.85, INICIO, FIN, false));

        assertThat(registradas).hasSize(41)
                .allSatisfy(p -> {
                    assertThat(p.estadoRevision()).isEqualTo(EstadoRevision.PENDIENTE);
                    assertThat(p.motivoDeRevision()).contains("41 barrios");
                });
        verify(revisar, never()).aprobar(any());
    }

    /** Seis zonas de 10 barrios cada una pasan la compuerta por zona, pero son 60 en el boletín. */
    @Test
    void elTopeDeBarriosCuentaElBoletinEnteroYNoSoloLaZona() {
        AvisoDeIngesta zona = new AvisoDeIngesta(sectores(10), EstadoServicio.CORTE_PROGRAMADO, "acuacar",
                "https://acuacar.com/x", "cita", 0.85, INICIO, FIN, null, AHORA, "titular", false, 60);

        List<PropuestaIngesta> registradas = servicio.registrarAviso(zona);

        assertThat(registradas).allSatisfy(p -> assertThat(p.motivoDeRevision()).contains("60 barrios"));
        verify(revisar, never()).aprobar(any());
    }

    @Test
    void unAvisoSinCitaVaAlVeedorAunqueSeaFiable() {
        AvisoDeIngesta sinCita = new AvisoDeIngesta(sectores(2), EstadoServicio.CORTE_PROGRAMADO, "acuacar",
                "https://acuacar.com/x", " ", 0.85, INICIO, FIN, null, AHORA, "titular", false);

        assertThat(servicio.registrarAviso(sinCita)).allSatisfy(p -> assertThat(p.motivoDeRevision()).contains("cita"));
        verify(revisar, never()).aprobar(any());
    }

    @Test
    void unAliasAmbiguoVaAlVeedor() {
        List<PropuestaIngesta> registradas = servicio.registrarAviso(aviso(sectores(3), 0.85, INICIO, FIN, true));

        assertThat(registradas).allSatisfy(p -> assertThat(p.motivoDeRevision()).contains("ambiguo"));
        verify(revisar, never()).aprobar(any());
    }

    @Test
    void unaVentanaDeMasDeSetentaYDosHorasVaAlVeedor() {
        List<PropuestaIngesta> registradas =
                servicio.registrarAviso(aviso(sectores(3), 0.85, INICIO, INICIO.plus(Duration.ofHours(100)), false));

        assertThat(registradas).allSatisfy(p -> assertThat(p.motivoDeRevision()).contains("72 horas"));
        verify(revisar, never()).aprobar(any());
    }

    /** Un boletín de prensa nunca se publica solo, aunque pase todas las compuertas. */
    @Test
    void laPrensaNuncaSePublicaSolaAunqueSeaFiable() {
        AvisoDeIngesta deprensa = new AvisoDeIngesta(sectores(2), EstadoServicio.CORTE_PROGRAMADO, "zona-cero",
                "https://prensa.com/x", "cita", 0.95, INICIO, FIN, null, AHORA, "titular", false);

        List<PropuestaIngesta> registradas = servicio.registrarAviso(deprensa);

        assertThat(registradas).hasSize(2);
        verify(revisar, never()).aprobar(any());
    }

    @Test
    void losSectoresQueNoExistenSeOmitenSinTumbarElAviso() {
        given(sectores.buscarPorId(new SectorId("barrio-1"))).willReturn(Optional.empty());

        List<PropuestaIngesta> registradas = servicio.registrarAviso(aviso(sectores(3), 0.85, INICIO, FIN, false));

        assertThat(registradas).hasSize(2);
    }
}
