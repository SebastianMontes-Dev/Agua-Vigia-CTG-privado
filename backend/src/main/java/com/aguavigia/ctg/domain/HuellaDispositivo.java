package com.aguavigia.ctg.domain;

/**
 * Identificador anónimo del aparato que reporta (ADR-007). No es una cuenta ni un dato personal:
 * es lo único que permite RF006 (límite de reportes por dispositivo) sin pedir registro.
 *
 * El prefijo `IoT-` marca una huella de sensor (M13) solo como *forma*, para que quede legible en
 * Mongo — no es la fuente de verdad de si un reporte viene de un sensor. Esa decisión la toma
 * quien registra el reporte (`RegistrarReporteUseCase.registrar`, parámetro `esSensor`) a partir
 * de por qué endpoint entró la petición, nunca del contenido de esta huella: un cliente anónimo
 * que mande `POST /api/reportes` con una huella que empiece por `IoT-` no consigue el cupo de
 * sensor con solo eso, porque `/api/iot/presion` (el único que puede fijar `esSensor=true`) exige
 * además `X-IoT-Key`.
 */
public record HuellaDispositivo(String hash) {

    private static final String PREFIJO_SENSOR = "IoT-";

    public HuellaDispositivo {
        if (hash == null || hash.isBlank()) {
            throw new IllegalArgumentException("La huella de dispositivo no puede estar vacía");
        }
    }

    /** M13 — huella de un sensor de presión, derivada de su identificador. */
    public static HuellaDispositivo deSensor(String sensorId) {
        if (sensorId == null || sensorId.isBlank()) {
            throw new IllegalArgumentException("El sensor debe declarar su identificador");
        }
        return new HuellaDispositivo(PREFIJO_SENSOR + sensorId.trim());
    }
}
