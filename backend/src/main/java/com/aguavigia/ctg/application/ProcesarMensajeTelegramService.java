package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.ChatTelegramId;
import com.aguavigia.ctg.domain.DescripcionDeEstado;
import com.aguavigia.ctg.domain.MensajeTelegram;
import com.aguavigia.ctg.domain.Sector;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.SuscripcionTelegram;
import com.aguavigia.ctg.domain.port.in.ProcesarMensajeTelegramUseCase;
import com.aguavigia.ctg.domain.port.out.EnvioTelegramPort;
import com.aguavigia.ctg.domain.port.out.RelojPort;
import com.aguavigia.ctg.domain.port.out.SectorRepository;
import com.aguavigia.ctg.domain.port.out.SuscripcionTelegramRepository;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * RF041 — el bot de Telegram como canal de alertas alternativo al correo. Entiende cinco comandos
 * (/suscribir, /baja, /estado, /mis y /ayuda) y responde siempre en el mismo chat.
 *
 * El sector se escribe como aparece en el mapa. Se compara contra el id (que es el nombre en minúsculas
 * y con guiones) y, si no coincide, se sugieren hasta cinco parecidos.
 */
public class ProcesarMensajeTelegramService implements ProcesarMensajeTelegramUseCase {

    private static final int MAXIMO_SUGERENCIAS = 5;

    static final String AYUDA = "AguaVigía: avisos del acueducto de Cartagena.\n\n"
            + "/suscribir <sector> — te aviso cuando cambie su estado (ej.: /suscribir Bocagrande)\n"
            + "/baja <sector> — dejo de avisarte de ese sector\n"
            + "/baja — dejo de avisarte de todo y borro tu chat de mis registros\n"
            + "/estado <sector> — te digo cómo está ahora\n"
            + "/mis — los sectores que sigues\n"
            + "/ayuda — este mensaje\n\n"
            + "Solo guardo el identificador de este chat mientras sigas al menos un sector. "
            + "Somos una plataforma ciudadana independiente, no de Acuacar.";

    private final SuscripcionTelegramRepository suscripciones;
    private final SectorRepository sectores;
    private final EnvioTelegramPort envio;
    private final RelojPort reloj;

    public ProcesarMensajeTelegramService(SuscripcionTelegramRepository suscripciones, SectorRepository sectores,
                                           EnvioTelegramPort envio, RelojPort reloj) {
        this.suscripciones = suscripciones;
        this.sectores = sectores;
        this.envio = envio;
        this.reloj = reloj;
    }

    @Override
    public void procesar(MensajeTelegram mensaje) {
        String texto = mensaje.texto();
        if (!texto.startsWith("/")) {
            responder(mensaje.chat(), "No entendí ese mensaje. Escribe /ayuda para ver lo que puedo hacer.");
            return;
        }
        String[] partes = texto.split("\\s+", 2);
        String comando = partes[0].toLowerCase(Locale.ROOT);
        int arroba = comando.indexOf('@');
        if (arroba >= 0) {
            comando = comando.substring(0, arroba);
        }
        String argumento = partes.length > 1 ? partes[1].strip() : "";

        switch (comando) {
            case "/start", "/ayuda", "/help" -> responder(mensaje.chat(), AYUDA);
            case "/suscribir" -> suscribir(mensaje.chat(), argumento);
            case "/baja" -> darDeBaja(mensaje.chat(), argumento);
            case "/estado" -> consultarEstado(mensaje.chat(), argumento);
            case "/mis" -> listar(mensaje.chat());
            default -> responder(mensaje.chat(), "No conozco ese comando. Escribe /ayuda para ver los disponibles.");
        }
    }

    private void suscribir(ChatTelegramId chat, String argumento) {
        if (argumento.isEmpty()) {
            responder(chat, "Dime el sector. Por ejemplo: /suscribir Bocagrande");
            return;
        }
        Optional<Sector> sector = resolver(argumento);
        if (sector.isEmpty()) {
            responder(chat, noEncontrado(argumento));
            return;
        }
        SuscripcionTelegram actual = suscripciones.buscarPorChat(chat)
                .orElseGet(() -> SuscripcionTelegram.nueva(chat, reloj.ahora()));
        if (actual.sigue(sector.get().id())) {
            responder(chat, "Ya seguías " + sector.get().nombre() + ". No hace falta repetirlo.");
            return;
        }
        try {
            suscripciones.guardar(actual.siguiendo(sector.get().id()));
        } catch (IllegalStateException limite) {
            responder(chat, limite.getMessage() + ". Usa /baja <sector> para liberar uno.");
            return;
        }
        responder(chat, "Listo: te aviso cuando cambie el estado de " + sector.get().nombre() + ".");
    }

