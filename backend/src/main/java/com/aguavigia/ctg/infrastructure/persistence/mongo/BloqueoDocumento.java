package com.aguavigia.ctg.infrastructure.persistence.mongo;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Documento de control compartido para {@link BloqueoDeAdministradoresMongoAdapter}. Un solo
 * documento (`_id` fijo) por bloqueo: quien logra fijar `expiraEn` en el futuro con un `token`
 * propio lo tiene; los demás, mientras siga vigente, no.
 */
@Getter
@Setter
@Document(collection = "bloqueos_administracion")
public class BloqueoDocumento {

    @Id
    private String id;

    private Instant expiraEn;

    private String token;
}
