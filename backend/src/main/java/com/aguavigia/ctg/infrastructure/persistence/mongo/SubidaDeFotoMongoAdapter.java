package com.aguavigia.ctg.infrastructure.persistence.mongo;

import com.aguavigia.ctg.domain.ReporteId;
import com.aguavigia.ctg.domain.port.out.SubidaDeFotoRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class SubidaDeFotoMongoAdapter implements SubidaDeFotoRepository {

    private final MongoTemplate mongoTemplate;

    public SubidaDeFotoMongoAdapter(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void guardar(ReporteId reporte, String hashDelToken, Instant venceEn) {
        mongoTemplate.remove(Query.query(Criteria.where("reporteId").is(reporte.valor())), SubidaDeFotoDocumento.class);
        SubidaDeFotoDocumento documento = new SubidaDeFotoDocumento();
        documento.setHashDelToken(hashDelToken);
        documento.setReporteId(reporte.valor());
        documento.setVenceEn(venceEn);
        mongoTemplate.save(documento);
    }

    @Override
    public boolean consumir(ReporteId reporte, String hashDelToken, Instant ahora) {
        Query vigente = Query.query(Criteria.where("_id").is(hashDelToken)
                .and("reporteId").is(reporte.valor())
                .and("venceEn").gte(ahora));
        return mongoTemplate.findAndRemove(vigente, SubidaDeFotoDocumento.class) != null;
    }
}
