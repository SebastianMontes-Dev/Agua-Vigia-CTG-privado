package com.aguavigia.ctg.api;

import com.aguavigia.ctg.domain.Pagina;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Publica los metadatos de paginación en **cabeceras**, no en el cuerpo.
 *
 * Envolver la respuesta en `{contenido: [...], total: N}` habría sido más convencional, pero rompe
 * a todo cliente que ya consume estas rutas como un arreglo JSON — y el contrato con el frontend es aditivo.
 * Con cabeceras, un cliente que las ignore sigue funcionando igual y uno que las lea puede paginar.
 *
 * `X-Total-Count` es lo que espera la mayoría de clientes generados; `Link` con `rel="next"` es el
 * mecanismo de RFC 8288, el mismo que usa la API de GitHub.
 */
final class CabecerasDePaginacion {

    private CabecerasDePaginacion() {
    }

    static <T> ResponseEntity<List<T>> respuesta(Pagina<?> pagina, List<T> contenido, String rutaBase) {
        return respuesta(pagina, contenido, rutaBase, Map.of());
    }

    /**
     * Los filtros viajan en el enlace a la siguiente página: sin ellos, un cliente que siga `Link`
     * pasaría de «eventos de Manga» a la página 2 de toda la ciudad. Los nulos se omiten.
     */
    static <T> ResponseEntity<List<T>> respuesta(Pagina<?> pagina, List<T> contenido, String rutaBase,
                                                 Map<String, ?> filtros) {
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.add("X-Total-Count", String.valueOf(pagina.totalElementos()));
        cabeceras.add("X-Total-Pages", String.valueOf(pagina.totalPaginas()));
        cabeceras.add("X-Page", String.valueOf(pagina.pagina()));
        cabeceras.add("X-Page-Size", String.valueOf(pagina.tamano()));

        if (pagina.hayMas()) {
            StringBuilder consulta = new StringBuilder();
            filtros.forEach((nombre, valor) -> {
                if (valor != null) {
                    consulta.append('&').append(nombre).append('=')
                            .append(URLEncoder.encode(valor.toString(), StandardCharsets.UTF_8));
                }
            });
            cabeceras.add(HttpHeaders.LINK, "<%s?pagina=%d&tamano=%d%s>; rel=\"next\""
                    .formatted(rutaBase, pagina.pagina() + 1, pagina.tamano(), consulta));
        }

        // Sin esto, un navegador no deja que el JavaScript del frontend lea las cabeceras: son
        // personalizadas y CORS las oculta por defecto. Aquí no hay CORS (nginx sirve todo bajo el
        // mismo origen), pero declararlo evita una sorpresa el día que alguien sirva la API aparte.
        cabeceras.add(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
                "X-Total-Count, X-Total-Pages, X-Page, X-Page-Size, Link");

        return ResponseEntity.ok().headers(cabeceras).body(contenido);
    }
}
