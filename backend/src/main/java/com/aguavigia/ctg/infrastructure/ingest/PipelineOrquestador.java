package com.aguavigia.ctg.infrastructure.ingest;

import com.aguavigia.ctg.domain.AvisoDeIngesta;
import com.aguavigia.ctg.domain.EstadoServicio;
import com.aguavigia.ctg.domain.Sector;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.port.in.RegistrarPropuestaIngestaUseCase;
import com.aguavigia.ctg.domain.port.out.RelojPort;
import com.aguavigia.ctg.domain.port.out.SectorRepository;
import com.aguavigia.ctg.infrastructure.persistence.mongo.DocumentoFallidoDocumento;
import com.aguavigia.ctg.infrastructure.persistence.mongo.DocumentoFallidoMongoRepository;
import com.aguavigia.ctg.infrastructure.persistence.mongo.MarcaDeIngestaDocumento;
import com.aguavigia.ctg.infrastructure.persistence.mongo.MarcaDeIngestaMongoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.aguavigia.ctg.infrastructure.scheduling.EjecucionUnica;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Orquestador principal del pipeline de Ingesta Automatizada (M9).
 *
 * **No decide qué se publica: siempre entrega a `RegistrarPropuestaIngestaUseCase`**, que separa lo
 * oficial de lo inferido. Un boletín de Acuacar sale al mapa en el acto; una nota de prensa queda
 * PENDIENTE para el veedor. Lo que este orquestador nunca vuelve a hacer es llamar a
 * `SectorRepository.guardar()` directamente, que es lo que permitía que una expresión regular sobre
 * prensa cambiara el estado público de un barrio y disparara correo, push y SSE sin revisión.
 *
 * Cada colector se llama por separado y con su propio try/catch: son sitios de terceros
 * independientes y uno caído no puede impedir que se lea el otro (RNF004). `RssCollector` ya aislaba
 * feed por feed; lo que faltaba era el mismo aislamiento a nivel de fuente.
 */
@Service
public class PipelineOrquestador {

    private static final Logger log = LoggerFactory.getLogger(PipelineOrquestador.class);

    /**
     * Desde dónde lee un colector que nunca ha corrido. Acuacar publica desde mayo de 2020, así que
     * la primera ejecución se trae el histórico completo (317 boletines el 30/08/2026): sin él, las
     * estadísticas y el Índice de Cumplimiento solo tendrían lo publicado desde que alguien encendió
     * el sistema, que no es una medición de la ciudad sino de nuestro tiempo de actividad.
     */
    private static final Instant ORIGEN = Instant.parse("2020-01-01T00:00:00Z");

    /**
     * Cuánto se relee hacia atrás por encima de la marca. La API de WordPress filtra por fecha de
     * publicación, y un boletín editado después de publicarse no cambia de fecha: sin este solape,
     * una corrección publicada un minuto antes de la marca no se volvería a mirar nunca.
     */
    private static final Duration SOLAPE = Duration.ofDays(2);

    private final AcuacarApiCollector acuacarApiCollector;
    private final RssCollector rssCollector;
    /**
     * Existe con `aguavigia.ingesta.modo=local` (ADR-082), donde sustituye a Acuacar y a la prensa, y con `auto`, donde es
     * solo el respaldo de Acuacar en vivo (`esRespaldo()`).
     */
    private final Optional<ColectorLocalDeBoletines> colectorLocal;
    /** Existe con `aguavigia.ingesta.modo=simulacion` (D38): sustituye a Acuacar y a la prensa con lo que el simulador le entrega. */
    private final Optional<ColectorSimulado> colectorSimulado;
    private final DeduplicadorReciente deduplicador;
    private final HeuristicaExtractor extractor;
    private final SectorRepository sectorRepository;
    private final RegistrarPropuestaIngestaUseCase registrarPropuesta;
    private final EstadoColectorRegistry estadoColectores;
    private final MarcaDeIngestaMongoRepository marcas;
    private final DocumentoFallidoMongoRepository fallidos;
    private final RelojPort reloj;
    private final EjecucionUnica ejecucionUnica;

