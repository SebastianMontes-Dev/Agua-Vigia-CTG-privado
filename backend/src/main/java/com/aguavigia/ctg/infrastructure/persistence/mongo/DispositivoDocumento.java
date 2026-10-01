package com.aguavigia.ctg.infrastructure.persistence.mongo;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Getter
@Setter
@Document(collection = "dispositivos")
public class DispositivoDocumento {

    @Id
    private String id;
    private Instant creadoEn;
    private Instant ultimoVisto;
}
