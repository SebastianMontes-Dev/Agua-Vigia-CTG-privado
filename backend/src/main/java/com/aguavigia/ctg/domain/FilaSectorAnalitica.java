package com.aguavigia.ctg.domain;

import java.util.Objects;

/** Un barrio tal como se copia a la base analítica: solo su identidad, su nombre y su población (nulable, ver {@link Sector}). */
public record FilaSectorAnalitica(String id, String nombre, Integer poblacion) {

    public FilaSectorAnalitica {
        Objects.requireNonNull(id, "El barrio debe tener id");
        Objects.requireNonNull(nombre, "El barrio debe tener nombre");
    }
}
