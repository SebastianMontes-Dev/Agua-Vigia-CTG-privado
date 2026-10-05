package com.aguavigia.ctg.domain;

/**
 * Identidad con la que vota un reporte (ADR-007, ADR-090): el SHA-256 del id de un dispositivo que el servidor
 * emitió, o de una cuenta de vecino. Permite RF006 (límite de reportes) y un voto por identidad en el quórum.
 *
 * Ya no la elige el cliente. Ojo con la de cuenta: se deriva solo del id de usuario, sin secreto, así que quien
 * conozca ese id (un ADMIN que lista cuentas) puede recalcularla y enlazar los reportes de esa cuenta. Es lo que
 * hace que una cuenta vote como una persona, pero no es anónima.
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

    /**
     * D7 — la huella de quien reporta con un token de dispositivo: el SHA-256 del id que firmó el servidor. Ya no
     * la inventa el cliente, así que no se puede fabricar una por reporte; y no contiene el id, que no tiene por qué
     * repetirse en cada reporte guardado.
     */
    public static HuellaDispositivo deDispositivo(DispositivoId dispositivo) {
        return new HuellaDispositivo(sha256("dispositivo:" + dispositivo.valor()));
    }

    /**
     * Quien reporta desde su cuenta vota como una sola persona aunque use varios aparatos. El espacio de nombres
     * («cuenta:» frente a «dispositivo:») impide que un id de cuenta y uno de dispositivo con el mismo texto sean el
     * mismo votante.
     */
    public static HuellaDispositivo deCuenta(UsuarioId cuenta) {
        return new HuellaDispositivo(sha256("cuenta:" + cuenta.valor()));
    }

    /** Quien recibió un enlace de un toque en su correo. El prefijo la separa de cuentas y dispositivos. */
    public static HuellaDispositivo deSuscripcion(SuscripcionId suscripcion) {
        return new HuellaDispositivo(sha256("suscripcion:" + suscripcion.valor()));
    }

    private static String sha256(String texto) {
        try {
            java.security.MessageDigest resumen = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(
                    resumen.digest(texto.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException imposibleEnCualquierJvm) {
            throw new IllegalStateException("SHA-256 no disponible", imposibleEnCualquierJvm);
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
