package com.aguavigia.ctg.infrastructure.persistence.mongo;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** El hash del token es el `_id`: consumirlo es borrar el documento, que es atómico. */
@Getter
@Setter
@Document(collection = "subidas_foto")
public class SubidaDeFotoDocumento {

    @Id
    private String hashDelToken;

    @Indexed
    private String reporteId;

    /** Mongo borra el documento solo al llegar esta fecha (índice TTL de IndicesMongo). */
    private Instant venceEn;
}
