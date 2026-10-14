package com.aguavigia.ctg.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Falla el arranque, con un mensaje accionable, si un secreto está mal configurado, en vez de dejar que
 * el sistema responda 500 o acepte una clave adivinable cuando alguien lo use.
 *
 * - `IOT_KEY`, si se define, mide al menos 32 caracteres: es una clave compartida que permite inyectar
 *   reportes anónimos en el consenso público, así que «cualquier valor» no vale.
 * - Con el perfil `prod`, `JWT_SECRET` es obligatorio (HS256 exige 32 bytes o más).
 *
 * En dev y docker un JWT vacío no tumba el arranque: el perfil docker genera uno aleatorio (ADR-086) y en dev
 * los endpoints públicos no dependen de él.
 */
@Component
public class ValidacionDeSecretos {

    static final int LONGITUD_MINIMA_IOT = 32;
    static final int BYTES_MINIMOS_JWT = 32;

    public ValidacionDeSecretos(Environment entorno,
                                @Value("${aguavigia.iot.key:}") String claveIot,
                                @Value("${aguavigia.jwt.secret:}") String secretoJwt) {
        if (!claveIot.isBlank() && claveIot.length() < LONGITUD_MINIMA_IOT) {
            throw new IllegalStateException("IOT_KEY mide " + claveIot.length() + " caracteres y se exigen al menos "
                    + LONGITUD_MINIMA_IOT + ". Genera una con, por ejemplo: openssl rand -base64 32");
        }
        if (entorno.acceptsProfiles(Profiles.of("prod"))
                && secretoJwt.getBytes(StandardCharsets.UTF_8).length < BYTES_MINIMOS_JWT) {
            throw new IllegalStateException("Con el perfil prod JWT_SECRET es obligatorio y mide al menos "
                    + BYTES_MINIMOS_JWT + " bytes. Genera uno con, por ejemplo: openssl rand -base64 32");
        }
    }
}
