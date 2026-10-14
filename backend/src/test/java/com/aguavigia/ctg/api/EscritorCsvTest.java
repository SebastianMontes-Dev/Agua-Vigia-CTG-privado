package com.aguavigia.ctg.api;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** RF025 — el archivo tiene que abrirse bien donde lo van a abrir: Excel en español. */
class EscritorCsvTest {

    private static final String BOM = "﻿";

    @Test
    void debeEmpezarConBomYSepararConPuntoYComa() {
        String csv = EscritorCsv.escribir(List.of("a", "b"), List.of(List.of("1", "2")));

        // Sin BOM, Excel asume la codificacion del sistema y "Cienaga" llega como "CiÃ©naga".
        assertThat(csv).startsWith(BOM);
        assertThat(csv).isEqualTo(BOM + "a;b\r\n1;2\r\n");
    }

    @Test
    void debeTerminarCadaFilaEnCrlfComoPideRfc4180() {
        String csv = EscritorCsv.escribir(List.of("a"), List.of(List.of("1"), List.of("2")));

        assertThat(csv).isEqualTo(BOM + "a\r\n1\r\n2\r\n");
    }

    /** Los nombres de barrio vienen de un GeoJSON de terceros, no de una lista que controlemos. */
    @Test
    void debeEntrecomillarUnValorQueTraeElSeparador() {
        String csv = EscritorCsv.escribir(List.of("nombre"), List.of(List.of("Manga; Bocagrande")));

        assertThat(csv).isEqualTo(BOM + "nombre\r\n\"Manga; Bocagrande\"\r\n");
    }

    @Test
    void debeDuplicarLasComillasInternas() {
        String csv = EscritorCsv.escribir(List.of("nombre"), List.of(List.of("El \"Laguito\"")));

        assertThat(csv).isEqualTo(BOM + "nombre\r\n\"El \"\"Laguito\"\"\"\r\n");
    }

    @Test
    void debeEntrecomillarUnValorConSaltoDeLinea() {
        String csv = EscritorCsv.escribir(List.of("nombre"), List.of(List.of("Manga\nCrespo")));

        assertThat(csv).isEqualTo(BOM + "nombre\r\n\"Manga\nCrespo\"\r\n");
    }

    /**
     * Una celda que empieza por `=`, `+`, `-` o `@` la abre Excel como fórmula (`=HYPERLINK(...)` exfiltra datos con un clic).
     * Los textos vienen de terceros (barrios, causas de un boletín), así que se les antepone una comilla simple.
     */
    @Test
    void debeNeutralizarLosTextosQueExcelTomariaPorFormula() {
        String csv = EscritorCsv.escribir(List.of("a"), List.of(
                List.of("=HYPERLINK(\"http://x\")"), List.of("+cmd"), List.of("@SUM(A1)"), List.of("-2+3")));

        assertThat(csv).isEqualTo(BOM + "a\r\n\"'=HYPERLINK(\"\"http://x\"\")\"\r\n'+cmd\r\n'@SUM(A1)\r\n'-2+3\r\n");
    }

    /** Una cifra negativa o con signo es un número, no una fórmula: neutralizarla la volvería texto. */
    @Test
    void noDebeTocarLosNumerosAunqueLlevenSigno() {
        String csv = EscritorCsv.escribir(List.of("a"), List.of(List.of("-1,5"), List.of("+3"), List.of("12,0"), List.of("0")));

        assertThat(csv).isEqualTo(BOM + "a\r\n-1,5\r\n+3\r\n12,0\r\n0\r\n");
    }

    @Test
    void unValorNuloDebeSalirComoCeldaVacia() {
        String csv = EscritorCsv.escribir(List.of("a", "b"), List.of(java.util.Arrays.asList("1", null)));

        assertThat(csv).isEqualTo(BOM + "a;b\r\n1;\r\n");
    }

    @Test
    void sinFilasDebeQuedarSoloElEncabezado() {
        assertThat(EscritorCsv.escribir(List.of("a", "b"), List.of())).isEqualTo(BOM + "a;b\r\n");
    }
}
