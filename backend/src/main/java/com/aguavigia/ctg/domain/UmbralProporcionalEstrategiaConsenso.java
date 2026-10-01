package com.aguavigia.ctg.domain;

/**
 * RF010 — el umbral crece con la población del sector (un barrio de 12.000 habitantes necesita
 * más reportes que uno de 500 para justificar la misma confianza), con un piso fijo para que un
 * sector muy pequeño —o sin dato censal, 27 de 213 no lo tienen— no quede con un umbral de 0 o 1,
 * y un tope porque participación no es población: 48 vecinos de El Pozón harían inalcanzable el quórum.
 */
public class UmbralProporcionalEstrategiaConsenso implements EstrategiaConsenso {

    private final double factorPoblacion;
    private final long umbralMinimo;
    private final long umbralMaximo;

    public UmbralProporcionalEstrategiaConsenso(double factorPoblacion, long umbralMinimo, long umbralMaximo) {
        if (factorPoblacion <= 0) {
            throw new IllegalArgumentException("El factor de población debe ser mayor que cero");
        }
        if (umbralMinimo <= 0) {
            throw new IllegalArgumentException("El umbral mínimo debe ser mayor que cero");
        }
        if (umbralMaximo < umbralMinimo) {
            throw new IllegalArgumentException("El umbral máximo no puede ser menor que el mínimo");
        }
        this.factorPoblacion = factorPoblacion;
        this.umbralMinimo = umbralMinimo;
        this.umbralMaximo = umbralMaximo;
    }

    @Override
    public long umbral(Sector sector) {
        if (sector.poblacion() == null) {
            return umbralMinimo;
        }
        long proporcional = (long) Math.ceil(sector.poblacion() * factorPoblacion);
        return Math.min(umbralMaximo, Math.max(umbralMinimo, proporcional));
    }
}
