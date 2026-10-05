package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.AvisoDeIngesta;
import com.aguavigia.ctg.domain.CompuertaDePublicacion;
import com.aguavigia.ctg.domain.EstadoRevision;
import com.aguavigia.ctg.domain.EstadoServicio;
import com.aguavigia.ctg.domain.PropuestaId;
import com.aguavigia.ctg.domain.PropuestaIngesta;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.port.in.RegistrarPropuestaIngestaUseCase;
import com.aguavigia.ctg.domain.port.in.RevisarPropuestaIngestaUseCase;
import com.aguavigia.ctg.domain.port.out.PropuestaIngestaRepository;
import com.aguavigia.ctg.domain.port.out.RelojPort;
import com.aguavigia.ctg.domain.port.out.SectorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * M9 — recibe lo que detecta el pipeline y decide si va a la cola del veedor o al mapa.
 *
 * Lo que viene de Acuacar se publica solo <b>únicamente si pasa las compuertas</b> ({@link CompuertaDePublicacion}, D5):
 * una extracción poco fiable o sin sentido espera al veedor con el motivo. Lo que viene de prensa espera siempre
 * ({@link PropuestaIngesta#esDeFuenteOficial}). Publicar reusa {@link RevisarPropuestaIngestaUseCase} en vez de tocar el
 * sector aquí: así el camino automático y el del veedor son el mismo código —misma guarda de estado repetido, mismo
 * evento de bitácora, mismos correos y SSE— y no pueden divergir con el tiempo.
 *
 * Las compuertas se deciden por <b>aviso</b> (una zona de un boletín con todos sus sectores), no sector por sector:
 * «el aviso toca 90 barrios» solo se ve con la lista entera, y publicar la mitad de un aviso dudoso sería peor que
 * encolarlo entero.
 */
public class RegistrarPropuestaIngestaService implements RegistrarPropuestaIngestaUseCase {

    private static final Logger log = LoggerFactory.getLogger(RegistrarPropuestaIngestaService.class);

    private static final String MOTIVO_DE_PRENSA = "Fuente de prensa: nada se publica sin que un veedor lo revise";

    private final PropuestaIngestaRepository propuestas;
    private final SectorRepository sectores;
    private final RevisarPropuestaIngestaUseCase revisar;
    private final RelojPort reloj;
    private final CompuertaDePublicacion compuerta;

    public RegistrarPropuestaIngestaService(PropuestaIngestaRepository propuestas,
                                             SectorRepository sectores,
                                             RevisarPropuestaIngestaUseCase revisar,
                                             RelojPort reloj,
                                             CompuertaDePublicacion compuerta) {
        this.propuestas = propuestas;
        this.sectores = sectores;
        this.revisar = revisar;
        this.reloj = reloj;
        this.compuerta = compuerta;
    }

    @Override
    public Optional<PropuestaIngesta> registrar(SectorId sectorId, EstadoServicio estadoPropuesto,
                                                 String fuente, String urlOriginal, String citaTextual,
                                                 double confianza, Instant inicioDeclarado,
                                                 Instant finPrometido, String imagenUrl,
                                                 Instant publicadoEn, String tituloOriginal) {
        List<PropuestaIngesta> registradas = registrarAviso(new AvisoDeIngesta(List.of(sectorId), estadoPropuesto,
                fuente, urlOriginal, citaTextual, confianza, inicioDeclarado, finPrometido, imagenUrl, publicadoEn,
                tituloOriginal, false));
        return registradas.stream().findFirst();
    }

    @Override
    public List<PropuestaIngesta> registrarAviso(AvisoDeIngesta aviso) {
        List<String> motivos = motivosParaRevisar(aviso);
        String motivo = motivos.isEmpty() ? null : String.join("; ", motivos);

        List<PropuestaIngesta> registradas = new ArrayList<>();
        for (SectorId sectorId : aviso.sectores()) {
            registrarSector(aviso, sectorId, motivo).ifPresent(registradas::add);
        }
        return List.copyOf(registradas);
    }

    private List<String> motivosParaRevisar(AvisoDeIngesta aviso) {
        List<String> motivos = new ArrayList<>();
        if (!esDeFuenteOficial(aviso)) {
            motivos.add(MOTIVO_DE_PRENSA);
            return motivos;
        }
        motivos.addAll(compuerta.motivosParaRevisar(aviso.estadoPropuesto(), aviso.confianza(),
                aviso.inicioDeclarado(), aviso.finPrometido(), aviso.publicadoEn(),
                Math.max(aviso.sectores().size(), aviso.sectoresDelBoletin()), aviso.aliasAmbiguo(), aviso.citaTextual()));
        return motivos;
    }

    private Optional<PropuestaIngesta> registrarSector(AvisoDeIngesta aviso, SectorId sectorId, String motivoDeRevision) {
        // Un nombre extraido de una nota de prensa no tiene por que ser un barrio de Cartagena.
        // Se descarta en silencio (log a nivel debug) porque es el caso normal, no una anomalia.
        if (sectores.buscarPorId(sectorId).isEmpty()) {
            log.debug("Propuesta ignorada: el sector '{}' no existe", sectorId.valor());
            return Optional.empty();
        }

        if (aviso.urlOriginal() != null && propuestas.existeDelBoletin(
                sectorId, aviso.urlOriginal(), aviso.estadoPropuesto(), aviso.inicioDeclarado())) {
            // Idempotente: releer un boletín (respaldo local, Redis vaciado, solape de la marca) no duplica propuestas ni bitácora.
            log.debug("Propuesta ignorada: el boletín {} ya se registró para '{}'", aviso.urlOriginal(), sectorId.valor());
            return Optional.empty();
        }

        if (propuestas.existePendiente(sectorId, aviso.estadoPropuesto())) {
            // Info y no debug: un aviso dudoso en cola bloquea a uno posterior del mismo barrio hasta que alguien decida (RNF006).
            log.info("Propuesta no registrada: ya hay una pendiente de {} para '{}' esperando al veedor ({})",
                    aviso.estadoPropuesto(), sectorId.valor(), aviso.urlOriginal());
            return Optional.empty();
        }

        PropuestaIngesta propuesta = new PropuestaIngesta(
                new PropuestaId(UUID.randomUUID().toString()),
                sectorId,
                aviso.estadoPropuesto(),
                aviso.fuente(),
                aviso.urlOriginal(),
                aviso.citaTextual(),
                aviso.confianza(),
                reloj.ahora(),
                EstadoRevision.PENDIENTE,
                aviso.inicioDeclarado(),
                aviso.finPrometido(),
                aviso.imagenUrl(),
                aviso.publicadoEn(),
                aviso.tituloOriginal(),
                null,
                motivoDeRevision);

        PropuestaIngesta guardada = propuestas.guardar(propuesta);

        if (motivoDeRevision == null) {
            log.info("Propuesta oficial publicada sin revisión: {} en '{}' (fuente: {})",
                    aviso.estadoPropuesto(), sectorId.valor(), aviso.fuente());
            return Optional.of(revisar.aprobar(guardada.id()));
        }

        log.info("Propuesta de ingesta encolada para revisión: {} en '{}' (fuente: {}): {}",
                aviso.estadoPropuesto(), sectorId.valor(), aviso.fuente(), motivoDeRevision);
        return Optional.of(guardada);
    }

    private static boolean esDeFuenteOficial(AvisoDeIngesta aviso) {
        return PropuestaIngesta.FUENTE_OFICIAL.equalsIgnoreCase(aviso.fuente());
    }
}