    public PipelineOrquestador(AcuacarApiCollector acuacarApiCollector,
                               RssCollector rssCollector,
                               Optional<ColectorLocalDeBoletines> colectorLocal,
                               DeduplicadorReciente deduplicador,
                               HeuristicaExtractor extractor,
                               SectorRepository sectorRepository,
                               RegistrarPropuestaIngestaUseCase registrarPropuesta,
                               EstadoColectorRegistry estadoColectores,
                               MarcaDeIngestaMongoRepository marcas,
                               DocumentoFallidoMongoRepository fallidos,
                               RelojPort reloj,
                               EjecucionUnica ejecucionUnica) {
        this(acuacarApiCollector, rssCollector, colectorLocal, deduplicador, extractor, sectorRepository, registrarPropuesta,
                estadoColectores, marcas, fallidos, reloj, ejecucionUnica, Optional.empty());
    }

    @Autowired
    public PipelineOrquestador(AcuacarApiCollector acuacarApiCollector,
                               RssCollector rssCollector,
                               Optional<ColectorLocalDeBoletines> colectorLocal,
                               DeduplicadorReciente deduplicador,
                               HeuristicaExtractor extractor,
                               SectorRepository sectorRepository,
                               RegistrarPropuestaIngestaUseCase registrarPropuesta,
                               EstadoColectorRegistry estadoColectores,
                               MarcaDeIngestaMongoRepository marcas,
                               DocumentoFallidoMongoRepository fallidos,
                               RelojPort reloj,
                               EjecucionUnica ejecucionUnica,
                               Optional<ColectorSimulado> colectorSimulado) {
        this.colectorSimulado = colectorSimulado;
        this.acuacarApiCollector = acuacarApiCollector;
        this.rssCollector = rssCollector;
        this.colectorLocal = colectorLocal;
        this.deduplicador = deduplicador;
        this.extractor = extractor;
        this.sectorRepository = sectorRepository;
        this.registrarPropuesta = registrarPropuesta;
        this.estadoColectores = estadoColectores;
        this.marcas = marcas;
        this.fallidos = fallidos;
        this.reloj = reloj;
        this.ejecucionUnica = ejecucionUnica;
    }

    /** Una sola réplica por ciclo: con varias, cada una ingería y duplicaba propuestas. Ver {@link EjecucionUnica}. */
    @Scheduled(initialDelayString = "${aguavigia.ingesta.retraso-inicial-ms:60000}",
            fixedDelayString = "${aguavigia.ingesta.intervalo-ms:600000}")
    public void ejecutarCicloEnUnaReplica() {
        ejecucionUnica.ejecutar("ingesta", Duration.ofMinutes(9), Duration.ofMinutes(5), this::ejecutarCiclo);
    }

