package com.aguavigia.ctg.infrastructure.persistence.mongo;

import com.aguavigia.ctg.domain.Coordenada;
import com.aguavigia.ctg.domain.EstadoServicio;
import com.aguavigia.ctg.domain.Sector;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.port.out.RelojPort;
import com.aguavigia.ctg.domain.port.out.SectorRepository;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.data.geo.Point;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.aguavigia.ctg.application.SectorActualizadoEvent;
import java.util.ArrayList;

import java.util.List;
import java.util.Optional;

/**
 * Adaptador de SectorRepository sobre MongoDB.
 *
 * Un sector sin estado registrado se traduce a estadoActual == null, no a CON_SERVICIO
 * (ver ADR-014): afirmar que hay servicio sin haberlo verificado es exactamente el falso
 * positivo que MEMORY.md manda evitar.
 */
@Component
public class SectorMongoAdapter implements SectorRepository {

    private final SectorMongoRepository repositorio;
    private final MongoTemplate mongoTemplate;
    private final RelojPort reloj;
    private final ApplicationEventPublisher eventPublisher;
    private final CacheManager cacheManager;

    public SectorMongoAdapter(SectorMongoRepository repositorio, MongoTemplate mongoTemplate, RelojPort reloj,
                              ApplicationEventPublisher eventPublisher, CacheManager cacheManager) {
        this.repositorio = repositorio;
        this.mongoTemplate = mongoTemplate;
        this.reloj = reloj;
        this.eventPublisher = eventPublisher;
        this.cacheManager = cacheManager;
    }

