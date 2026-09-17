package com.aguavigia.ctg.infrastructure.persistence.mongo;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface CorteAguaMongoRepository extends MongoRepository<CorteAguaDocumento, String> {

    List<CorteAguaDocumento> findBySectoresAfectadosContaining(String sectorId);

    /**
     * `$in` sobre un campo arreglo hace match por elemento: trae todo corte cuyo
     * `sectoresAfectados` contenga AL MENOS UNO de los ids dados, en una sola consulta. Reemplaza
     * un `findBySectoresAfectadosContaining` por sector dentro de un `for` (GestionarCorteOficialService).
     */
    List<CorteAguaDocumento> findBySectoresAfectadosIn(List<String> sectorIds);
}
