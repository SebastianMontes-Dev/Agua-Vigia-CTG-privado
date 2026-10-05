package com.aguavigia.ctg.infrastructure.ingest;

import com.aguavigia.ctg.domain.EstadoServicio;
import com.aguavigia.ctg.domain.Sector;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.port.in.RegistrarPropuestaIngestaUseCase;
import com.aguavigia.ctg.domain.port.out.RelojPort;
import com.aguavigia.ctg.domain.port.out.SectorRepository;
import com.aguavigia.ctg.infrastructure.persistence.mongo.DocumentoFallidoDocumento;
import com.aguavigia.ctg.infrastructure.persistence.mongo.DocumentoFallidoMongoRepository;
import com.aguavigia.ctg.infrastructure.persistence.mongo.MarcaDeIngestaMongoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class PipelineOrquestadorTest {

    private static final Instant AHORA = Instant.parse("2026-08-09T15:30:00Z");

    private AcuacarApiCollector acuacar;
    private RssCollector rss;
    private DeduplicadorReciente deduplicador;
    private HeuristicaExtractor extractor;
    private SectorRepository sectores;
    private RegistrarPropuestaIngestaUseCase registrarPropuesta;
    private EstadoColectorRegistry estadoColectores;
    private MarcaDeIngestaMongoRepository marcas;
    private DocumentoFallidoMongoRepository fallidos;
    private RelojPort reloj;

    private PipelineOrquestador orquestador;

    @BeforeEach
    void montar() {
        acuacar = mock(AcuacarApiCollector.class);
        rss = mock(RssCollector.class);
        deduplicador = mock(DeduplicadorReciente.class);
        extractor = mock(HeuristicaExtractor.class);
        sectores = mock(SectorRepository.class);
        registrarPropuesta = mock(RegistrarPropuestaIngestaUseCase.class);
        reloj = mock(RelojPort.class);
        marcas = mock(MarcaDeIngestaMongoRepository.class);
        fallidos = mock(DocumentoFallidoMongoRepository.class);
        // Real y no mock: es un contador en memoria sin dependencias, y así el test puede
        // comprobar de verdad lo que RNF007 exige reportar.
        estadoColectores = new EstadoColectorRegistry(() -> AHORA);

        given(marcas.findById(anyString())).willReturn(Optional.empty());
        given(fallidos.findById(anyString())).willReturn(Optional.empty());

        given(reloj.ahora()).willReturn(AHORA);
        given(acuacar.obtenerDesde(any())).willReturn(List.of());
        given(rss.obtenerDesde(any())).willReturn(List.of());
        given(deduplicador.yaVistoRecientemente(any())).willReturn(false);
        given(registrarPropuesta.registrarAviso(any())).willReturn(List.of());

        orquestador = new PipelineOrquestador(acuacar, rss, Optional.empty(), deduplicador, extractor, sectores,
                registrarPropuesta, estadoColectores, marcas, fallidos, reloj, (nombre, maximo, minimo, tarea) -> tarea.run());
    }

    private DocumentoCrudo documento(String texto) {
        return DocumentoCrudo.de("acuacar", "https://acuacar.com/x", AHORA, "Titulo", texto);
    }

    private EventoExtraido eventoParaSectores(List<String> sectoresMencionados) {
        return new EventoExtraido(true, "SUSPENSION_PROGRAMADA", sectoresMencionados,
                null, null, "daño", 0.6, List.of(), "cita del boletin");
    }

    private void hayUnDocumentoSobre(String texto, List<String> mencionados, List<Sector> sembrados) {
        given(acuacar.obtenerDesde(any())).willReturn(List.of(documento(texto)));
        given(extractor.extraerPorZonas(any())).willReturn(List.of(eventoParaSectores(mencionados)));
        given(sectores.listarTodos()).willReturn(sembrados);
    }

    /**
     * Un boletín de suspensión cuya ventana ya terminó (el caso del histórico) no es un restablecimiento: proponerlo como
     * CON_SERVICIO lo sacaba de la rama de historia y lo trataba como «servicio normal» fechado en la publicación.
     */
    @Test
    void unaSuspensionCuyaVentanaYaTerminoSeProponeComoCorteYNoComoServicioNormal() {
        given(acuacar.obtenerDesde(any())).willReturn(List.of(documento("Corte en Manga por daño en la red")));
        given(extractor.extraerPorZonas(any())).willReturn(List.of(new EventoExtraido(true, "SUSPENSION_PROGRAMADA",
                List.of("Manga"), AHORA.minusSeconds(40 * 3600), AHORA.minusSeconds(31 * 3600), "daño", 0.85, List.of(),
                "cita del boletin")));
        given(sectores.listarTodos()).willReturn(
                List.of(new Sector(new SectorId("manga"), "Manga", 1000, EstadoServicio.CON_SERVICIO)));

        orquestador.ejecutarCiclo();

        verify(registrarPropuesta).registrarAviso(argThat(aviso ->
                aviso.estadoPropuesto() == EstadoServicio.SIN_SERVICIO));
    }

    /** Un boletín de varias zonas se cuenta entero para la compuerta de «demasiados barrios». */
    @Test
    void elAvisoDeCadaZonaLlevaElTotalDeSectoresDelBoletinEntero() {
        given(acuacar.obtenerDesde(any())).willReturn(List.of(documento("Corte en Manga y Bocagrande por daño en la red")));
        given(extractor.extraerPorZonas(any())).willReturn(List.of(
                eventoParaSectores(List.of("Manga")), eventoParaSectores(List.of("Bocagrande"))));
        given(sectores.listarTodos()).willReturn(List.of(
                new Sector(new SectorId("manga"), "Manga", 1000, EstadoServicio.CON_SERVICIO),
                new Sector(new SectorId("bocagrande"), "Bocagrande", 1000, EstadoServicio.CON_SERVICIO)));

        orquestador.ejecutarCiclo();

        verify(registrarPropuesta, org.mockito.Mockito.times(2)).registrarAviso(argThat(aviso ->
                aviso.sectores().size() == 1 && aviso.sectoresDelBoletin() == 2));
    }

    // --- Lo esencial del rediseño: la ingesta propone, no publica ---

    @Test
    void nuncaDebeTocarElEstadoDeUnSectorPorSiMisma() {
        hayUnDocumentoSobre("Corte en Manga por daño en la red", List.of("Manga"),
                List.of(new Sector(new SectorId("manga"), "Manga", 1000, EstadoServicio.CON_SERVICIO)));

        orquestador.ejecutarCiclo();

        // Publicar es decisión del veedor (RevisarPropuestaIngestaUseCase), no de una regex.
        verify(sectores, never()).guardar(any());
    }

    @Test
    void debeRegistrarUnaPropuestaCuandoElNombreNormalizadoCoincideExactamente() {
        hayUnDocumentoSobre("Corte en Manga por daño en la red", List.of("Manga"),
                List.of(new Sector(new SectorId("manga"), "Manga", 1000, EstadoServicio.CON_SERVICIO)));

        orquestador.ejecutarCiclo();

        // La zona entera, con sus sectores a la vista: es sobre ella que se deciden las compuertas.
        verify(registrarPropuesta).registrarAviso(argThat(aviso ->
                aviso.sectores().equals(List.of(new SectorId("manga")))
                        && aviso.estadoPropuesto() == EstadoServicio.SIN_SERVICIO
                        && "acuacar".equals(aviso.fuente())
                        && "https://acuacar.com/x".equals(aviso.urlOriginal())
                        && "cita del boletin".equals(aviso.citaTextual())
                        && aviso.confianza() == 0.6
                        && !aviso.aliasAmbiguo()));
    }

    @Test
    void noDebeProponerParaUnSectorCuyoNombreSoloContieneLaMencionComoSubstring() {
        // "Manga" es substring de "Mangaville" — el emparejamiento laxo anterior podía pintar
        // decenas de barrios con un solo artículo.
        hayUnDocumentoSobre("Corte en Manga por daño en la red", List.of("Manga"),
                List.of(new Sector(new SectorId("mangaville"), "Mangaville", 500, EstadoServicio.CON_SERVICIO)));

        orquestador.ejecutarCiclo();

        verify(registrarPropuesta, never()).registrarAviso(any());
    }

    // --- Modo local, sin internet (ADR-082) ---

    @Test
    void enModoLocalNoDebeTocarLaRedAunqueAcuacarEstuvieraCaido() {
        ColectorLocalDeBoletines local = mock(ColectorLocalDeBoletines.class);
        given(local.obtenerDesde(any())).willReturn(List.of(documento("Corte en Manga por daño en la red")));
        given(extractor.extraerPorZonas(any())).willReturn(List.of(eventoParaSectores(List.of("Manga"))));
        given(sectores.listarTodos()).willReturn(
                List.of(new Sector(new SectorId("manga"), "Manga", 1000, EstadoServicio.CON_SERVICIO)));
        var orquestadorLocal = new PipelineOrquestador(acuacar, rss, Optional.of(local), deduplicador, extractor,
                sectores, registrarPropuesta, estadoColectores, marcas, fallidos, reloj,
                (nombre, maximo, minimo, tarea) -> tarea.run());

        orquestadorLocal.ejecutarCiclo();

        verify(acuacar, never()).obtenerDesde(any());
        verify(rss, never()).obtenerDesde(any());
        verify(registrarPropuesta).registrarAviso(argThat(aviso ->
                aviso.sectores().contains(new SectorId("manga")) && "acuacar".equals(aviso.fuente())));
        assertThat(estadoColectores.hayAlgunColectorCaido()).isFalse();
        assertThat(estadoColectores.estados()).extracting(EstadoColector::nombre).containsExactly("acuacar");
    }

    // --- Sin barrios sembrados no se lee nada ---

    /**
     * Con un solo `docker compose up` el backend arranca y ejecuta su primer ciclo antes de que el sembrador cargue los barrios. Con
     * el catálogo vacío ningún nombre se reconoce, así que todo boletín se descartaba y la marca avanzaba: el histórico completo se
     * perdía para siempre. Sin barrios, el ciclo no procesa nada y no mueve la marca; el próximo lo reintenta desde el mismo punto.
     */
    @Test
    void sinSectoresSembradosElCicloNoProcesaNadaNiAvanzaLaMarca() {
        given(acuacar.obtenerDesde(any())).willReturn(List.of(documento("Corte en Manga por daño en la red")));
        given(sectores.listarTodos()).willReturn(List.of());

        orquestador.ejecutarCiclo();

        verify(registrarPropuesta, never()).registrarAviso(any());
        verify(deduplicador, never()).marcarComoVisto(any());
        verify(marcas, never()).save(any());
    }

    /**
     * El primer ciclo no corre al instante: con un solo `docker compose up` los barrios los carga el sembrador unos segundos después
     * de que el backend arranque, y el ciclo sin barrios no hace nada hasta el siguiente (10 minutos).
     */
    @Test
    void elPrimerCicloEsperaUnRatoParaQueElSembradorCarguelosBarrios() throws Exception {
        org.springframework.scheduling.annotation.Scheduled programada = PipelineOrquestador.class
                .getMethod("ejecutarCicloEnUnaReplica").getAnnotation(org.springframework.scheduling.annotation.Scheduled.class);

        assertThat(programada.initialDelayString()).contains("aguavigia.ingesta.retraso-inicial-ms");
    }

    // --- Modo auto: en vivo, con respaldo a los boletines locales ---

    private PipelineOrquestador orquestadorAuto(ColectorLocalDeBoletines respaldo) {
        given(respaldo.esRespaldo()).willReturn(true);
        return new PipelineOrquestador(acuacar, rss, Optional.of(respaldo), deduplicador, extractor,
                sectores, registrarPropuesta, estadoColectores, marcas, fallidos, reloj,
                (nombre, maximo, minimo, tarea) -> tarea.run());
    }

    @Test
    void enModoAutoConAcuacarCaidoSeUsanLosBoletinesLocalesYElFalloQuedaVisible() {
        ColectorLocalDeBoletines local = mock(ColectorLocalDeBoletines.class);
        given(local.obtenerDesde(any())).willReturn(List.of(documento("Corte en Manga por daño en la red")));
        given(acuacar.obtenerDesde(any())).willThrow(new IllegalStateException("acuacar.com responde 503"));
        given(extractor.extraerPorZonas(any())).willReturn(List.of(eventoParaSectores(List.of("Manga"))));
        given(sectores.listarTodos()).willReturn(
                List.of(new Sector(new SectorId("manga"), "Manga", 1000, EstadoServicio.CON_SERVICIO)));

        orquestadorAuto(local).ejecutarCiclo();

        verify(registrarPropuesta).registrarAviso(argThat(aviso -> aviso.sectores().contains(new SectorId("manga"))));
        // El panel debe seguir diciendo que Acuacar en vivo no responde: el respaldo no esconde la caída.
        assertThat(estadoColectores.estados())
                .filteredOn(e -> e.nombre().equals("acuacar")).extracting(EstadoColector::fallosConsecutivos)
                .containsExactly(1);
    }

    /** Si la marca avanzara con los 13 boletines locales, al volver Acuacar solo se leería lo posterior y se perdería el histórico. */
    @Test
    void enModoAutoElRespaldoNoAvanzaLaMarcaDeAcuacar() {
        ColectorLocalDeBoletines local = mock(ColectorLocalDeBoletines.class);
        given(local.obtenerDesde(any())).willReturn(List.of(documento("Corte en Manga por daño en la red")));
        given(acuacar.obtenerDesde(any())).willThrow(new IllegalStateException("sin red"));
        given(extractor.extraerPorZonas(any())).willReturn(List.of());
        given(sectores.listarTodos()).willReturn(List.of());

        orquestadorAuto(local).ejecutarCiclo();

        verify(marcas, never()).save(argThat(marca -> "acuacar".equals(marca.getFuente())));
    }

    @Test
    void enModoAutoConAcuacarSanoNoSeTocanLosBoletinesLocales() {
        ColectorLocalDeBoletines local = mock(ColectorLocalDeBoletines.class);
        given(acuacar.obtenerDesde(any())).willReturn(List.of(documento("Corte en Manga por daño en la red")));
        given(extractor.extraerPorZonas(any())).willReturn(List.of());
        given(sectores.listarTodos()).willReturn(List.of());

        orquestadorAuto(local).ejecutarCiclo();

        verify(local, never()).obtenerDesde(any());
        verify(rss).obtenerDesde(any());
    }

    // --- Aislamiento de fallos (RNF004) ---

    @Test
    void unColectorCaidoNoDebeImpedirQueSeLeaElOtro() {
        given(acuacar.obtenerDesde(any())).willThrow(new IllegalStateException("acuacar.com responde 503"));
        given(rss.obtenerDesde(any())).willReturn(List.of(documento("Corte en Manga por daño en la red")));
        given(extractor.extraerPorZonas(any())).willReturn(List.of(eventoParaSectores(List.of("Manga"))));
        given(sectores.listarTodos()).willReturn(
                List.of(new Sector(new SectorId("manga"), "Manga", 1000, EstadoServicio.CON_SERVICIO)));

        orquestador.ejecutarCiclo();

        verify(registrarPropuesta).registrarAviso(argThat(aviso -> aviso.sectores().contains(new SectorId("manga"))));
    }

    @Test
    void losDosColectoresCaidosNoDebenTumbarElCiclo() {
        given(acuacar.obtenerDesde(any())).willThrow(new IllegalStateException("sin user agent"));
        given(rss.obtenerDesde(any())).willThrow(new IllegalStateException("sin user agent"));

        orquestador.ejecutarCiclo();

        verify(registrarPropuesta, never()).registrarAviso(any());
    }

    // --- Salud por colector (RNF007) ---

    @Test
    void debeRegistrarLaUltimaEjecucionExitosaYLosItemsDeCadaColector() {
        given(acuacar.obtenerDesde(any())).willReturn(List.of(documento("Corte en Manga")));
        given(rss.obtenerDesde(any())).willReturn(List.of());
        given(extractor.extraerPorZonas(any())).willReturn(List.of(eventoParaSectores(List.of())));
        given(sectores.listarTodos()).willReturn(List.of());

        orquestador.ejecutarCiclo();

        assertThat(estadoColectores.estados())
                .extracting(EstadoColector::nombre, EstadoColector::itemsProcesados,
                        EstadoColector::ultimaEjecucionExitosa, EstadoColector::fallosConsecutivos)
                .containsExactly(
                        tuple("acuacar", 1L, AHORA, 0),
                        tuple("rss", 0L, AHORA, 0));
    }

    @Test
    void debeRegistrarElFalloDeUnColectorConSuMotivo() {
        given(acuacar.obtenerDesde(any())).willThrow(new IllegalStateException("acuacar.com responde 503"));

        orquestador.ejecutarCiclo();

        EstadoColector acuacarEstado = estadoColectores.estados().getFirst();
        assertThat(acuacarEstado.nombre()).isEqualTo("acuacar");
        assertThat(acuacarEstado.fallosConsecutivos()).isEqualTo(1);
        assertThat(acuacarEstado.ultimaEjecucionExitosa()).isNull();
        assertThat(acuacarEstado.motivoDelUltimoFallo()).contains("503");
        assertThat(acuacarEstado.tasaDeError()).isEqualTo(1.0);
    }

    @Test
    void tresCiclosSeguidosFallandoDebenReportarElColectorComoCaido() {
        given(acuacar.obtenerDesde(any())).willThrow(new IllegalStateException("sin red"));

        orquestador.ejecutarCiclo();
        assertThat(estadoColectores.hayAlgunColectorCaido()).isFalse();
        orquestador.ejecutarCiclo();
        assertThat(estadoColectores.hayAlgunColectorCaido()).isFalse();
        orquestador.ejecutarCiclo();

        assertThat(estadoColectores.hayAlgunColectorCaido()).isTrue();
    }

    @Test
    void unCicloExitosoDebeLimpiarLaRachaDeFallos() {
        given(acuacar.obtenerDesde(any())).willThrow(new IllegalStateException("sin red"));
        orquestador.ejecutarCiclo();
        orquestador.ejecutarCiclo();
        orquestador.ejecutarCiclo();
        assertThat(estadoColectores.hayAlgunColectorCaido()).isTrue();

        org.mockito.Mockito.reset(acuacar);
        given(acuacar.obtenerDesde(any())).willReturn(List.of());
        orquestador.ejecutarCiclo();

        assertThat(estadoColectores.hayAlgunColectorCaido()).isFalse();
    }

    // --- Deduplicación (RNF006: cero descartes silenciosos) ---

    @Test
    void noDebeMarcarComoVistoUnDocumentoQueFalloAlProcesarse() {
        hayUnDocumentoSobre("Corte en Manga por daño en la red", List.of("Manga"),
                List.of(new Sector(new SectorId("manga"), "Manga", 1000, EstadoServicio.CON_SERVICIO)));
        given(registrarPropuesta.registrarAviso(any()))
                .willThrow(new RuntimeException("Mongo caído"));

        orquestador.ejecutarCiclo();

        // Marcarlo dejaría el documento mudo los 7 días de la ventana del deduplicador y nadie
        // lo reintentaría nunca.
        verify(deduplicador, never()).marcarComoVisto(anyString());
    }

    @Test
    void debeMarcarComoVistoUnDocumentoProcesadoConExito() {
        hayUnDocumentoSobre("Corte en Manga por daño en la red", List.of("Manga"),
                List.of(new Sector(new SectorId("manga"), "Manga", 1000, EstadoServicio.CON_SERVICIO)));

        orquestador.ejecutarCiclo();

        verify(deduplicador).marcarComoVisto(anyString());
    }

    // --- Cola muerta de fallidos (RNF006/BUG-091: el fallo deja rastro consultable, no solo el log) ---

    @Test
    void unDocumentoQueFallaDebeQuedarEnLaColaDeFallidosConSuMotivo() {
        hayUnDocumentoSobre("Corte en Manga por daño en la red", List.of("Manga"),
                List.of(new Sector(new SectorId("manga"), "Manga", 1000, EstadoServicio.CON_SERVICIO)));
        given(registrarPropuesta.registrarAviso(any()))
                .willThrow(new RuntimeException("Mongo caído"));

        orquestador.ejecutarCiclo();

        ArgumentCaptor<DocumentoFallidoDocumento> capturado = ArgumentCaptor.forClass(DocumentoFallidoDocumento.class);
        verify(fallidos).save(capturado.capture());
        assertThat(capturado.getValue().getFuente()).isEqualTo("acuacar");
        assertThat(capturado.getValue().getMotivo()).contains("Mongo caído");
        assertThat(capturado.getValue().getReintentos()).isEqualTo(1);
        assertThat(capturado.getValue().getPrimerIntento()).isEqualTo(AHORA);
        assertThat(capturado.getValue().getUltimoIntento()).isEqualTo(AHORA);
    }

    @Test
    void unDocumentoQueFallaVariasVecesDebeAcumularReintentosEnLaMismaFila() {
        hayUnDocumentoSobre("Corte en Manga por daño en la red", List.of("Manga"),
                List.of(new Sector(new SectorId("manga"), "Manga", 1000, EstadoServicio.CON_SERVICIO)));
        given(registrarPropuesta.registrarAviso(any()))
                .willThrow(new RuntimeException("Mongo caído"));
        String hash = documento("Corte en Manga por daño en la red").hash();
        given(fallidos.findById(hash)).willReturn(Optional.of(
                new DocumentoFallidoDocumento(hash, "acuacar", "https://acuacar.com/x", "Titulo",
                        "Mongo caído", AHORA, AHORA, 1)));

        orquestador.ejecutarCiclo();

        ArgumentCaptor<DocumentoFallidoDocumento> capturado = ArgumentCaptor.forClass(DocumentoFallidoDocumento.class);
        verify(fallidos).save(capturado.capture());
        assertThat(capturado.getValue().getReintentos()).isEqualTo(2);
        assertThat(capturado.getValue().getPrimerIntento()).isEqualTo(AHORA);
    }

    @Test
    void unDocumentoProcesadoConExitoDebeSalirDeLaColaDeFallidos() {
        hayUnDocumentoSobre("Corte en Manga por daño en la red", List.of("Manga"),
                List.of(new Sector(new SectorId("manga"), "Manga", 1000, EstadoServicio.CON_SERVICIO)));

        orquestador.ejecutarCiclo();

        // Puede haber quedado de un intento anterior; ya no está roto.
        verify(fallidos).deleteById(anyString());
        verify(fallidos, never()).save(any());
    }

    @Test
    void noDebeReprocesarUnDocumentoYaVisto() {
        hayUnDocumentoSobre("Corte en Manga por daño en la red", List.of("Manga"),
                List.of(new Sector(new SectorId("manga"), "Manga", 1000, EstadoServicio.CON_SERVICIO)));
        given(deduplicador.yaVistoRecientemente(anyString())).willReturn(true);

        orquestador.ejecutarCiclo();

        verify(registrarPropuesta, never()).registrarAviso(any());
    }

    @Test
    void noDebeProcesarUnDocumentoQueNoPasaElPrefiltro() {
        hayUnDocumentoSobre("La alcaldia inauguro un parque en el centro historico", List.of("Manga"),
                List.of(new Sector(new SectorId("manga"), "Manga", 1000, EstadoServicio.CON_SERVICIO)));

        orquestador.ejecutarCiclo();

        verify(registrarPropuesta, never()).registrarAviso(any());
        verify(deduplicador, never()).marcarComoVisto(anyString());
    }
}
