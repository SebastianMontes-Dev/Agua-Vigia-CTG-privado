package com.aguavigia.ctg.domain;

/**
 * RF010 — patrón Strategy: al menos dos formas intercambiables de decidir si suficientes reportes
 * independientes justifican cambiar el estado de un sector. La estrategia activa se elige en
 * infrastructure/config/ConsensoConfig, no aquí — domain/ no sabe de configuración.
 */
public interface EstrategiaConsenso {

    /** Cuántos vecinos hacen falta en este sector. El resolutor lo necesita como número, no solo como sí o no. */
    long umbral(Sector sector);

    /** Una sola fuente de verdad: el «sí o no» se deriva del umbral, así que nunca pueden divergir. */
    default boolean seAlcanzaConsenso(long reportesRecientes, Sector sector) {
        return reportesRecientes >= umbral(sector);
    }
}
