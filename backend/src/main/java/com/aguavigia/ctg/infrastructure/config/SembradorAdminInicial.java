package com.aguavigia.ctg.infrastructure.config;

import com.aguavigia.ctg.application.RegistroDeAuditoria;
import com.aguavigia.ctg.domain.AccionAuditada;
import com.aguavigia.ctg.domain.ClaveEnClaro;
import com.aguavigia.ctg.domain.ClaveHash;
import com.aguavigia.ctg.domain.ContextoDeAccion;
import com.aguavigia.ctg.domain.CorreoElectronico;
import com.aguavigia.ctg.domain.EstadoCuenta;
import com.aguavigia.ctg.domain.PermisosEfectivos;
import com.aguavigia.ctg.domain.RolVeedor;
import com.aguavigia.ctg.domain.Usuario;
import com.aguavigia.ctg.domain.UsuarioId;
import com.aguavigia.ctg.domain.port.out.CifradorClavePort;
import com.aguavigia.ctg.domain.port.out.RelojPort;
import com.aguavigia.ctg.domain.port.out.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * Resuelve el huevo y la gallina de un sistema donde solo un ADMIN puede crear cuentas: si nadie
 * puede entrar, nadie puede aprobar a nadie.
 *
 * Reutiliza VEEDOR_PASSWORD_HASH, la credencial compartida de ADR-016, en lugar de pedir una
 * variable nueva: ya está en el `.env` y así la migración no exige repartir
 * credenciales. Deja de servir en cuanto existe la primera cuenta — a partir de ahí la clave
 * pertenece a una persona con nombre y correo, no a una credencial compartida.
 *
 * La cuenta nace ADMIN y, por tanto, obligada a dar de alta su segundo factor antes de poder hacer
 * nada: su primera sesión tendrá alcance ALTA_SEGUNDO_FACTOR (ver AlcanceSesion).
 *
 * Con `aguavigia.cuentas.admin-inicial-generar-clave` (perfil `docker`, ADR-086) y sin
 * VEEDOR_PASSWORD_HASH, genera una clave aleatoria y la escribe una sola vez en el log: así
 * `docker compose up` deja un panel accesible sin versionar ninguna credencial.
 */
@Component
public class SembradorAdminInicial {

    private static final Logger log = LoggerFactory.getLogger(SembradorAdminInicial.class);

    private final UsuarioRepository usuarios;
    private final RegistroDeAuditoria auditoria;
    private final RelojPort reloj;
    private final String correoConfigurado;
    private final String hashConfigurado;
    private final CifradorClavePort cifrador;
    private final boolean generarClaveSiFalta;

    public SembradorAdminInicial(UsuarioRepository usuarios,
                                 RegistroDeAuditoria auditoria,
                                 RelojPort reloj,
                                 CifradorClavePort cifrador,
                                 @Value("${aguavigia.cuentas.admin-inicial-correo:}") String correoConfigurado,
                                 @Value("${aguavigia.veedor.password-hash:}") String hashConfigurado,
                                 @Value("${aguavigia.cuentas.admin-inicial-generar-clave:false}") boolean generarClaveSiFalta) {
        this.usuarios = usuarios;
        this.auditoria = auditoria;
        this.reloj = reloj;
        this.cifrador = cifrador;
        this.correoConfigurado = correoConfigurado;
        this.hashConfigurado = hashConfigurado;
        this.generarClaveSiFalta = generarClaveSiFalta;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void sembrarSiNoHayNadie() {
        if (correoConfigurado.isBlank() || (hashConfigurado.isBlank() && !generarClaveSiFalta)) {
            log.info("Sin ADMIN_INICIAL_CORREO o VEEDOR_PASSWORD_HASH: no se siembra administrador. "
                    + "El panel del veedor quedara sin acceso hasta configurarlos.");
            return;
        }

        try {
            // Basta con saber si existe alguna cuenta, sea cual sea su estado: sembrar por segunda
            // vez sobre un sistema que ya tuvo administradores volveria a abrir una credencial que
            // se pudo haber retirado a proposito.
            if (usuarios.listar(null, null, 0, 1).totalElementos() > 0) {
                return;
            }

            CorreoElectronico correo = new CorreoElectronico(correoConfigurado).normalizado();
            String claveGenerada = hashConfigurado.isBlank() ? generarClave() : null;
            ClaveHash hash = claveGenerada != null ? cifrador.cifrar(claveGenerada) : new ClaveHash(hashConfigurado);

            Usuario admin = usuarios.guardar(new Usuario(
                    new UsuarioId(UUID.randomUUID().toString()),
                    correo,
                    "Administrador inicial",
                    hash,
                    EstadoCuenta.ACTIVA,
                    PermisosEfectivos.deRol(RolVeedor.ADMIN),
                    null,
                    reloj.ahora(),
                    reloj.ahora()));

            auditoria.registrarConAutor(AccionAuditada.CUENTA_APROBADA, null, admin,
                    "Administrador inicial sembrado al arrancar con un sistema sin cuentas",
                    ContextoDeAccion.delSistema());

            if (claveGenerada != null) {
                // Una sola línea: en el perfil docker el log sale en JSON, y un salto de línea llegaría como «\n».
                log.warn("ADMINISTRADOR INICIAL | correo: {} | clave: {} | se muestra solo esta vez; al entrar, "
                        + "da de alta el segundo factor", admin.correo().valor(), claveGenerada);
            } else {
                log.info("Administrador inicial creado para {}. Entra al panel y da de alta tu segundo "
                        + "factor: hasta entonces la sesion solo sirve para eso.", admin.correo().valor());
            }
        } catch (DataAccessException noHayMongo) {
            // Mismo criterio que IndicesMongo: el backend no se cae porque Mongo no este disponible
            // al arrancar. Al proximo reinicio con base de datos, la siembra vuelve a intentarse.
            log.warn("No se pudo sembrar el administrador inicial: {}", noHayMongo.getMessage());
        } catch (IllegalArgumentException configuracionInvalida) {
            log.error("ADMIN_INICIAL_CORREO o VEEDOR_PASSWORD_HASH no son validos: {}",
                    configuracionInvalida.getMessage());
        }
    }

    private static String generarClave() {
        SecureRandom aleatorio = new SecureRandom();
        // Sin caracteres que se confunden al copiarlos del log (0/O, 1/l/I).
        String alfabeto = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
        StringBuilder clave = new StringBuilder(20);
        for (int i = 0; i < 20; i++) {
            clave.append(alfabeto.charAt(aleatorio.nextInt(alfabeto.length())));
        }
        return new ClaveEnClaro(clave.toString()).valor();
    }
}
