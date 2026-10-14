package com.aguavigia.ctg.api;

import com.aguavigia.ctg.api.error.ServicioNoDisponibleException;
import com.aguavigia.ctg.domain.CredencialInvalidaException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Protege las rutas de simulación con {@code X-Sim-Key}, igual que {@code X-IoT-Key} protege las de sensores: 503 si el servidor no tiene
 * clave (vacía por defecto), 401 si la cabecera falta o no coincide, y comparación en tiempo constante. Solo existe donde la simulación
 * está habilitada: en la instancia real no hay ruta que proteger.
 */
@Component
@ConditionalOnProperty(prefix = "aguavigia.sim", name = "habilitada", havingValue = "true")
public class GuardiaDeSimulacion {

    private static final int LONGITUD_MINIMA = 32;

    private final String clave;

    public GuardiaDeSimulacion(@Value("${aguavigia.sim.clave:}") String clave) {
        // Esta clave mueve el reloj y abre sesiones de ADMIN: una adivinable no vale (mismo criterio que IOT_KEY).
        if (!clave.isBlank() && clave.length() < LONGITUD_MINIMA) {
            throw new IllegalStateException("SIMULACION_CLAVE mide " + clave.length() + " caracteres y se exigen al menos "
                    + LONGITUD_MINIMA + ". Genera una con, por ejemplo: openssl rand -hex 16");
        }
        this.clave = clave;
    }

    void exigir(String cabecera) {
        if (clave.isBlank()) {
            throw new ServicioNoDisponibleException("La simulación no tiene clave configurada en este servidor (SIMULACION_CLAVE)");
        }
        if (cabecera == null || !MessageDigest.isEqual(cabecera.getBytes(StandardCharsets.UTF_8), clave.getBytes(StandardCharsets.UTF_8))) {
            throw new CredencialInvalidaException("Clave de simulación ausente o incorrecta");
        }
    }
}
