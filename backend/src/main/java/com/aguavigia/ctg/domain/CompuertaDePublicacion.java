package com.aguavigia.ctg.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Las compuertas que un aviso de Acuacar debe pasar para publicarse solo (D5). Antes todo lo oficial salía al mapa sin
 * mirar la confianza: un boletín de 0,45 —una mención suelta— pintaba barrios por una lectura dudosa. Ahora una
 * extracción poco fiable, o fiable pero sin sentido (una ventana de cuatro días, un corte «en 90 barrios», un año mal
 * leído), espera al veedor con el motivo a la vista.
 *
 * Es una función pura de lo que el aviso dice: no sabe de dónde viene ni qué hace el sistema con el resultado. Un
 * resultado vacío significa «publícalo»; si no, son las razones —cada una una frase que el veedor puede leer— por las que
 * se encola. Los umbrales son valores iniciales configurables, sin datos reales que los respalden (plan §9).
 *
 * @param confianzaMinima               lo que se exige para publicar un corte o una presión baja sin revisión
 * @param confianzaMinimaDeRestablecimiento lo que se exige para publicar un restablecimiento: basta una enumeración
 *                                      explícita, porque afirmar que hay agua no inventa una emergencia
 * @param duracionMaxima                una ventana más larga se considera mal leída
 * @param margenDeInicio                cuánto puede alejarse el inicio de la fecha de publicación, hacia cualquier lado
 * @param maximoDeBarrios               más sectores que esto en un solo aviso se considera una lectura que se desbordó
 */
public record CompuertaDePublicacion(double confianzaMinima, double confianzaMinimaDeRestablecimiento,
                                     Duration duracionMaxima, Duration margenDeInicio, int maximoDeBarrios) {

    public CompuertaDePublicacion {
        for (double confianza : new double[]{confianzaMinima, confianzaMinimaDeRestablecimiento}) {
            if (confianza < 0 || confianza > 1) {
                throw new IllegalArgumentException("La confianza mínima debe estar entre 0 y 1: " + confianza);
            }
        }
        Objects.requireNonNull(duracionMaxima, "Falta la duración máxima de una ventana");
        Objects.requireNonNull(margenDeInicio, "Falta el margen del inicio respecto a la publicación");
        if (duracionMaxima.isZero() || duracionMaxima.isNegative() || margenDeInicio.isNegative()) {
            throw new IllegalArgumentException("La duración máxima debe ser positiva y el margen no negativo");
        }
        if (maximoDeBarrios < 1) {
            throw new IllegalArgumentException("El máximo de barrios por aviso debe ser al menos 1: " + maximoDeBarrios);
        }
    }

    public static CompuertaDePublicacion porDefecto() {
        return new CompuertaDePublicacion(0.85, 0.75, Duration.ofHours(72), Duration.ofDays(7), 40);
    }

    /**
     * @param barrios      cuántos sectores del catálogo toca <b>el boletín entero</b> (con los alias ya expandidos): un boletín
     *                     de seis zonas de cuarenta barrios no debe publicar 240 sin revisión solo por partirse en zonas
     * @param aliasAmbiguo si algún nombre del boletín casa con más de un sector del catálogo sin que nadie lo haya resuelto
     * @return vacío si el aviso puede publicarse solo
     */
    public List<String> motivosParaRevisar(EstadoServicio estado, double confianza, Instant inicio, Instant fin,
                                           Instant publicadoEn, int barrios, boolean aliasAmbiguo, String citaTextual) {
        List<String> motivos = new ArrayList<>();

        // ADR-006: sin la frase exacta del boletín que respalde la lectura, nada llega al mapa sin que alguien la revise.
        if (citaTextual == null || citaTextual.isBlank()) {
            motivos.add("La propuesta no trae la cita textual del boletín que la respalda");
        }

        double exigida = estado == EstadoServicio.CON_SERVICIO ? confianzaMinimaDeRestablecimiento : confianzaMinima;
        if (confianza < exigida) {
            motivos.add("La confianza de la extracción es %s y se exige al menos %s para publicar sin revisión"
                    .formatted(coma(confianza), coma(exigida)));
        }
        if (inicio != null && fin != null && Duration.between(inicio, fin).compareTo(duracionMaxima) > 0) {
            motivos.add("La ventana dura más de %d horas, lo que suele ser una fecha mal leída"
                    .formatted(duracionMaxima.toHours()));
        }
        if (inicio != null && publicadoEn != null
                && Duration.between(publicadoEn, inicio).abs().compareTo(margenDeInicio) > 0) {
            motivos.add("El inicio de la ventana queda a más de %d días de la fecha de publicación del boletín"
                    .formatted(margenDeInicio.toDays()));
        }
        if (barrios > maximoDeBarrios) {
            motivos.add("El aviso toca %d barrios y el máximo para publicar sin revisión es %d"
                    .formatted(barrios, maximoDeBarrios));
        }
        if (aliasAmbiguo) {
            motivos.add("Un nombre del boletín es ambiguo: casa con más de un barrio del catálogo");
        }
        return List.copyOf(motivos);
    }

    private static String coma(double valor) {
        return String.format(java.util.Locale.forLanguageTag("es-CO"), "%.2f", valor);
    }
}
