package com.aguavigia.ctg.infrastructure.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;
import java.util.Set;

/**
 * `aguavigia.rate-limit.reglas` en application.yml. Lista vacia por defecto: sin reglas
 * configuradas, ninguna ruta queda protegida — es opt-in, no un comportamiento sorpresa.
 *
 * Ejemplo para el login del veedor (ADR-016, hueco senalado — no se activa aqui, cada perfil
 * decide su propio valor):
 * <pre>
 * aguavigia:
 *   rate-limit:
 *     reglas:
 *       - ruta: /api/veedor/sesion
 *         limite: 5
 *         ventana-segundos: 300
 * </pre>
 *
 * Dos factores (por defecto 1) multiplican el límite de las reglas, sin tocar la ventana. Son para las instancias que no son la
 * real: la simulación y las pruebas de carga crean cientos de vecinos y de dispositivos desde una sola IP, y con los topes de
 * producción local (10 identidades por hora) no podrían ni empezar. Nunca aprietan (un factor menor que 1 se rechaza) y tienen techo
 * ({@link #FACTOR_MAXIMO}): un factor desmedido dejaría la ruta sin límite de hecho.
 * <ul>
 *   <li>{@code factor}: todo **menos** las rutas de {@link #RUTAS_DE_CUENTAS}. Es el que se sube para una prueba de carga.</li>
 *   <li>{@code factorCuentas}: solo esas rutas (ingreso, 2.º factor, cambio de clave, altas y correos). Es un mando aparte para que
 *       subir el factor de carga no afloje por descuido el freno a la fuerza bruta ni a los correos salientes; solo la simulación,
 *       que registra cientos de cuentas por HTTP desde un equipo, lo necesita.</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "aguavigia.rate-limit")
public record RateLimitProperties(List<Regla> reglas, @DefaultValue("1") double factor,
                                  @DefaultValue("1") double factorCuentas) {

    /** Techo de cualquiera de los dos factores. */
    public static final double FACTOR_MAXIMO = 1000;

    /** Las rutas donde adivinar una clave o inundar una bandeja de correo es el riesgo: no las mueve `factor`. */
    public static final Set<String> RUTAS_DE_CUENTAS = Set.of(
            "/api/veedor/sesion", "/api/vecino/sesion", "/api/cuentas/**", "/api/suscripciones/**",
            "/api/veedor/segundo-factor/**", "/api/veedor/cuenta/**");

    public RateLimitProperties {
        validar("factor", factor);
        validar("factor-cuentas", factorCuentas);
        List<Regla> declaradas = reglas == null ? List.of() : List.copyOf(reglas);
        reglas = factor == 1 && factorCuentas == 1 ? declaradas : declaradas.stream()
                .map(regla -> regla.multiplicadaPor(RUTAS_DE_CUENTAS.contains(regla.ruta()) ? factorCuentas : factor))
                .toList();
    }

    private static void validar(String nombre, double valor) {
        if (valor < 1) {
            throw new IllegalArgumentException("El " + nombre + " del rate limit no puede ser menor que 1: " + valor);
        }
        if (valor > FACTOR_MAXIMO) {
            throw new IllegalArgumentException("El " + nombre + " del rate limit no puede pasar de " + (int) FACTOR_MAXIMO + ": " + valor);
        }
    }

    /** Verdadero si algún tope está multiplicado: no es la configuración de la instancia real. */
    public boolean escalado() {
        return factor > 1 || factorCuentas > 1;
    }

    /** ruta: patron Ant (mismo formato que @RequestMapping). limite peticiones por ventanaSegundos, por IP. */
    public record Regla(String ruta, int limite, int ventanaSegundos) {

        public Regla {
            if (ruta == null || ruta.isBlank()) {
                throw new IllegalArgumentException("Una regla de rate limiting debe declarar su ruta");
            }
            if (limite <= 0) {
                throw new IllegalArgumentException("El limite debe ser mayor que cero (ruta: " + ruta + ")");
            }
            if (ventanaSegundos <= 0) {
                throw new IllegalArgumentException("La ventana debe ser mayor que cero (ruta: " + ruta + ")");
            }
        }

        Regla multiplicadaPor(double factor) {
            return factor == 1 ? this
                    : new Regla(ruta, (int) Math.min(Integer.MAX_VALUE, Math.round(limite * factor)), ventanaSegundos);
        }
    }
}