    /**
     * Cada fuente arranca donde quedó, no en los últimos N días. Una ventana rodante daba por
     * perdido todo lo publicado mientras el sistema estaba apagado más tiempo que la ventana:
     * medido el 30/08/2026, el boletín más reciente de Acuacar tenía 9 días y la ventana era de 7,
     * así que el ciclo no veía absolutamente nada y los boletines de corte de julio y agosto nunca
     * se ingirieron.
     */
    // synchronized: en la simulación el ciclo también lo dispara cada boletín inyectado, y dos ciclos a la vez procesarían un mismo
    // documento dos veces antes de que el deduplicador lo vea. Entre réplicas manda EjecucionUnica; esto cubre la misma JVM.
    public synchronized void ejecutarCiclo() {
        // Modo local o simulación: sus boletines ocupan el lugar de Acuacar y no se toca la red ni la prensa.
        boolean sustituyeALaRed = colectorSimulado.isPresent()
                || colectorLocal.filter(local -> !local.esRespaldo()).isPresent();
        Optional<ColectorLocalDeBoletines> respaldo = colectorLocal.filter(ColectorLocalDeBoletines::esRespaldo);
        FuenteDatosPort sustituto = colectorSimulado.<FuenteDatosPort>map(c -> c).orElseGet(() -> colectorLocal.orElse(null));

        Lectura deAcuacar = sustituyeALaRed
                ? new Lectura(recolectar("acuacar", () -> sustituto.obtenerDesde(desdeDondeLeer("acuacar"))), false)
                : recolectarConRespaldo("acuacar", () -> acuacarApiCollector.obtenerDesde(desdeDondeLeer("acuacar")), respaldo);
        List<DocumentoCrudo> deRss = sustituyeALaRed
                ? List.of()
                : recolectar("rss", () -> rssCollector.obtenerDesde(desdeDondeLeer("rss")));

        List<DocumentoCrudo> documentos = new ArrayList<>();
        documentos.addAll(deAcuacar.documentos());
        documentos.addAll(deRss);

        // listarTodos() una sola vez por ciclo: son 213 barrios y antes se pedia dentro del bucle,
        // una vez por documento que pasara el prefiltro.
        List<Sector> sectores = sectorRepository.listarTodos();
        if (sectores.isEmpty()) {
            // El backend arranca y corre su primer ciclo antes de que el sembrador cargue los barrios. Sin catálogo ningún nombre
            // se reconoce: procesar descartaría todo y avanzar la marca perdería el histórico para siempre.
            log.warn("Ingesta: todavía no hay barrios sembrados; no se procesa nada y la marca no avanza. Se reintenta en el próximo ciclo.");
            return;
        }

        EmparejadorDeSectores emparejador = new EmparejadorDeSectores(sectores);
        for (DocumentoCrudo documento : documentos) {
            if (deduplicador.yaVistoRecientemente(documento.hash())) {
                continue;
            }
            if (!PrefiltroDeterminista.posibleInterrupcionDeAcueducto(documento.texto())) {
                continue;
            }
            procesar(documento, emparejador);
        }

        // Lo leído del respaldo no avanza la marca: al volver Acuacar en vivo hay que leer todo lo que quedó sin leer, no
        // solo lo posterior a los pocos boletines guardados.
        if (!deAcuacar.deRespaldo()) {
            avanzarMarca("acuacar", deAcuacar.documentos());
        }
        avanzarMarca("rss", deRss);
    }

    /**
     * Dónde retomar la lectura de una fuente. El solape hacia atrás es deliberado: reprocesar unos
     * pocos boletines no cuesta nada porque el deduplicador los descarta por hash, mientras que
     * saltarse uno lo pierde para siempre.
     */
    private Instant desdeDondeLeer(String fuente) {
        return marcas.findById(fuente)
                .map(MarcaDeIngestaDocumento::getUltimoPublicadoEn)
                .map(marca -> marca.minus(SOLAPE))
                .orElse(ORIGEN);
    }

    /**
     * La marca se guarda con el nombre del **colector**, no con el de `DocumentoCrudo.fuente()`: en
     * el RSS cada feed se identifica por su medio (`zona-cero`, `caracol-radio`), así que agrupar
     * por el campo del documento escribía marcas que `desdeDondeLeer("rss")` nunca encontraba y el
     * colector volvía al origen en cada ciclo.
     *
     * Avanza al más reciente que se llegó a *recolectar*, no al que produjo una propuesta: un
     * boletín que no habla de cortes también está leído. Un colector que falló devuelve lista vacía,
     * así que su marca no se mueve y el próximo ciclo reintenta desde el mismo punto.
     */
    private void avanzarMarca(String colector, List<DocumentoCrudo> documentos) {
        documentos.stream()
                .map(DocumentoCrudo::publicadoEn)
                .filter(java.util.Objects::nonNull)
                .max(java.util.Comparator.naturalOrder())
                .ifPresent(masReciente -> {
                    marcas.save(new MarcaDeIngestaDocumento(colector, masReciente));
                    log.info("Marca de ingesta de '{}' avanzada a {}", colector, masReciente);
                });
    }

    private interface Colector {
        List<DocumentoCrudo> obtener();
    }

    private record Lectura(List<DocumentoCrudo> documentos, boolean deRespaldo) {
    }

