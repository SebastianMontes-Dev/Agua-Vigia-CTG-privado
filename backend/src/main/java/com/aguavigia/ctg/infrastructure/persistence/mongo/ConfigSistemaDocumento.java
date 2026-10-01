package com.aguavigia.ctg.infrastructure.persistence.mongo;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@Setter
@Document(collection = "config_sistema")
public class ConfigSistemaDocumento {

    /** El nombre del dato: «dispositivos» para el secreto de los tokens de dispositivo. */
    @Id
    private String id;
    private String valor;
}
