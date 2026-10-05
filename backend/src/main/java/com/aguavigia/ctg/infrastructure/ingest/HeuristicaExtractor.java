package com.aguavigia.ctg.infrastructure.ingest;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracción por heurísticas deterministas sobre los boletines de Acuacar, tras el descarte de la
 * IA en `ADR-025`.
 *
 * <h2>Por qué cambió</h2>
 * La versión anterior tomaba la <i>primera</i> aparición de «barrios» del texto. En un boletín real
 * («#2854 — AGUA DE CARTAGENA REPARA FUGA…», 21/08/2026) eso caía en la frase de resumen
 * «suspensión del servicio a <b>barrios del entorno</b>» y devolvía la lista {@code ["del entorno"]},
 * mientras los 20 barrios afectados —enumerados más abajo tras «en los siguientes barrios:»— se
 * perdían enteros. Medido sobre 37 boletines de mayo a agosto de 2026, el ancla nueva identifica
 * al menos un barrio del catálogo oficial en el 100% de los que traen lista enumerada.
 *
 * <h2>Qué hace</h2>
 * <ol>
 *   <li>Ancla en la enumeración explícita («los siguientes barrios, sectores y corregimientos:»),
 *       que es la plantilla que Acuacar usa cuando de verdad hay barrios afectados.</li>
 *   <li>Devuelve los nombres <b>tal como los escribió la fuente</b>. Decidir cuáles existen es de
 *       {@link EmparejadorDeSectores}, contra el catálogo catastral: aquí no se inventa un sector.</li>
 *   <li>Lee la ventana prometida con {@link LectorDeVentanaDeclarada} (RF020–RF022).</li>
 *   <li>Gradúa la confianza según la evidencia que realmente encontró.</li>
 * </ol>
 *
 * <h2>La confianza ya no es una constante</h2>
 * `ADR-028` descartó publicar por umbral con un argumento exacto: «el extractor emite un valor
 * constante de 0.6, el umbral no distinguiría nada». Ahora distingue —enumeración explícita y
 * ventana horaria valen más que una mención suelta— y el número sirve para ordenar la cola del
 * veedor y, desde F3, para las compuertas de publicación ({@code CompuertaDePublicacion}, ADR-092): un boletín de Acuacar
 * solo se publica solo si la lectura es fiable. {@code citaTextual} es la frase literal que lee el veedor (`ADR-006`).
 */
@Component
public class HeuristicaExtractor {

    /** Enumeración explícita + ventana horaria: la plantilla completa de un aviso de suspensión. */
    static final double CONFIANZA_ENUMERACION_CON_VENTANA = 0.85;
    /** Enumeración explícita sin horario: los barrios son fiables, el «cuándo» no consta. */
    static final double CONFIANZA_ENUMERACION = 0.75;
    /** Mención en prosa, sin lista: se propone, pero es lo más débil que este extractor emite. */
    static final double CONFIANZA_MENCION_SUELTA = 0.45;

    private static final int LARGO_MAXIMO_CITA = 300;
    /** Un aviso por zona puede enumerar más de cien barrios: el tope solo evita leer un documento sin fin. */
    private static final int LARGO_MAXIMO_REGION = 8000;
    /** Cuánto texto previo al «…siguientes barrios:» entra en la cita: ahí van el día y la hora. */
    private static final int CONTEXTO_ANTES_DEL_ANCLA = 180;

    /**
     * «(los) siguientes barrios», «barrios y sectores:», «barrios, sectores y corregimientos:».
     * Exige los dos puntos: es lo que separa la enumeración real de la prosa que solo menciona la
     * palabra «barrios».
     */
    private static final Pattern ANCLA_ENUMERACION = Pattern.compile(
            "(?i)(?:los\\s+)?(?:siguientes\\s+)?(barrios?|sector(?:es)?|corregimiento(?:s)?)"
                    + "(?:\\s*(?:,|y)\\s*(?:barrios?|sector(?:es)?|corregimiento(?:s)?))*"
                    + "(?:\\s+(?:programad[oa]s|afectad[oa]s|impactad[oa]s))?\\s*:");

    /** Corta la enumeración donde vuelve a empezar la prosa del boletín. */
    private static final Pattern FIN_DE_ENUMERACION = Pattern.compile(
            "(?i)\\b(?:aguas de cartagena|acuacar|la compa[nñ][ií]a|la empresa|se recomienda|"
                    + "se invita|para m[aá]s informaci[oó]n|la programaci[oó]n|cabe (?:se[nñ]alar|recordar)|"
                    + "es importante|de igual (?:forma|manera)|as[ií] mismo|adicionalmente)\\b");

    /**
     * Los dos puntos también separan: dentro de una enumeración larga Acuacar abre sublistas
     * («Nelson Mandela, sectores: Los Olivos, Las Vegas»), y sin cortar ahí el primer nombre de
     * cada sublista quedaba pegado a la palabra que la introduce.
     */
    private static final Pattern SEPARADOR = Pattern.compile("[,;.:]|\\sy\\s");

    /**
     * Frases que nombran «barrios» sin decir cuáles. Si se dejaran pasar, «del entorno» o
     * «afectados» viajarían como si fueran nombres propios.
     */
    private static final Pattern MENCION_GENERICA = Pattern.compile(
            "(?i)^(?:del?\\s+)?(?:entorno|sector|la\\s+zona|zona\\s+\\w+|la\\s+ciudad|"
                    + "algunos?|varios?|otros?|dem[aá]s|mismos?|afectad[oa]s?|aleda[nñ][oa]s?|"
                    + "comprendidos?|incluidos?|mencionad[oa]s?|beneficiad[oa]s?|"
                    + "las\\s+zonas?\\s+\\w+|el\\s+entorno)\\b.*$");

    /**
     * El encabezado de la zona siguiente («Grupo 2», «30 de septiembre Horario de suspensión:») queda pegado al final
     * de la lista de la anterior; sin quitarlo, entraría como si fuera el último barrio.
     */
    private static final Pattern ENCABEZADO_DE_ZONA_AL_FINAL = Pattern.compile(
            "(?i)(?:\\s*(?:grupo\\s+\\d+|zona\\s+\\d+|horario\\s+de\\s+suspensi[oó]n:?|"
                    + "\\d{1,2}\\s+de\\s+\\p{L}+|(?:lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bado|domingo)))+\\s*$");

    /** Lo que dice un aviso que levanta o mueve un corte anunciado antes (se normaliza sin tildes). */
    private static final Pattern ANULACION = Pattern.compile(
            "\\b(?:se\\s+aplaza|aplazad[oa]s?|queda\\s+aplazad[oa]|se\\s+reprograma|reprogramad[oa]s?|"
                    + "se\\s+cancela|cancelad[oa]s?|queda\\s+cancelad[oa]|no\\s+se\\s+realizara|"
                    + "se\\s+suspende\\s+la\\s+suspension)\\b");

    /** Un aviso que anuncia una suspensión nueva, aunque no traiga horario («se programó…», «habrá suspensión…»). */
    private static final Pattern ANUNCIA_SUSPENSION = Pattern.compile(
            "\\b(?:se\\s+programo|programad[oa]s?\\s+(?:la|una|para)|habra\\s+suspension|"
                    + "sera\\s+necesario\\s+(?:realizar\\s+)?(?:una\\s+)?suspe|suspender\\s+temporalmente|"
                    + "suspendera|se\\s+suspendera|interrumpira|se\\s+interrumpira|se\\s+interrumpe|"
                    + "interrupcion\\s+(?:programada|temporal)|conllevan\\s+a\\s+la\\s+suspension)");

    /** El servicio vuelve: lo dice la fuente, no lo deducimos de que haya terminado una ventana. */
    private static final Pattern RESTABLECIMIENTO = Pattern.compile(
            "\\b(?:servicio\\s+restablecid|servicio\\s+normalizad|"
                    + "restablecimiento\\s+(?:progresivo\\s+|gradual\\s+|total\\s+)?del\\s+servicio|"
                    + "normalizacion\\s+(?:progresiva\\s+|gradual\\s+)?del\\s+servicio|"
                    + "se\\s+restablecio\\s+el\\s+servicio|restablece(?:ra)?\\s+(?:gradualmente\\s+)?el\\s+servicio|"
                    + "restablecer\\s+el\\s+servicio|ya\\s+se\\s+logro\\s+restablecer)");

    private static final Pattern CAUSA = Pattern.compile("(?i)debido a\\s+([^,.]+)|por\\s+([^,.]+)");

    /** La frase que sostiene la afirmación: es lo que el veedor lee, no el arranque del documento. */
    private static final Pattern ORACION_DE_INTERRUPCION = Pattern.compile(
            "(?i)[^.]*\\b(?:suspensi[oó]n|suspender|racionamiento|corte|restablec|normaliza)[^.]*\\.");

    /**
     * Un evento por zona. Un boletín de una sola ventana da uno solo; el de varias zonas («Grupo 1 desde las 11:00… hasta
     * las 4:00… Barrios y sectores: …», «Grupo 2 …») da uno por zona, cada uno con su horario y solo sus barrios: antes
     * se tomaba la primera ventana para todos y el segundo grupo quedaba anunciado a la hora equivocada.
     *
     * Siempre devuelve al menos un evento: cuando el boletín no habla de cortes, uno con
     * {@code esInterrupcionDeAcueducto=false}, para que el orquestador sepa que ya lo leyó.
     */
    public List<EventoExtraido> extraerPorZonas(DocumentoCrudo documento) {
        String texto = documento.texto();
        String sinTildes = sinTildes(texto.toLowerCase());

        boolean mencionaInterrupcion = contieneAlguna(sinTildes,
                "suspension", "suspender", "racionamiento", "corte del servicio",
                "corte de agua", "cortes de agua");
        boolean mencionaPresionBaja = contieneAlguna(sinTildes,
                "baja presion", "presion baja", "bajas presiones");
        boolean mencionaNormalidad = RESTABLECIMIENTO.matcher(sinTildes).find();

        List<LectorDeVentanaDeclarada.VentanaEnTexto> ventanas =
                LectorDeVentanaDeclarada.leerTodas(texto, documento.publicadoEn());
        boolean anunciaSuspensionNueva = !ventanas.isEmpty() || ANUNCIA_SUSPENSION.matcher(sinTildes).find();
        // Sin horario nuevo, «se aplaza» o «se cancela» mueve o levanta lo ya anunciado: no es otro corte.
        boolean anulaLoAnunciado = ventanas.isEmpty() && ANULACION.matcher(sinTildes).find();

        String tipo = "SUSPENSION_PROGRAMADA";
        if (anulaLoAnunciado) {
            tipo = "AVISO_DE_ANULACION";
        } else if (mencionaPresionBaja && !mencionaInterrupcion) {
            tipo = "PRESION_BAJA";
        } else if (mencionaNormalidad && !anunciaSuspensionNueva) {
            // Un restablecimiento suele recordar la suspensión que termina («tras la suspensión temporal…»): mencionarla
            // no lo vuelve un corte nuevo. Y al revés, un aviso de suspensión puede hablar de «restablecer las condiciones»
            // sin ser un restablecimiento: por eso se exige que no anuncie una suspensión nueva.
            tipo = "SERVICIO_NORMAL";
        }
        boolean hablaDeServicio = mencionaInterrupcion || mencionaPresionBaja || mencionaNormalidad;

        List<EventoExtraido> porZonas = zonasConVentanaPropia(texto, ventanas, tipo, hablaDeServicio, documento);
        if (!porZonas.isEmpty()) {
            return porZonas;
        }

        List<String> mencionados = barriosEnumerados(texto);
        boolean huboEnumeracion = !mencionados.isEmpty();
        if (!huboEnumeracion) {
            mencionados = barriosEnProsa(texto);
        }
        LectorDeVentanaDeclarada.Ventana ventana = ventanas.isEmpty()
                ? new LectorDeVentanaDeclarada.Ventana(null, null) : ventanas.get(0).ventana();
        return List.of(evento(hablaDeServicio, tipo, mencionados, ventana, huboEnumeracion, texto, citaTextual(texto)));
    }

    /**
     * Si el boletín trae dos o más horarios y cada uno va seguido de su propia lista de barrios, cada par es una zona.
     * Los horarios sin lista propia —la ventana global de una jornada que luego se detalla por días— no son zonas.
     * Vacío cuando no hay esa estructura: el llamador usa entonces la lectura de una sola zona.
     */
    private static List<EventoExtraido> zonasConVentanaPropia(String texto,
            List<LectorDeVentanaDeclarada.VentanaEnTexto> ventanas, String tipo, boolean hablaDeServicio,
            DocumentoCrudo documento) {
        if (ventanas.size() < 2) {
            return List.of();
        }
        List<EventoExtraido> zonas = new ArrayList<>();
        for (int i = 0; i < ventanas.size(); i++) {
            LectorDeVentanaDeclarada.VentanaEnTexto actual = ventanas.get(i);
            int finDelTramo = i + 1 < ventanas.size() ? ventanas.get(i + 1).desde() : texto.length();
            String tramo = quitarEncabezadoDeLaSiguienteZona(texto.substring(actual.hasta(), finDelTramo));
            List<String> barrios = barriosEnumerados(tramo);
            if (barrios.isEmpty()) {
                continue;
            }
            String citaDeLaZona = citaTextual(texto.substring(actual.desde(), actual.hasta()) + tramo);
            zonas.add(evento(hablaDeServicio, tipo, barrios, actual.ventana(), true, texto, citaDeLaZona));
        }
        return List.copyOf(zonas);
    }

    /** El encabezado de una zona es corto: solo se mira el final del tramo, no todo (la expresión se vuelve lenta con tramos largos). */
    private static final int LARGO_MAXIMO_DE_ENCABEZADO = 200;

    private static String quitarEncabezadoDeLaSiguienteZona(String tramo) {
        int desde = Math.max(0, tramo.length() - LARGO_MAXIMO_DE_ENCABEZADO);
        Matcher encabezado = ENCABEZADO_DE_ZONA_AL_FINAL.matcher(tramo.substring(desde));
        return encabezado.find() ? tramo.substring(0, desde + encabezado.start()) : tramo;
    }

    private static EventoExtraido evento(boolean hablaDeServicio, String tipo, List<String> barrios,
            LectorDeVentanaDeclarada.Ventana ventana, boolean huboEnumeracion, String texto, String cita) {
        return new EventoExtraido(
                hablaDeServicio && !barrios.isEmpty(),
                tipo,
                barrios,
                ventana.inicio(),
                ventana.fin(),
                causaDeclarada(texto),
                confianza(huboEnumeracion, ventana),
                camposInferidos(ventana),
                cita);
    }

    private static String sinTildes(String texto) {
        return java.text.Normalizer.normalize(texto, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    private static double confianza(boolean huboEnumeracion, LectorDeVentanaDeclarada.Ventana ventana) {
        if (!huboEnumeracion) {
            return CONFIANZA_MENCION_SUELTA;
        }
        return ventana.vacia() ? CONFIANZA_ENUMERACION : CONFIANZA_ENUMERACION_CON_VENTANA;
    }

    /** Lo que no se supo leer se declara; no se rellena. */
    private static List<String> camposInferidos(LectorDeVentanaDeclarada.Ventana ventana) {
        List<String> faltantes = new ArrayList<>();
        if (ventana.inicio() == null) {
            faltantes.add("inicioDeclarado");
        }
        if (ventana.fin() == null) {
            faltantes.add("finPrometido");
        }
        return List.copyOf(faltantes);
    }

    /**
     * Recorre <b>todas</b> las enumeraciones del boletín, no solo la primera: los avisos largos
     * abren una lista por zona («… Nelson Mandela, sectores: Los Olivos, Las Vegas, …») y quedarse
     * con la primera perdía las demás.
     */
    private static List<String> barriosEnumerados(String texto) {
        List<String> nombres = new ArrayList<>();
        Matcher ancla = ANCLA_ENUMERACION.matcher(texto);
        int desde = 0;
        while (ancla.find(desde)) {
            int inicio = ancla.end();
            String region = texto.substring(inicio,
                    Math.min(texto.length(), inicio + LARGO_MAXIMO_REGION));
            Matcher corte = FIN_DE_ENUMERACION.matcher(region);
            if (corte.find()) {
                region = region.substring(0, corte.start());
            }
            nombres.addAll(trocear(region));
            desde = inicio + Math.max(region.length(), 1);
            if (desde >= texto.length()) {
                break;
            }
        }
        return nombres;
    }

    /**
     * Solo cuando no hubo enumeración. Se limita a «barrio X» / «barrios X y Z» en prosa y descarta
     * las menciones genéricas, que es de donde salía el falso «del entorno».
     */
    private static List<String> barriosEnProsa(String texto) {
        Pattern enProsa = Pattern.compile(
                "(?i)\\b(?:barrios?|sector(?:es)?)\\s+([\\p{L}0-9 ,]{3,120}?)"
                        + "(?=[.;:]|\\s+(?:se|no|por|para|desde|hasta|durante|debido|con|donde)\\b|$)");
        Matcher coincidencia = enProsa.matcher(texto);
        List<String> nombres = new ArrayList<>();
        while (coincidencia.find()) {
            nombres.addAll(trocear(coincidencia.group(1)));
        }
        // En prosa, «barrios que tuvieron suspensión» o «sectores intervenidos» no son nombres: un barrio se escribe
        // con mayúscula inicial (o es un número, «13 de Junio»). En una enumeración con dos puntos no hace falta, porque
        // ahí todo lo que sigue es una lista.
        nombres.removeIf(nombre -> !Character.isUpperCase(nombre.charAt(0)) && !Character.isDigit(nombre.charAt(0)));
        return nombres;
    }

    private static List<String> trocear(String region) {
        List<String> nombres = new ArrayList<>();
        for (String trozo : SEPARADOR.split(region)) {
            // La fuente a veces separa con dos espacios, un salto de línea o un espacio de no separación: «La  Floresta».
            String limpio = trozo.replaceAll("[\s\u00A0\u2007\u202F]+", " ").strip();
            if (limpio.length() < 3 || limpio.length() > 60) {
                continue;
            }
            if (MENCION_GENERICA.matcher(limpio).matches()) {
                continue;
            }
            if (limpio.chars().noneMatch(Character::isLetter)) {
                continue;
            }
            // «sectores», «barrios», «corregimientos» sueltos: son la palabra que introduce la
            // sublista, no un nombre. Se reconocen porque al normalizar no queda nada.
            if (NormalizadorDeNombres.normalizar(limpio).isEmpty()) {
                continue;
            }
            nombres.add(limpio);
        }
        return nombres;
    }

    private static String causaDeclarada(String texto) {
        Matcher coincidencia = CAUSA.matcher(texto);
        if (!coincidencia.find()) {
            return "Mantenimiento / Daño general";
        }
        return coincidencia.group(1) != null ? coincidencia.group(1).trim() : coincidencia.group(2).trim();
    }

    /**
     * Fragmento literal, no un resumen (`ADR-006`): es lo que el veedor contrasta con la fuente.
     * Se prefiere la oración que habla de la interrupción; si no hay, el arranque del documento.
     */
    private static String citaTextual(String texto) {
        String limpio = texto.strip();

        // Se prefiere el tramo que rodea a la enumeración: ahí es donde el boletín dice a la vez
        // qué pasa, cuándo y en qué barrios. Antes se citaba la frase de resumen («…a barrios del
        // entorno») que no nombra ninguno, y con eso el veedor no podía contrastar nada.
        Matcher ancla = ANCLA_ENUMERACION.matcher(limpio);
        if (ancla.find()) {
            int desde = Math.max(0, ancla.start() - CONTEXTO_ANTES_DEL_ANCLA);
            if (desde > 0) {
                int corteDePalabra = limpio.indexOf(' ', desde);
                desde = corteDePalabra < 0 ? desde : corteDePalabra + 1;
            }
            String tramo = limpio.substring(desde,
                    Math.min(limpio.length(), desde + LARGO_MAXIMO_CITA)).strip();
            return (desde > 0 ? "…" : "") + recortar(tramo);
        }

        Matcher oracion = ORACION_DE_INTERRUPCION.matcher(limpio);
        return recortar(oracion.find() ? oracion.group().strip() : limpio);
    }

    private static String recortar(String texto) {
        return texto.length() <= LARGO_MAXIMO_CITA
                ? texto
                : texto.substring(0, LARGO_MAXIMO_CITA).stripTrailing() + "…";
    }

    private static boolean contieneAlguna(String texto, String... agujas) {
        for (String aguja : agujas) {
            if (texto.contains(aguja)) {
                return true;
            }
        }
        return false;
    }
}
