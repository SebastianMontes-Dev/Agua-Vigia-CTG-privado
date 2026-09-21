package com.aguavigia.ctg.infrastructure.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

/**
 * En producción los enlaces de los correos (verificar cuenta, restablecer clave, baja de una
 * suscripción) se arman con `aguavigia.app.url-publica`. Su valor por defecto es `localhost`, así
 * que si el despliegue se olvida de definirla los correos salen con enlaces que solo funcionan en
 * la máquina del desarrollador y nadie lo nota hasta que un usuario los abre. Se aborta el
 * arranque en vez de enviar enlaces rotos.
 */
@Component
@Profile("prod")
class ValidacionDeUrlPublicaProd {

    private static final Set<String> HOSTS_LOCALES = Set.of("localhost", "127.0.0.1", "0.0.0.0", "[::1]", "::1");

    ValidacionDeUrlPublicaProd(@Value("${aguavigia.app.url-publica:}") String urlPublica) {
        String host = hostDe(urlPublica);
        if (host == null || HOSTS_LOCALES.contains(host)) {
            throw new IllegalStateException(
                    "APP_URL_PUBLICA debe ser la URL pública real del despliegue (https://tu-dominio) y no '"
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
