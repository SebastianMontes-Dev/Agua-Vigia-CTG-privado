package com.aguavigia.ctg.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * `aguavigia.cors.origenes-permitidos` en application.yml. Lista vacia por defecto: si el
 * frontend se sirve detras del mismo nginx que hace de proxy de /api (infra/nginx/nginx.conf),
 * el navegador nunca hace una peticion cruzada y no hay nada que permitir. Mismo criterio
 * opt-in que `aguavigia.rate-limit` (ADR-018): habilitar CORS de par en par por si acaso seria
 * abrir la API a cualquier origen sin que nadie lo decidiera.
 *
 * Se configura solo cuando el frontend vive en otro origen que el backend: su dev server local o
 * un host estatico aparte tendrian que declarar aqui su dominio.
 */
@ConfigurationProperties(prefix = "aguavigia.cors")
public record CorsProperties(List<String> origenesPermitidos) {

    public CorsProperties {
        origenesPermitidos = origenesPermitidos == null ? List.of() : List.copyOf(origenesPermitidos);
    }

    public boolean habilitado() {
        return !origenesPermitidos.isEmpty();
    }
}
