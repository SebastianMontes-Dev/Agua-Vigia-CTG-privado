package com.aguavigia.ctg.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Un corte tal como se copia a la base analítica, con una fila por cada barrio que afectó. Sin datos personales. */
public record FilaCorteAnalitica(String id, String origen, String estado, String causa, Instant inicio,
                                 Instant finPrometido, List<FilaParAnalitica> pares) {

    public FilaCorteAnalitica {
        Objects.requireNonNull(id, "El corte debe tener id");
        Objects.requireNonNull(origen, "El corte debe tener origen");
        Objects.requireNonNull(estado, "El corte debe tener estado");
        Objects.requireNonNull(inicio, "El corte debe tener inicio");
        Objects.requireNonNull(finPrometido, "El corte debe tener fin prometido");
        if (!finPrometido.isAfter(inicio)) {
            throw new IllegalArgumentException("El fin prometido debe ser posterior al inicio");
        }
        pares = List.copyOf(pares);
    }
}
