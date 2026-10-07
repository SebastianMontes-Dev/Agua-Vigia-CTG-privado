package com.aguavigia.ctg.domain;

import java.time.Instant;

/**
 * Un boletín que la simulación hace llegar como si lo hubiera publicado Acuacar, con la forma de la API de WordPress que usa el
 * archivo real (id, fecha, enlace, título, contenido en HTML, portada). Pasa por la misma limpieza, deduplicación, prefiltro, extractor y
 * compuertas que uno en vivo (D38). {@code fecha} puede faltar: entonces vale la hora del reloj de la simulación.
 */
public record BoletinSimulado(long id, Instant fecha, String enlace, String titulo, String contenidoHtml, String portada) {

    public BoletinSimulado {
        if (titulo == null || titulo.isBlank()) {
            throw new IllegalArgumentException("El boletín simulado necesita un título");
        }
        if (contenidoHtml == null || contenidoHtml.isBlank()) {
            throw new IllegalArgumentException("El boletín simulado necesita contenido");
        }
    }

    public BoletinSimulado conFecha(Instant nueva) {
        return new BoletinSimulado(id, nueva, enlace, titulo, contenidoHtml, portada);
    }
}
