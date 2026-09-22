package com.aguavigia.ctg.infrastructure.persistence.mongo;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface DocumentoFallidoMongoRepository extends MongoRepository<DocumentoFallidoDocumento, String> {

    /** Los 200 más recientes primero — mismo tope que las demás colas paginadas del veedor. */
    List<DocumentoFallidoDocumento> findTop200ByOrderByUltimoIntentoDesc();
}