    /**
     * Acuacar en vivo; si falla y hay respaldo local (modo auto), sus boletines reales ocupan el lugar de esta lectura.
     * El fallo en vivo queda registrado igual que sin respaldo: el panel debe decir que la fuente no responde.
     */
    private Lectura recolectarConRespaldo(String nombre, Colector enVivo, Optional<ColectorLocalDeBoletines> respaldo) {
        try {
            List<DocumentoCrudo> documentos = enVivo.obtener();
            estadoColectores.registrarExito(nombre, documentos.size());
            return new Lectura(documentos, false);
        } catch (Exception fallo) {
            estadoColectores.registrarFallo(nombre, fallo.toString());
            if (respaldo.isEmpty()) {
                log.warn("El colector '{}' falló en este ciclo, se sigue con el resto: {}", nombre, fallo.toString());
                return new Lectura(List.of(), false);
            }
            log.warn("El colector '{}' falló en este ciclo, se usan los boletines reales guardados: {}", nombre, fallo.toString());
            return new Lectura(respaldo.get().obtenerDesde(ORIGEN), true);
        }
    }

    /**
     * Un colector caído devuelve lista vacía en vez de tumbar el ciclo. Antes, un 5xx de
     * acuacar.com —o un COLLECTOR_USER_AGENT sin configurar— lanzaba antes de que el RSS se
     * llegara a leer.
     *
     * El resultado se registra en `EstadoColectorRegistry` pase lo que pase: RNF007 pide saber
     * cuándo fue la última ejecución exitosa de cada colector, y eso no se puede reconstruir
     * después si el fallo se tragó en silencio.
     */
    private List<DocumentoCrudo> recolectar(String nombre, Colector colector) {
        try {
            List<DocumentoCrudo> documentos = colector.obtener();
            estadoColectores.registrarExito(nombre, documentos.size());
            return documentos;
        } catch (Exception fallo) {
            log.warn("El colector '{}' falló en este ciclo, se sigue con el resto: {}", nombre, fallo.toString());
            estadoColectores.registrarFallo(nombre, fallo.toString());
            return List.of();
        }
    }

    /**
     * Se marca como visto **después** de registrar la propuesta, no antes: si el registro falla, el
     * documento debe poder reintentarse en el siguiente ciclo. Marcarlo primero lo dejaba mudo
     * durante los 7 días de la ventana del deduplicador, que es el descarte silencioso que RNF006
     * prohíbe.
     */
    private void procesar(DocumentoCrudo documento, EmparejadorDeSectores emparejador) {
        try {
            // Un boletín con varias zonas trae un horario por zona: cada una es un aviso con su ventana y sus barrios.
            List<EventoExtraido> zonas = extractor.extraerPorZonas(documento).stream()
                    .filter(EventoExtraido::esInterrupcionDeAcueducto).toList();
            List<EmparejadorDeSectores.Resultado> emparejadas =
                    zonas.stream().map(zona -> emparejador.emparejar(zona.sectoresMencionados())).toList();
            // El tope de «demasiados barrios» es del boletín, no de la zona: se suman todos, sin repetir.
            int sectoresDelBoletin = (int) emparejadas.stream().flatMap(r -> r.sectores().stream()).distinct().count();
            for (int i = 0; i < zonas.size(); i++) {
                registrarZona(documento, zonas.get(i), emparejadas.get(i), sectoresDelBoletin);
            }
            deduplicador.marcarComoVisto(documento.hash());
            // Puede haber quedado en `documentos_fallidos` de un intento anterior; ya no está roto.
            fallidos.deleteById(documento.hash());
        } catch (Exception fallo) {
            log.warn("Documento de '{}' no procesado, se reintentará en el próximo ciclo: {}",
                    documento.fuente(), fallo.toString());
            registrarFallo(documento, fallo);
        }
    }

