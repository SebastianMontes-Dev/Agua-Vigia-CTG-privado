package com.aguavigia.ctg.infrastructure.ingest;

import org.springframework.web.util.HtmlUtils;

/** Boletines y notas de prensa llegan como HTML renderizado; DocumentoCrudo quiere texto plano. */
final class LimpiadorHtml {

    private LimpiadorHtml() {
    }

    /**
     * Un boletín real pasa poco de 12 000 caracteres. El tope existe porque el extractor recorre el texto con varias
     * expresiones regulares: un cuerpo enorme (un feed comprometido, un ítem sin fin) lo volvería muy lento y congelaría el
     * ciclo, que corre con una sola réplica a la vez.
     */
    static final int LARGO_MAXIMO = 150_000;

    static String limpiar(String html) {
        if (html == null) {
            return "";
        }
        // Se recorta antes de aplicar la expresión regular, no después: es la que se vuelve lenta con textos largos.
        String acotado = html.length() > LARGO_MAXIMO * 4 ? html.substring(0, LARGO_MAXIMO * 4) : html;
        String sinEtiquetas = acotado.replaceAll("<[^>]+>", " ");
        String limpio = HtmlUtils.htmlUnescape(sinEtiquetas).replaceAll("\\s+", " ").trim();
        return limpio.length() > LARGO_MAXIMO ? limpio.substring(0, LARGO_MAXIMO) : limpio;
    }
}
