package com.aguavigia.ctg.api;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * RF025 — exportación en formato abierto. Escrito a mano y no con una librería: dos exportaciones
 * de forma fija no justifican una dependencia nueva, el mismo criterio por el que `PlantillaCorreo`
 * no trajo un motor de plantillas para dos correos.
 *
 * Dos decisiones para que el archivo se abra bien donde lo van a abrir:
 *
 * - **Separador `;`** y no coma: Excel en configuración regional española interpreta la coma como
 *   separador decimal y mete toda la fila en una sola celda.
 * - **BOM UTF-8** al principio: sin él, Excel asume la codificación del sistema y "Ciénaga de la
 *   Virgen" llega como "CiÃ©naga". El consumidor declarado de RF025 es un periodista, no un script.
 */
final class EscritorCsv {

    /** Excel solo respeta la codificación del archivo si encuentra esta marca al inicio. */
    private static final String BOM_UTF8 = "﻿";
    private static final char SEPARADOR = ';';

    /**
     * Coma decimal, fijada explícitamente y no heredada de `Locale.getDefault()`: el formato del
     * archivo no puede depender de la configuración regional del servidor que lo genere. Es coma y
     * no punto por coherencia con el separador `;` — ambas decisiones existen para que Excel en
     * español abra el archivo bien.
     */
    private static final Locale LOCALE_CO = Locale.forLanguageTag("es-CO");

    /** Una cifra, con signo o sin él: no es una fórmula aunque empiece por `-` o `+`. */
    private static final Pattern NUMERO = Pattern.compile("[+-]?\\d+([.,]\\d+)?");

    private EscritorCsv() {
    }

    /** Un decimal: DESIGN.md §5 pide cifras que una persona pueda leer, no toda la mantisa. */
    static String numero(double valor) {
        return String.format(LOCALE_CO, "%.1f", valor);
    }

    static String escribir(List<String> encabezados, List<List<String>> filas) {
        StringBuilder csv = new StringBuilder(BOM_UTF8);
        agregarFila(csv, encabezados);
        for (List<String> fila : filas) {
            agregarFila(csv, fila);
        }
        return csv.toString();
    }

    private static void agregarFila(StringBuilder csv, List<String> valores) {
        for (int i = 0; i < valores.size(); i++) {
            if (i > 0) {
                csv.append(SEPARADOR);
            }
            csv.append(escapar(valores.get(i)));
        }
        // CRLF y no LF: es lo que pide RFC 4180 y lo que Excel espera.
        csv.append("\r\n");
    }

    /**
     * Excel y LibreOffice abren como fórmula una celda que empieza por `=`, `+`, `-` o `@` (OWASP, «CSV injection»): un
     * `=HYPERLINK(...)` en la causa de un boletín exfiltraría datos de quien abra el archivo. Los textos vienen de terceros,
     * así que se les antepone una comilla simple, que Excel muestra como texto. Las cifras se dejan como están.
     */
    private static String neutralizarFormula(String texto) {
        if (texto.isEmpty() || "=+-@".indexOf(texto.charAt(0)) < 0 || NUMERO.matcher(texto).matches()) {
            return texto;
        }
        return "'" + texto;
    }

    /**
     * Un nombre de barrio con `;`, comillas o un salto de línea rompería el archivo entero — y los
     * nombres vienen de un GeoJSON de terceros, no de una lista que controlemos.
     */
    private static String escapar(String valor) {
        String texto = neutralizarFormula(valor == null ? "" : valor);
        if (texto.indexOf(SEPARADOR) < 0 && texto.indexOf('"') < 0
                && texto.indexOf('\n') < 0 && texto.indexOf('\r') < 0) {
            return texto;
        }
        return '"' + texto.replace("\"", "\"\"") + '"';
    }
}
