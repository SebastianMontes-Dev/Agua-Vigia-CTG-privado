package com.aguavigia.ctg.infrastructure.persistence.mongo;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio de Spring Data. No sale de infrastructure/: quien lo consume es
 * SectorMongoAdapter, que es el que implementa el puerto de dominio.
 */
public interface SectorMongoRepository extends MongoRepository<SectorDocumento, String> {

    /**
     * Documento COMPLETO. Solo para `guardar()`, que lo relee y lo vuelve a escribir: si llegara sin
     * `geometry`, el `save()` la borraria de la base.
     */
    Optional<SectorDocumento> findBySlug(String slug);

    /**
     * Para las lecturas: el poligono de un barrio pesa kilobytes y el estado del servicio no lo
     * necesita. Se leia entero en cada POST /api/reportes y en cada recarga del listado. Lo que
     * devuelve NO debe pasarse a `save()`.
     */
    @Query(value = "{ 'slug': ?0 }", fields = "{ 'geometry': 0 }")
    Optional<SectorDocumento> leerSinGeometriaPorSlug(String slug);

    @Query(value = "{}", fields = "{ 'geometry': 0 }")
    List<SectorDocumento> listarSinGeometria(Sort orden);
}
