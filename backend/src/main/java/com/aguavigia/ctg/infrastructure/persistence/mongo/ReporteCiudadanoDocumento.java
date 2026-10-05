package com.aguavigia.ctg.infrastructure.persistence.mongo;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Getter
@Setter
@Document(collection = "reportes")
public class ReporteCiudadanoDocumento {

    @Id
    private String id;

    @Indexed
    private String sectorId;

    private String tipo;
    private Double latitud;
    private Double longitud;
    private String huella;

    @Indexed
    private Instant timestamp;

    /** RF018 (`ADR-023`) — PENDIENTE, APROBADO o DESCARTADO. Nulo en documentos sembrados antes de M5. */
    @Indexed
    private String estadoModeracion;

    private String fotoUrl;

    /** El reporte lo envió un sensor de la red por `/api/iot/presion`. Falso en los documentos anteriores al dato. */
    private boolean esSensor;

    /** D16 — CUENTA_VERIFICADA, UBICACION_VERIFICADA o NINGUNA. Nulo en los documentos anteriores, que se leen como NINGUNA. */
    private String verificacion;

    /** D16 — resumen diario de la red de origen. Nulo si no se conoce (sensores, documentos anteriores). */
    private String redHash;

    /** SHA-256 de la foto tal como se guardó. Nulo si no hay foto o es anterior al dato. */
    private String fotoSha256;

    /** El veedor descartó la foto sin descartar el reporte. Falso en los documentos anteriores. */
    private boolean fotoDescartada;

    private java.util.Set<String> huellasConfirmacion = new java.util.HashSet<>();
}
