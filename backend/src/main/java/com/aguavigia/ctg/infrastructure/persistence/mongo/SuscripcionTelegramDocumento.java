package com.aguavigia.ctg.infrastructure.persistence.mongo;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/** RF041 — una fila por chat de Telegram; el id de la fila es el id del chat. La baja borra la fila (RNF009). */
@Getter
@Setter
@Document(collection = "suscripciones_telegram")
public class SuscripcionTelegramDocumento {

    @Id
    private String id;

    /** Cada aviso de un sector busca por aquí: sin índice recorrería todos los chats. */
    @Indexed
    private List<String> sectorIds;

    private Instant creadaEn;
}
