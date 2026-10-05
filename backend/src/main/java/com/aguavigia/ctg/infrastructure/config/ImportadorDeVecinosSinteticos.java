package com.aguavigia.ctg.infrastructure.config;

import com.aguavigia.ctg.domain.ClaveHash;
import com.aguavigia.ctg.domain.RolVeedor;
import com.aguavigia.ctg.domain.port.in.ImportarVecinosSinteticosUseCase;
import com.aguavigia.ctg.domain.port.out.CifradorClavePort;
import com.aguavigia.ctg.domain.port.out.SectorRepository;
import com.aguavigia.ctg.domain.port.out.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Crea las cuentas sintéticas de vecino al arrancar (D36), dentro del backend y sin HTTP: es el único sitio que sabe en qué
 * orden hay que hacer las cosas.
 *
 * Espera, en un hilo aparte para no retrasar el arranque, a que existan el ADMIN inicial —que {@link SembradorAdminInicial}
 * solo crea en un sistema sin ninguna cuenta: si este importador corriera antes, el ADMIN no nacería nunca— y los sectores,
 * que siembra otro proceso. Se activa con \`aguavigia.siembra.vecinos-sinteticos\` (0, por defecto, no hace nada; el perfil
 * \`docker\` lo pone en 30 000).
 *
 * La clave que llevan todas las cuentas se calcula una vez (un solo BCrypt) a partir de una contraseña aleatoria que se
 * descarta: nadie la conoce, así que ninguna cuenta sintética se puede abrir.
 */
@Component
public class ImportadorDeVecinosSinteticos {

    private static final Logger log = LoggerFactory.getLogger(ImportadorDeVecinosSinteticos.class);

    /** Cómo se espera entre intentos; en las pruebas no duerme. */
    @FunctionalInterface
    interface Espera {
        void dormir(long milisegundos);
    }

    private final ImportarVecinosSinteticosUseCase importar;
    private final UsuarioRepository usuarios;
    private final SectorRepository sectores;
    private final CifradorClavePort cifrador;
    private final int objetivo;
    private final int sectoresEsperados;
    private final int intentosMaximos;
    private final Espera espera;

    @Autowired
    public ImportadorDeVecinosSinteticos(
            ImportarVecinosSinteticosUseCase importar, UsuarioRepository usuarios, SectorRepository sectores,
            CifradorClavePort cifrador,
            @Value("${aguavigia.siembra.vecinos-sinteticos:0}") int objetivo,
            @Value("${aguavigia.siembra.sectores-esperados:211}") int sectoresEsperados,
            @Value("${aguavigia.siembra.intentos-de-espera:60}") int intentosMaximos,
            @Value("${aguavigia.siembra.intervalo-de-espera-ms:5000}") long intervaloMs) {
        this(importar, usuarios, sectores, cifrador, objetivo, sectoresEsperados, intentosMaximos, milisegundos -> {
            try {
                Thread.sleep(intervaloMs);
            } catch (InterruptedException interrumpido) {
                Thread.currentThread().interrupt();
            }
        });
    }

    ImportadorDeVecinosSinteticos(ImportarVecinosSinteticosUseCase importar, UsuarioRepository usuarios,
                                  SectorRepository sectores, CifradorClavePort cifrador, int objetivo,
                                  int sectoresEsperados, int intentosMaximos, Espera espera) {
        this.importar = importar;
        this.usuarios = usuarios;
        this.sectores = sectores;
        this.cifrador = cifrador;
        this.objetivo = objetivo;
        this.sectoresEsperados = sectoresEsperados;
        this.intentosMaximos = intentosMaximos;
        this.espera = espera;
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void alArrancar() {
        importarCuandoEsteListo();
    }

    void importarCuandoEsteListo() {
        if (objetivo <= 0) {
            return;
        }
        try {
            boolean hayAdmin = false;
            boolean haySectores = false;
            for (int intento = 0; intento <= intentosMaximos; intento++) {
                haySectores = sectores.listarTodos().size() >= sectoresEsperados;
                hayAdmin = usuarios.contarActivosPorRol(RolVeedor.ADMIN) > 0;
                if (haySectores && hayAdmin) {
                    break;
                }
                if (intento < intentosMaximos) {
                    espera.dormir(intento);
                }
            }
            if (!haySectores) {
                log.warn("Cuentas sintéticas: faltan los sectores ({} esperados); no se crean. Se reintentará en el próximo arranque.",
                        sectoresEsperados);
                return;
            }
            if (!hayAdmin) {
                log.warn("Cuentas sintéticas: no apareció el ADMIN inicial (¿falta ADMIN_INICIAL_CORREO?); se crean igual.");
            }
            int creadas = importar.importar(objetivo, claveCompartida());
            log.info("Cuentas sintéticas: {} creadas en esta pasada (objetivo {}). Son de demostración, no personas.",
                    creadas, objetivo);
        } catch (DataAccessException noHayMongo) {
            // Mismo criterio que SembradorAdminInicial: el backend no se cae porque Mongo no responda; la siguiente vez lo reintenta.
            log.warn("No se pudieron crear las cuentas sintéticas: {}", noHayMongo.getMessage());
        }
    }

    private ClaveHash claveCompartida() {
        byte[] azar = new byte[32];
        new SecureRandom().nextBytes(azar);
        return cifrador.cifrar(HexFormat.of().formatHex(azar));
    }
}
