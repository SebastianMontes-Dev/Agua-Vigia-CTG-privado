package com.aguavigia.ctg.compartido.config;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Nombre de clase que Spring Data guarda en el campo {@code _class} de los documentos de este modelo.
 *
 * La reducción por funcionalidad (ADR-098) mueve los {@code @Document} de paquete, y sin esto el valor de {@code _class} de los
 * documentos nuevos cambiaría con el nombre de la clase: la base ya no sería la misma (requisito 6 y ADR-099). Cada {@code @Document}
 * que se mueva lleva aquí el nombre completo que tenía antes de moverse; {@link MapeadorDeClaseGuardada} lo escribe.
 * Al leer no hace falta nada: un {@code _class} que ya no nombra una clase existente se lee como el tipo declarado.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ClaseGuardadaComo {

    /** El nombre completo de la clase de antes de moverla, tal como está en los documentos ya guardados. */
    String value();
}