    /**
     * Difiere {@code efecto} hasta que la transacción activa confirme (Fase 3 de
     * `plan-validacion-backend.md`): sin esto, un correo, push, SSE o invalidación de caché podían
     * anunciar un cambio que la transacción luego revertía. Fuera de una transacción (todavía hay
     * llamadas que no pasan por `TransaccionPort`), se ejecuta de inmediato — el comportamiento de
     * siempre.
     */
    private void trasConfirmar(Runnable efecto) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    efecto.run();
                }
            });
        } else {
            efecto.run();
        }
    }

    private void invalidarCache() {
        Cache cache = cacheManager.getCache("sectores");
        if (cache != null) {
            cache.clear();
        }
    }

    @Override
    public Optional<Sector> buscarPorId(SectorId id) {
        return repositorio.leerSinGeometriaPorSlug(id.valor()).map(SectorMongoAdapter::aDominio);
    }

    /**
     * RF007. `$geoIntersects` sobre el índice `2dsphere` de `geometry`; sirve igual para Polygon y
     * MultiPolygon. Se excluye `geometry` de la proyección: el polígono de un barrio pesa kilobytes
     * y aquí solo hace falta saber cuál es.
     */
    @Override
    public Optional<Sector> buscarPorCoordenada(Coordenada coordenada) {
        // GeoJSON es (longitud, latitud); Coordenada es (latitud, longitud).
        Query consulta = Query.query(Criteria.where("geometry")
                        .intersects(new GeoJsonPoint(new Point(coordenada.longitud(), coordenada.latitud()))))
                .limit(1);
        consulta.fields().exclude("geometry");
        return Optional.ofNullable(mongoTemplate.findOne(consulta, SectorDocumento.class))
                .map(SectorMongoAdapter::aDominio);
    }

    /**
     * Se cachea la lista completa, no cada sector por separado: el mapa pide siempre los 213 de
     * golpe y una sola llave se invalida entera cuando el consenso mueve un estado.
     *
     * ArrayList y no el List inmutable de toList(): el serializador del cache escribe la clase
     * concreta del valor, y ImmutableCollections$ListN no se puede reconstruir al leerla de vuelta.
     */
    @Override
    // sync=true: al expirar la entrada, una sola peticion recalcula y las demas esperan, en vez de
    // que todas las que llegan en ese instante golpeen Mongo a la vez (estampida).
    @Cacheable(value = "sectores", sync = true)
    public List<Sector> listarTodos() {
        return new ArrayList<>(repositorio.listarSinGeometria(Sort.by(Sort.Direction.ASC, "nombre")).stream()
                .map(SectorMongoAdapter::aDominio)
                .toList());
    }

    /**
     * Invalida el cache aunque el estado no haya cambiado. Sin esto, un corte confirmado por
     * consenso tardaria hasta un TTL entero en verse en el mapa, que es justo la desinformacion
     * que el proyecto existe para evitar (DESIGN.md §6). Diferida a `trasConfirmar` (Fase 3): la
     * invalidación y el evento solo deben anunciarse si la transacción que envuelve este guardado
     * de verdad confirma.
     */
    @Override
    public Sector guardar(Sector sector) {
        // Se lee el documento existente en vez de construir uno nuevo: la geometria y los datos
        // censales los siembra el sembrador y este adaptador no los produce. Un save() sobre un documento
        // recien construido los borraria de los 213 barrios.
        SectorDocumento documento = repositorio.findBySlug(sector.id().valor())
                .orElseGet(() -> {
                    SectorDocumento nuevo = new SectorDocumento();
                    nuevo.setSlug(sector.id().valor());
                    return nuevo;
                });

        documento.setNombre(sector.nombre());
        documento.setPoblacion(sector.poblacion());

        boolean cambioElEstado = sector.estadoActual() != null
                && !sector.estadoActual().name().equals(documento.getEstadoActual());
        if (cambioElEstado) {
            documento.setEstadoActual(sector.estadoActual().name());
            documento.setEstadoActualizadoEn(reloj.ahora());
        }

        Sector guardado = aDominio(repositorio.save(documento));
        trasConfirmar(this::invalidarCache);
        if (cambioElEstado) {
            trasConfirmar(() -> eventPublisher.publishEvent(new SectorActualizadoEvent(guardado)));
        }
        return guardado;
    }

    /**
     * `findAndModify` con el estado esperado en el filtro: Mongo lo aplica de forma atomica, asi que
     * de dos llamadas simultaneas solo una encuentra el documento con el estado esperado.
     * `Criteria.is(null)` casa tambien con un documento sin el campo, que es un sector sin estado.
     */
    @Override
    public boolean cambiarEstadoSiEs(SectorId id, EstadoServicio esperado, EstadoServicio nuevo) {
        Query condicion = Query.query(Criteria.where("slug").is(id.valor())
                .and("estadoActual").is(esperado == null ? null : esperado.name()));
        Update cambio = new Update()
                .set("estadoActual", nuevo.name())
                .set("estadoActualizadoEn", reloj.ahora());

        SectorDocumento actualizado = mongoTemplate.findAndModify(
                condicion, cambio, FindAndModifyOptions.options().returnNew(true), SectorDocumento.class);
        if (actualizado == null) {
            return false;
        }
        trasConfirmar(this::invalidarCache);
        trasConfirmar(() -> eventPublisher.publishEvent(new SectorActualizadoEvent(aDominio(actualizado))));
        return true;
    }

    private static Sector aDominio(SectorDocumento documento) {
        EstadoServicio estado = aEstadoServicio(documento.getEstadoActual());
        return new Sector(
                new SectorId(documento.getSlug()),
                documento.getNombre(),
                documento.getPoblacion(),
                estado,
                // RF003: sin estado no hay fecha de estado. Un documento con
                // estadoActualizadoEn pero con estadoActual fuera del enum (ver aEstadoServicio)
                // caeria aqui con estado nulo, y una fecha suelta diria "actualizado hace 5 min"
                // sobre un dato que no sabemos leer.
                estado != null ? documento.getEstadoActualizadoEn() : null);
    }

    /**
     * Devuelve null —no un valor por defecto— cuando el sector no tiene estado o cuando el
     * guardado en base de datos ya no corresponde a ningun valor del enum. Un estado desconocido
     * se trata como ausencia de dato, no como servicio normal.
     */
    private static EstadoServicio aEstadoServicio(String valorGuardado) {
        if (valorGuardado == null || valorGuardado.isBlank()) {
            return null;
        }
        try {
            return EstadoServicio.valueOf(valorGuardado);
        } catch (IllegalArgumentException valorFueraDelEnum) {
            return null;
        }
    }
}
