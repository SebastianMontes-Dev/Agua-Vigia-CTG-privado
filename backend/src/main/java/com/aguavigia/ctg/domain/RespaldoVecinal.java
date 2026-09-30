package com.aguavigia.ctg.domain;

/** Cuántos vecinos sostienen un estado y cuántos hacían falta: «11 vecinos» se muestra con su umbral, no suelto. */
public record RespaldoVecinal(int vecinos, int umbral) {
}