    private void registrarZona(DocumentoCrudo documento, EventoExtraido evento,
                               EmparejadorDeSectores.Resultado emparejados, int sectoresDelBoletin) {

        // RNF006: lo que la fuente nombra y el catálogo no reconoce se deja anotado. Antes
        // desaparecía sin rastro, y con ello la única señal de que al GeoJSON le faltan barrios.
        if (!emparejados.noReconocidos().isEmpty()) {
            log.info("Ingesta de '{}': {} nombre(s) sin sector en el catálogo: {}",
                    documento.fuente(), emparejados.noReconocidos().size(), emparejados.noReconocidos());
        }
        if ("AVISO_DE_ANULACION".equals(evento.tipo())) {
            log.info("Ingesta de '{}': aviso de aplazamiento o cancelación sobre {} sector(es); no es un corte nuevo",
                    documento.fuente(), emparejados.sectores().size());
            return;
        }

        if (emparejados.sectores().isEmpty()) {
            return;
        }
        if (!emparejados.ambiguos().isEmpty()) {
            log.warn("Ingesta de '{}': nombre(s) ambiguo(s), casan con más de un barrio y no se asignan: {}",
                    documento.fuente(), emparejados.ambiguos());
        }

        // El aviso entero, no sector por sector: «demasiados barrios» o «un nombre ambiguo» solo se ven con la lista completa.
        registrarPropuesta.registrarAviso(new AvisoDeIngesta(emparejados.sectores(),
                aEstadoServicio(evento, reloj.ahora()), documento.fuente(), documento.urlOriginal(),
                evento.citaTextual(), evento.confianza(), evento.inicioDeclarado(), evento.finPrometido(),
                documento.imagenUrl(), documento.publicadoEn(), documento.titulo(), !emparejados.ambiguos().isEmpty(),
                sectoresDelBoletin));
    }

    /**
     * RNF006 — cola muerta: un documento que falla se anota con su motivo, no solo en el log. Se
     * hace `upsert` por hash: el mismo documento roto reintentado cada ciclo actualiza su propia
     * fila (con el contador de reintentos) en vez de acumular una fila nueva por ciclo para siempre.
     */
    private void registrarFallo(DocumentoCrudo documento, Exception fallo) {
        try {
            Instant ahora = reloj.ahora();
            DocumentoFallidoDocumento existente = fallidos.findById(documento.hash()).orElse(null);
            Instant primerIntento = existente != null ? existente.getPrimerIntento() : ahora;
            int reintentos = existente != null ? existente.getReintentos() + 1 : 1;
            fallidos.save(new DocumentoFallidoDocumento(documento.hash(), documento.fuente(),
                    documento.urlOriginal(), documento.titulo(), fallo.toString(),
                    primerIntento, ahora, reintentos));
        } catch (Exception errorAlRegistrar) {
            // No debe impedir que el ciclo siga con el resto de documentos.
            log.warn("No se pudo dejar constancia del fallo de '{}' en la cola muerta: {}",
                    documento.fuente(), errorAlRegistrar.toString());
        }
    }

    /**
     * Una suspensión anunciada para mañana es un CORTE_PROGRAMADO, no un SIN_SERVICIO: antes
     * cualquier aviso caía en el `default` y el mapa pintaba de rojo barrios que en ese momento
     * tenían agua. La distinción sale de la ventana que el boletín declara; si no la declara, se
     * mantiene el estado del tipo de evento en vez de suponer un horario.
     */
    private static EstadoServicio aEstadoServicio(EventoExtraido evento, Instant ahora) {
        return switch (evento.tipo()) {
            case "PRESION_BAJA" -> EstadoServicio.PRESION_BAJA;
            case "SERVICIO_NORMAL" -> EstadoServicio.CON_SERVICIO;
            default -> {
                Instant inicio = evento.inicioDeclarado();
                Instant fin = evento.finPrometido();
                if (inicio != null && ahora.isBefore(inicio)) {
                    yield EstadoServicio.CORTE_PROGRAMADO;
                }
                // Con la ventana ya terminada sigue siendo un corte (SIN_SERVICIO), no un restablecimiento: que el servicio
                // volvió lo dice un boletín de restablecimiento, no el fin de una promesa. Si ya es historia, la
                // aprobación lo guarda como EXPIRADO; si no, el resolutor lo deja «por confirmar».
                yield EstadoServicio.SIN_SERVICIO;
            }
        };
    }
}
