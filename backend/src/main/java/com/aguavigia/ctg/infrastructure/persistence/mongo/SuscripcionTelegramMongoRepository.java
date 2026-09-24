package com.aguavigia.ctg.infrastructure.persistence.mongo;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface SuscripcionTelegramMongoRepository extends MongoRepository<SuscripcionTelegramDocumento, String> {

    List<SuscripcionTelegramDocumento> findBySectorIdsContaining(String sectorId);
}
