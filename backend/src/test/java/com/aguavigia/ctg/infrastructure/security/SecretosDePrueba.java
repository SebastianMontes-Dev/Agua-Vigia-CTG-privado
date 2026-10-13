package com.aguavigia.ctg.infrastructure.security;

import com.aguavigia.ctg.compartido.secretos.SecretosDelSistema;

import java.util.function.Function;

/** Un {@link SecretosDelSistema} que no toca Mongo: devuelve lo que diga la función para cada nombre de secreto. */
final class SecretosDePrueba {

    private SecretosDePrueba() {
    }

    static SecretosDelSistema con(Function<String, String> secretoPorNombre) {
        return new SecretosDelSistema(null) {
            @Override
            public String obtenerOCrear(String nombre) {
                return secretoPorNombre.apply(nombre);
            }
        };
    }
}