    private void darDeBaja(ChatTelegramId chat, String argumento) {
        Optional<SuscripcionTelegram> actual = suscripciones.buscarPorChat(chat);
        if (actual.isEmpty()) {
            responder(chat, "No sigues ningún sector.");
            return;
        }
        if (argumento.isEmpty() || argumento.equalsIgnoreCase("todo") || argumento.equalsIgnoreCase("todos")) {
            suscripciones.eliminar(chat);
            responder(chat, "Listo: dejé de avisarte y borré tu chat de mis registros.");
            return;
        }
        Optional<Sector> sector = resolver(argumento);
        if (sector.isEmpty()) {
            responder(chat, noEncontrado(argumento));
            return;
        }
        if (!actual.get().sigue(sector.get().id())) {
            responder(chat, "No seguías " + sector.get().nombre() + ".");
            return;
        }
        SuscripcionTelegram restante = actual.get().sinSeguir(sector.get().id());
        if (restante.sinSectores()) {
            suscripciones.eliminar(chat);
            responder(chat, "Dejé de avisarte de " + sector.get().nombre()
                    + ". Ya no sigues ningún sector: borré tu chat de mis registros.");
            return;
        }
        suscripciones.guardar(restante);
        responder(chat, "Dejé de avisarte de " + sector.get().nombre() + ".");
    }

    private void consultarEstado(ChatTelegramId chat, String argumento) {
        if (argumento.isEmpty()) {
            responder(chat, "Dime el sector. Por ejemplo: /estado Bocagrande");
            return;
        }
        Optional<Sector> sector = resolver(argumento);
        if (sector.isEmpty()) {
            responder(chat, noEncontrado(argumento));
            return;
        }
        responder(chat, sector.get().nombre() + ": " + DescripcionDeEstado.describir(sector.get().estadoActual()) + ".");
    }

    private void listar(ChatTelegramId chat) {
        Optional<SuscripcionTelegram> actual = suscripciones.buscarPorChat(chat);
        if (actual.isEmpty() || actual.get().sinSectores()) {
            responder(chat, "No sigues ningún sector. Usa /suscribir <sector>.");
            return;
        }
        String nombres = actual.get().sectorIds().stream()
                .map(id -> sectores.buscarPorId(id).map(Sector::nombre).orElse(id.valor()))
                .collect(Collectors.joining(", "));
        responder(chat, "Sigues: " + nombres + ".");
    }

    private Optional<Sector> resolver(String escrito) {
        String slug = normalizar(escrito);
        if (slug.isEmpty()) {
            return Optional.empty();
        }
        Optional<Sector> porId = sectores.buscarPorId(new SectorId(slug));
        if (porId.isPresent()) {
            return porId;
        }
        return sectores.listarTodos().stream()
                .filter(sector -> normalizar(sector.nombre()).equals(slug))
                .findFirst();
    }

    private String noEncontrado(String escrito) {
        String slug = normalizar(escrito);
        List<String> parecidos = slug.isEmpty() ? List.of() : sectores.listarTodos().stream()
                .filter(sector -> normalizar(sector.nombre()).contains(slug))
                .limit(MAXIMO_SUGERENCIAS)
                .map(Sector::nombre)
                .toList();
        if (parecidos.isEmpty()) {
            return "No encontré ningún sector llamado «" + escrito + "». Escríbelo como aparece en el mapa.";
        }
        return "No encontré «" + escrito + "». ¿Quisiste decir: " + String.join(", ", parecidos) + "?";
    }

    static String normalizar(String texto) {
        String sinTildes = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return sinTildes.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
    }

    private void responder(ChatTelegramId chat, String texto) {
        envio.enviar(chat, texto);
    }
}
