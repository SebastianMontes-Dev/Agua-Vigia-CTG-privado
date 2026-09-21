package com.aguavigia.ctg.api;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.HtmlUtils;

import java.nio.charset.StandardCharsets;

/**
 * Las páginas mínimas que abre un navegador desde el enlace de un correo (ADR-030). Sin plantilla
 * externa ni JavaScript: el correo apunta a la propia API porque no hay otro sitio que pinte estas
 * pantallas, y la política de contenido del proxy (infra/nginx) solo permite estilos en línea.
 *
 * Todo texto variable se escapa aquí y no en quien llama: el mensaje de una excepción o el token de
 * la URL acaban dentro de HTML y una sola omisión sería un XSS reflejado.
 */
final class PaginaDeCortesia {

    private static final MediaType HTML_UTF8 = new MediaType("text", "html", StandardCharsets.UTF_8);

    private static final String PLANTILLA = """
            <!doctype html>
            <html lang="es-CO">
            <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <meta name="color-scheme" content="light dark">
            <meta name="referrer" content="no-referrer">
            <title>{{titulo}} · AguaVigía CTG</title>
            <style>
              body { margin:0; min-height:100vh; display:flex; align-items:center; justify-content:center;
                     font-family:system-ui,-apple-system,'Segoe UI',Roboto,sans-serif;
                     background:#EEF3F3; color:#0B1F26; }
              @media (prefers-color-scheme: dark) { body { background:#061418; color:#E4EFEF; } }
              .tarjeta { max-width:420px; margin:1rem; padding:2rem; text-align:center;
                         background:#FFFFFF; border:1px solid #C9D9D8; border-radius:12px; }
              @media (prefers-color-scheme: dark) { .tarjeta { background:#0C2027; border-color:#1B3B45; } }
              .icono { font-size:2.5rem; }
              h1 { font-size:1.35rem; margin:0.75rem 0; }
              p { font-size:0.95rem; line-height:1.5; color:#3E585F; margin:0; }
              @media (prefers-color-scheme: dark) { p { color:#9DB8BE; } }
              form { margin-top:1.5rem; text-align:left; }
              label { display:block; font-size:0.85rem; font-weight:600; margin-bottom:0.35rem; }
              input[type=password] { box-sizing:border-box; width:100%; padding:0.6rem 0.7rem;
                         font-size:1rem; border:1px solid #C9D9D8; border-radius:8px; }
              button { margin-top:1rem; display:block; width:100%; padding:0.7rem;
                       font-size:1rem; font-weight:600; color:#FFFFFF; background:#0A6C78;
                       border:0; border-radius:8px; cursor:pointer; }
            </style>
            </head>
            <body>
              <div class="tarjeta">
                <div class="icono" aria-hidden="true">{{icono}}</div>
                <h1>{{titulo}}</h1>
                <p>{{mensaje}}</p>
            {{formulario}}
              </div>
            </body>
            </html>
            """;

    private PaginaDeCortesia() {
    }

    /** Resultado final de una acción: éxito (200) o fallo (400), sin nada más que hacer. */
    static ResponseEntity<String> resultado(String titulo, String mensaje, boolean ok) {
        return respuesta(ok ? HttpStatus.OK : HttpStatus.BAD_REQUEST, ok ? "✅" : "⚠️", titulo, mensaje, "");
    }

    /**
     * Formulario que hace `POST` a {@code accion} llevando el token en un campo oculto. El `GET` que
     * lo muestra no consume nada: los antivirus de correo precargan los enlaces, y un enlace de un
     * solo uso que actúa con solo abrirse quedaría gastado antes de que la persona lo vea.
     *
     * @param pideClave si es true añade el campo de clave nueva (invitación y restablecimiento)
     * @param estado    200 al mostrarlo por primera vez, 400 al volver a mostrarlo con un error
     */
    static ResponseEntity<String> formulario(HttpStatus estado, String titulo, String mensaje,
                                             String accion, String token, boolean pideClave,
                                             String textoBoton) {
        String campoClave = pideClave
                ? """
                          <label for="clave">Clave nueva</label>
                          <input id="clave" name="clave" type="password" autocomplete="new-password" required>
                  """
                : "";
        String form = """
                    <form method="post" action="{{accion}}">
                      <input type="hidden" name="token" value="{{token}}">
                {{campoClave}}      <button type="submit">{{textoBoton}}</button>
                    </form>"""
                .replace("{{accion}}", escapar(accion))
                .replace("{{token}}", escapar(token))
                .replace("{{campoClave}}", campoClave)
                .replace("{{textoBoton}}", escapar(textoBoton));
        String icono = estado == HttpStatus.OK ? "🔐" : "⚠️";
        return respuesta(estado, icono, titulo, mensaje, form);
    }

    private static ResponseEntity<String> respuesta(HttpStatus estado, String icono, String titulo,
                                                    String mensaje, String formulario) {
        String html = PLANTILLA
                .replace("{{titulo}}", escapar(titulo))
                .replace("{{icono}}", icono)
                .replace("{{mensaje}}", escapar(mensaje))
                .replace("{{formulario}}", formulario);
        // no-store: la página puede llevar un token de un solo uso en el HTML.
        return ResponseEntity.status(estado)
                .contentType(HTML_UTF8)
                .cacheControl(CacheControl.noStore())
                .body(html);
    }

    private static String escapar(String texto) {
        return HtmlUtils.htmlEscape(texto == null ? "" : texto, "UTF-8");
    }
}
