package com.aguavigia.ctg.infrastructure.ingest;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lee del texto del boletín la ventana que Acuacar <b>promete</b>: «mañana viernes 21 de agosto,
 * entre las 9:00 a.m. y las 6:00 p.m.».
 *
 * Sin esto el Índice de Cumplimiento (RF020–RF022) no tiene con qué comparar la duración real, que
 * es la tesis del proyecto. Antes estos dos campos se rellenaban con `now` y `now + 12h` bajo un
 * comentario «fallback: asume»: dato inventado. La corrección fue ponerlos en nulo; lo que faltaba
 * era leer el que sí está escrito.
 *
 * <b>Si no hay ventana explícita se devuelve null, nunca una estimada.</b> Un cumplimiento calculado
 * contra una promesa que nadie hizo sería peor que no calcular ninguno.
 *
 * La hora se interpreta en la zona de Cartagena: el boletín le habla a un vecino, no a UTC.
 *
 * <h2>Formatos que reconoce (los que Acuacar usa en sus boletines)</h2>
 * <ul>
 *   <li>«entre las 9:00 a.m. y las 6:00 p.m.», con la fecha en una frase anterior;</li>
 *   <li>«de 8:00 a.m. a 4:00 p.m.» y «de 08:00 a 16:00 horas»;</li>
 *   <li>«desde las 11:00 a.m. del 9 de septiembre hasta las 4:00 a.m. del 10 de septiembre»: la fecha va en cada
 *       extremo, con o sin día de la semana, y el fin puede ser «del mismo día»;</li>
 *   <li>«12:00 de la medianoche» y «12:00 del mediodía».</li>
 * </ul>
 * Un boletín con varias zonas trae una ventana por zona: {@link #leerTodas} devuelve cada una con su posición en el
 * texto, para que el extractor sepa qué barrios le tocan.
 */
final class LectorDeVentanaDeclarada {

    /** Ventana declarada por la fuente. Cualquiera de los dos extremos puede faltar. */
    record Ventana(Instant inicio, Instant fin) {
        boolean vacia() {
            return inicio == null && fin == null;
        }
    }

    /** Una ventana y el tramo del texto donde está escrita: [desde, hasta). */
    record VentanaEnTexto(int desde, int hasta, Ventana ventana) {
    }

    static final ZoneId ZONA_CARTAGENA = ZoneId.of("America/Bogota");

    private static final Map<String, Integer> MESES = Map.ofEntries(
            Map.entry("enero", 1), Map.entry("febrero", 2), Map.entry("marzo", 3),
            Map.entry("abril", 4), Map.entry("mayo", 5), Map.entry("junio", 6),
            Map.entry("julio", 7), Map.entry("agosto", 8), Map.entry("septiembre", 9),
            Map.entry("setiembre", 9), Map.entry("octubre", 10), Map.entry("noviembre", 11),
            Map.entry("diciembre", 12));

    private static final String MESES_ER = "enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|setiembre|"
            + "octubre|noviembre|diciembre";
    private static final String DIA_DE_LA_SEMANA_ER = "(?:lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bado|domingo)";

    /**
     * Una hora con su meridiano: «9:00 a.m.», «6 p. m.», «12:00 de la medianoche», «12:00 del mediodía» o sin
     * meridiano («08:00», hora de 24). Grupos: hora, minutos, a/p, medianoche, mediodía.
     */
    private static final String HORA_ER = "(\\d{1,2})(?::(\\d{2}))?\\s*"
            + "(?:([ap])\\.?\\s*m\\.?|(de\\s+la\\s+medianoche)|(del\\s+mediod[ií]a))?";

    /** «entre las 9:00 a.m. y las 6:00 p.m.» — Acuacar escribe indistintamente «a.m.» y «a. m.». */
    private static final Pattern ENTRE_LAS = Pattern.compile(
            "(?i)entre\\s+las\\s+" + HORA_ER + "\\s*y\\s+(?:las\\s+)?" + HORA_ER + "(?:\\s*horas)?");

    /** «de 8:00 a.m. a 4:00 p.m.», «de 08:00 a 16:00 horas». */
    private static final Pattern DE_A = Pattern.compile(
            "(?i)\\bde\\s+" + HORA_ER + "\\s+a\\s+(?:las\\s+)?" + HORA_ER + "(\\s*(?:horas|hrs?\\.?))?");

    /**
     * «desde las 7:00 a. m. del martes 29 de septiembre hasta las 3:00 a. m. del miércoles 30 de septiembre»,
     * «desde las 11:00 a.m. del 9 de septiembre hasta las 4:00 a.m. del 10 de septiembre de 2026» o
     * «… hasta las 11:00 p. m. del mismo día».
     */
    private static final Pattern DESDE_HASTA = Pattern.compile(
            "(?i)desde\\s+(?:las?\\s+)?" + HORA_ER + "\\s*(?:del?\\s+)?(?:" + DIA_DE_LA_SEMANA_ER + "\\s+)?"
                    + "(\\d{1,2})\\s+de\\s+(" + MESES_ER + ")(?:\\s+de\\s+(\\d{4}))?\\s*,?\\s*"
                    + "hasta\\s+(?:las?\\s+)?" + HORA_ER + "\\s*(?:del?\\s+)?"
                    + "(?:(?:" + DIA_DE_LA_SEMANA_ER + "\\s+)?(\\d{1,2})\\s+de\\s+(" + MESES_ER
                    + ")(?:\\s+de\\s+(\\d{4}))?|(mismo\\s+d[ií]a))");

    private static final Pattern FECHA = Pattern.compile(
            "(?i)(\\d{1,2})\\s+de\\s+(" + MESES_ER + ")(?:\\s+de\\s+(\\d{4}))?");

    private LectorDeVentanaDeclarada() {
    }

    /** La primera ventana del texto; vacía si no hay ninguna legible. */
    static Ventana leer(String texto, Instant publicadoEn) {
        List<VentanaEnTexto> todas = leerTodas(texto, publicadoEn);
        return todas.isEmpty() ? new Ventana(null, null) : todas.get(0).ventana();
    }

    /**
     * Todas las ventanas legibles, en el orden en que están escritas. Si dos formatos leen el mismo tramo
     * («desde… hasta…» contiene horas que «de… a…» también casaría), gana el que empieza antes y, a igualdad, el más largo.
     */
    static List<VentanaEnTexto> leerTodas(String texto, Instant publicadoEn) {
        if (texto == null || texto.isBlank()) {
            return List.of();
        }
        List<VentanaEnTexto> candidatas = new ArrayList<>();
        recolectarDesdeHasta(texto, publicadoEn, candidatas);
        recolectarEntreLas(texto, publicadoEn, candidatas);
        recolectarDeA(texto, publicadoEn, candidatas);

        candidatas.sort(Comparator.comparingInt(VentanaEnTexto::desde)
                .thenComparing(Comparator.comparingInt((VentanaEnTexto v) -> v.hasta() - v.desde()).reversed()));
        List<VentanaEnTexto> elegidas = new ArrayList<>();
        int hastaDondeSeLeyo = -1;
        for (VentanaEnTexto candidata : candidatas) {
            if (candidata.desde() >= hastaDondeSeLeyo) {
                elegidas.add(candidata);
                hastaDondeSeLeyo = candidata.hasta();
            }
        }
        return List.copyOf(elegidas);
    }

    private static void recolectarDesdeHasta(String texto, Instant publicadoEn, List<VentanaEnTexto> salida) {
        Matcher m = DESDE_HASTA.matcher(texto);
        while (m.find()) {
            // Grupos: 1-5 hora de inicio, 6 día, 7 mes, 8 año; 9-13 hora de fin, 14 día, 15 mes, 16 año, 17 «mismo día».
            LocalDate diaInicio = fecha(m.group(6), m.group(7), m.group(8), publicadoEn);
            LocalDate diaFin = m.group(17) != null ? diaInicio : fecha(m.group(14), m.group(15), m.group(16), publicadoEn);
            if (diaInicio == null || diaFin == null) {
                continue;
            }
            LocalDateTime inicio = diaInicio.atTime(hora24(m, 1), minutos(m, 2));
            LocalDateTime fin = diaFin.atTime(hora24(m, 9), minutos(m, 10));
            if (fin.isAfter(inicio)) {
                salida.add(new VentanaEnTexto(m.start(), m.end(), aVentana(inicio, fin)));
            }
        }
    }

    private static void recolectarEntreLas(String texto, Instant publicadoEn, List<VentanaEnTexto> salida) {
        Matcher m = ENTRE_LAS.matcher(texto);
        while (m.find()) {
            if (!horasCoherentes(m)) {
                continue;
            }
            agregarDelMismoDia(texto, publicadoEn, m, salida);
        }
    }

    private static void recolectarDeA(String texto, Instant publicadoEn, List<VentanaEnTexto> salida) {
        Matcher m = DE_A.matcher(texto);
        while (m.find()) {
            // «de 5 a 6» no es un horario: hace falta el meridiano o los minutos en las dos horas, o la palabra «horas».
            boolean conIndicadorDeHora = m.group(11) != null;
            if (!conIndicadorDeHora && !horasCoherentes(m)) {
                continue;
            }
            agregarDelMismoDia(texto, publicadoEn, m, salida);
        }
    }

    /** Las dos horas deben traer meridiano o minutos: «de 5 a 6» o «entre las 8 y las 4» no son un horario fiable. */
    private static boolean horasCoherentes(Matcher m) {
        boolean inicioClaro = m.group(2) != null || tieneMeridiano(m, 1);
        boolean finClaro = m.group(7) != null || tieneMeridiano(m, 6);
        return inicioClaro && finClaro;
    }

    private static boolean tieneMeridiano(Matcher m, int primerGrupo) {
        return m.group(primerGrupo + 2) != null || m.group(primerGrupo + 3) != null || m.group(primerGrupo + 4) != null;
    }

    /** «entre las 9 y las 6»/«de 8 a 4»: la fecha es la que precede al horario, no la primera del documento. */
    private static void agregarDelMismoDia(String texto, Instant publicadoEn, Matcher m, List<VentanaEnTexto> salida) {
        LocalDate dia = fechaAplicable(texto, m.start(), publicadoEn);
        if (dia == null) {
            return;
        }
        LocalDateTime inicio = dia.atTime(hora24(m, 1), minutos(m, 2));
        LocalDateTime fin = dia.atTime(hora24(m, 6), minutos(m, 7));
        // «de 8:00 p.m. a 5:00 a.m.» cruza la medianoche: el fin cae al día siguiente.
        if (!fin.isAfter(inicio)) {
            fin = fin.plusDays(1);
        }
        salida.add(new VentanaEnTexto(m.start(), m.end(), aVentana(inicio, fin)));
    }

    private static Ventana aVentana(LocalDateTime inicio, LocalDateTime fin) {
        return new Ventana(inicio.atZone(ZONA_CARTAGENA).toInstant(), fin.atZone(ZONA_CARTAGENA).toInstant());
    }

    /**
     * La fecha del corte es la que precede al horario, no la primera del documento: los boletines
     * abren con la línea de fecha («Cartagena de Indias, 20 de agosto de 2026») y anuncian los
     * trabajos para otro día («mañana viernes 21 de agosto»). Tomar la primera adelantaba la
     * ventana un día entero.
     */
    private static LocalDate fechaAplicable(String texto, int posicionDelHorario, Instant publicadoEn) {
        Matcher fechas = FECHA.matcher(texto);
        LocalDate mejorAntes = null;
        LocalDate primeraDespues = null;
        while (fechas.find()) {
            LocalDate fecha = fecha(fechas.group(1), fechas.group(2), fechas.group(3), publicadoEn);
            if (fecha == null) {
                continue;
            }
            if (fechas.start() < posicionDelHorario) {
                mejorAntes = fecha;
            } else if (primeraDespues == null) {
                primeraDespues = fecha;
            }
        }
        return mejorAntes != null ? mejorAntes : primeraDespues;
    }

    /**
     * Sin año explícito se toma el de publicación, y se corrige el salto de fin de año: un boletín del 30 de
     * diciembre que anuncia trabajos «el 2 de enero» es del año siguiente, no de nueve meses antes.
     */
    private static LocalDate fecha(String dia, String mes, String anio, Instant publicadoEn) {
        Integer numeroDeMes = mes == null ? null : MESES.get(mes.toLowerCase());
        if (dia == null || numeroDeMes == null) {
            return null;
        }
        int d = Integer.parseInt(dia);
        if (anio != null) {
            return fechaSegura(Integer.parseInt(anio), numeroDeMes, d);
        }
        LocalDate publicacion = publicadoEn == null
                ? LocalDate.now(ZONA_CARTAGENA)
                : LocalDate.ofInstant(publicadoEn, ZONA_CARTAGENA);
        LocalDate candidata = fechaSegura(publicacion.getYear(), numeroDeMes, d);
        if (candidata == null) {
            return null;
        }
        if (candidata.isBefore(publicacion.minusMonths(6))) {
            return fechaSegura(publicacion.getYear() + 1, numeroDeMes, d);
        }
        return candidata;
    }

    private static LocalDate fechaSegura(int anio, int mes, int dia) {
        try {
            return LocalDate.of(anio, mes, dia);
        } catch (java.time.DateTimeException fechaInexistente) {
            // «31 de febrero» en un boletín mal redactado no debe tumbar el ciclo de ingesta.
            return null;
        }
    }

    /** `primerGrupo` es el de la hora; le siguen minutos, a/p, medianoche y mediodía. */
    private static int hora24(Matcher m, int primerGrupo) {
        int hora = Integer.parseInt(m.group(primerGrupo));
        if (m.group(primerGrupo + 3) != null) {
            return 0;
        }
        if (m.group(primerGrupo + 4) != null) {
            return 12;
        }
        String meridiano = m.group(primerGrupo + 2);
        if (meridiano == null) {
            return Math.min(hora, 23);
        }
        int normalizada = hora % 12;
        return meridiano.equalsIgnoreCase("p") ? normalizada + 12 : normalizada;
    }

    private static int minutos(Matcher m, int grupo) {
        String valor = m.group(grupo);
        return valor == null || valor.isBlank() ? 0 : Math.min(Integer.parseInt(valor), 59);
    }
}
