package com.aguavigia.ctg.infrastructure.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

/**
 * En producción, los enlaces de cuenta usan `aguavigia.app.url-publica` y los avisos usan
 * `aguavigia.app.url-frontend`. Ninguna puede apuntar a la máquina del desarrollador.
 */
@Component
@Profile("prod")
class ValidacionDeUrlPublicaProd {

    private static final Set<String> HOSTS_LOCALES = Set.of("localhost", "127.0.0.1", "0.0.0.0", "[::1]", "::1");

    ValidacionDeUrlPublicaProd(@Value("${aguavigia.app.url-publica:}") String urlPublica,
                              @Value("${aguavigia.app.url-frontend:}") String urlFrontend) {
        validar(urlPublica, "APP_URL_PUBLICA");
        validar(urlFrontend, "APP_URL_FRONTEND");
    }

    private static void validar(String urlPublica, String propiedad) {
        String host = hostDe(urlPublica);
        if (host == null || HOSTS_LOCALES.contains(host)) {
            throw new IllegalStateException(
                    propiedad + " debe ser la URL pública real del despliegue (https://tu-dominio) y no '"
                            + urlPublica + "': los enlaces de los correos saldrían apuntando a la máquina local.");
        }
    }

    private static String hostDe(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(url.trim());
            if (uri.getScheme() == null || uri.getHost() == null) {
                return null;
            }
            return uri.getHost().toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
