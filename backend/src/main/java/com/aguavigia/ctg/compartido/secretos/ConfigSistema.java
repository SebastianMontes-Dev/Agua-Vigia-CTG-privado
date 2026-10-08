package com.aguavigia.ctg.compartido.secretos;

import com.aguavigia.ctg.compartido.config.ClaseGuardadaComo;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@Setter
@ClaseGuardadaComo("com.aguavigia.ctg.infrastructure.persistence.mongo.ConfigSistemaDocumento")
@Document(collection = "config_sistema")
public class ConfigSistema {

    /** El nombre del dato: «dispositivos» para el secreto de los tokens de dispositivo. */
    @Id
    private String id;
    private String valor;
}
