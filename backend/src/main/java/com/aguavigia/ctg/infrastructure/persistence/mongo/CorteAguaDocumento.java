package com.aguavigia.ctg.infrastructure.persistence.mongo;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@Document(collection = "cortes")
public class CorteAguaDocumento {

    @Id
    private String id;

    @Indexed
    private List<String> sectoresAfectados;

    private Instant inicio;
    private Instant finPrometido;

    /**
     * Nulo mientras el corte no esté RESTABLECIDO. Es la hora del último cierre: se sigue guardando
     * porque las agregaciones del Índice la consultan directamente en Mongo. Los documentos anteriores
     * a los cierres por sector traen solo este campo, y el dominio los lee como cerrados en todos sus barrios.
     */
    private Instant finReal;

    private String causa;
    private String origen;
    private String estado;

    /** Un cierre por cada barrio ya restablecido. Nulo en los documentos anteriores a los cierres por sector. */
    private List<Cierre> cierres;

    /** Solo en un corte ANULADO. */
    private String motivoAnulacion;

    /** Solo en el corte del veedor: desde esta hora deja de afirmar nada. */
    private Instant caducaEn;

    /** Lista y no mapa: un id de sector como clave de documento obligaría a escapar puntos y símbolos. */
    @Getter
    @Setter
    public static class Cierre {
        private String sectorId;
        private Instant hora;
        private String fuente;
        private boolean provisional;
    }
}
