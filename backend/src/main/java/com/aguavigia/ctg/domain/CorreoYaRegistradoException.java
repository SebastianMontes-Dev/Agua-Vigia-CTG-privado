package com.aguavigia.ctg.domain;

/**
 * Otro alta con el mismo correo se coló entre la comprobación y el guardado. La garantía real de
 * unicidad es el índice único de la base; esta excepción es cómo el dominio se entera de que perdió la carrera.
 * Es un {@link IllegalStateException} para que las rutas que ya devolvían 409 por «correo ya existe» sigan igual.
 */
public class CorreoYaRegistradoException extends IllegalStateException {

    public CorreoYaRegistradoException(String correo) {
        super("Ya existe una cuenta con el correo " + correo);
    }
}
