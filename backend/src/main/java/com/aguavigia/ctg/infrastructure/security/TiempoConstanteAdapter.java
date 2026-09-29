package com.aguavigia.ctg.infrastructure.security;

import com.aguavigia.ctg.domain.port.out.TiempoConstantePort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Espera con {@code Thread.sleep}, que con hilos virtuales no retiene un hilo de plataforma: el
 * mínimo se puede subir sin que el servidor pierda capacidad. Debe superar con holgura el tiempo
 * de la rama más lenta (token + correo), o la diferencia vuelve a notarse.
 */
@Component
public class TiempoConstanteAdapter implements TiempoConstantePort {

    private final long duracionMinimaNanos;

    @Autowired
    public TiempoConstanteAdapter(
            @Value("${aguavigia.seguridad.duracion-minima-solicitud-ms:100}") long duracionMinimaMs) {
        this(Duration.ofMillis(duracionMinimaMs));
    }

    public TiempoConstanteAdapter(Duration duracionMinima) {
        this.duracionMinimaNanos = duracionMinima.toNanos();
    }

    @Override
    public void ejecutar(Runnable accion) {
        long inicio = System.nanoTime();
        try {
            accion.run();
        } finally {
            esperarHasta(inicio + duracionMinimaNanos);
        }
    }

    private static void esperarHasta(long instanteNanos) {
        boolean interrumpido = false;
        long restante;
        while ((restante = instanteNanos - System.nanoTime()) > 0) {
            try {
                Thread.sleep(Duration.ofNanos(restante));
            } catch (InterruptedException e) {
                interrumpido = true;
            }
        }
        if (interrumpido) {
            Thread.currentThread().interrupt();
        }
    }
}
