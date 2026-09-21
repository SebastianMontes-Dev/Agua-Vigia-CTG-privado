package com.aguavigia.ctg.infrastructure.persistence.mongo;

import com.aguavigia.ctg.domain.GeometriaSector;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.port.out.GeometriaSectoresPort;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Lee `geometry` como `org.bson.Document` crudo (ver SectorDocumento): un tipo fijo fallaría con
 * el único MultiPolygon. Se proyecta solo lo necesario; el resto del documento no se transfiere.
 */
@Component
public class GeometriaSectoresMongoAdapter implements GeometriaSectoresPort {

    private final MongoTemplate mongoTemplate;

    public GeometriaSectoresMongoAdapter(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public List<GeometriaSector> listar() {
        Query consulta = Query.query(Criteria.where("geometry").ne(null))
                .with(Sort.by(Sort.Direction.ASC, "nombre"));
        consulta.fields().include("slug", "nombre", "geometry");

        return mongoTemplate.find(consulta, SectorDocumento.class).stream()
                .map(documento -> new GeometriaSector(
                        new SectorId(documento.getSlug()), documento.getNombre(), documento.getGeometry()))
                .toList();
    }
}
