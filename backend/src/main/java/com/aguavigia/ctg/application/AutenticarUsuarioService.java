package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.AccionAuditada;
import com.aguavigia.ctg.domain.AlcanceSesion;
import com.aguavigia.ctg.domain.ContextoDeAccion;
import com.aguavigia.ctg.domain.CorreoElectronico;
import com.aguavigia.ctg.domain.CredencialInvalidaException;
import com.aguavigia.ctg.domain.CuentaBloqueadaException;
import com.aguavigia.ctg.domain.CuentaNoHabilitadaException;
import com.aguavigia.ctg.domain.EstadoCuenta;
import com.aguavigia.ctg.domain.SegundoFactorRequeridoException;
import com.aguavigia.ctg.domain.SesionEmitida;
import com.aguavigia.ctg.domain.Usuario;
import com.aguavigia.ctg.domain.port.in.AutenticarUsuarioUseCase;
import com.aguavigia.ctg.domain.port.out.CifradorClavePort;
import com.aguavigia.ctg.domain.port.out.ControlIntentosPort;
import com.aguavigia.ctg.domain.port.out.EmisorDeSesionPort;
import com.aguavigia.ctg.domain.port.out.SegundoFactorPort;
import com.aguavigia.ctg.domain.port.out.UsuarioRepository;

import java.time.Duration;
import java.util.Optional;

/**
 * El único camino por el que se emite una sesión. Sustituye a la comparación contra
 * VEEDOR_PASSWORD_HASH de ADR-016.
 *
 * El orden de las comprobaciones es parte del diseño, no casualidad:
 *
 * 1. El bloqueo por cuenta se mira antes que nada — si no, cada intento durante un bloqueo seguiría
 *    costando un BCrypt, y el propio freno se convertiría en el vector de agotamiento de CPU.
 * 2. Correo inexistente y clave equivocada devuelven lo mismo y tardan lo mismo
 *    ({@code gastarTiempoEquivalente}). Sin lo segundo, dan igual los mensajes: el cronómetro
 *    dice qué correos tienen cuenta.
 * 3. El estado de la cuenta se revisa DESPUÉS de validar la clave. Antes, responder "esa cuenta
 *    está suspendida" a quien no sabe la clave regalaría media respuesta.
 */
public class AutenticarUsuarioService implements AutenticarUsuarioUseCase {

    /**
     * Un código TOTP sigue siendo válido durante toda su franja. Sin esta ventana de un solo uso,
     * quien lo vea pasar (un hombro, un log mal configurado, una red hostil) puede reusarlo hasta
     * que expire. Es holgada a propósito: cubre también la franja anterior que el validador tolera.
     */
    private static final Duration VENTANA_ANTIRREPLAY_TOTP = Duration.ofMinutes(2);

    /**
     * El bloqueo se lleva por cuenta *y* dirección: si bastara con el correo, cualquiera que lo conociera
     * (el del ADMIN inicial es público) podría dejar sin acceso a su titular con cinco intentos. Además
     * hay un tope por cuenta, esta vez mucho más alto, que sí frena el ataque repartido entre muchas IP.
     */
    private static final int FACTOR_TOPE_GLOBAL = 10;
    private static final String PREFIJO_GLOBAL = "cuenta-global:";

    private final UsuarioRepository usuarios;
    private final CifradorClavePort cifrador;
    private final SegundoFactorPort segundoFactor;
    private final ControlIntentosPort intentos;
    private final EmisorDeSesionPort emisorDeSesion;
    private final RegistroDeAuditoria auditoria;
    private final int maximoIntentos;
    private final int maximoIntentosGlobales;
    private final Duration ventanaIntentos;
    private final Duration bloqueo;

    public AutenticarUsuarioService(UsuarioRepository usuarios,
                                    CifradorClavePort cifrador,
                                    SegundoFactorPort segundoFactor,
                                    ControlIntentosPort intentos,
                                    EmisorDeSesionPort emisorDeSesion,
                                    RegistroDeAuditoria auditoria,
                                    int maximoIntentos,
                                    long ventanaIntentosMinutos,
                                    long bloqueoMinutos) {
        this.usuarios = usuarios;
        this.cifrador = cifrador;
        this.segundoFactor = segundoFactor;
        this.intentos = intentos;
        this.emisorDeSesion = emisorDeSesion;
        this.auditoria = auditoria;
        this.maximoIntentos = maximoIntentos;
        this.maximoIntentosGlobales = maximoIntentos * FACTOR_TOPE_GLOBAL;
        this.ventanaIntentos = Duration.ofMinutes(ventanaIntentosMinutos);
        this.bloqueo = Duration.ofMinutes(bloqueoMinutos);
    }

    @Override
    public SesionEmitida autenticar(CorreoElectronico correo, String claveEnClaro, String codigoTotp,
                                    ContextoDeAccion contexto) {
        return autenticarComo(false, correo, claveEnClaro, codigoTotp, contexto);
    }

    @Override
    public SesionEmitida autenticarVecino(CorreoElectronico correo, String claveEnClaro,
                                          ContextoDeAccion contexto) {
        return autenticarComo(true, correo, claveEnClaro, null, contexto);
    }

    private SesionEmitida autenticarComo(boolean comoVecino, CorreoElectronico correo, String claveEnClaro,
                                         String codigoTotp, ContextoDeAccion contexto) {
        String cuenta = correo.normalizado().valor();
        String clave = cuenta + "|" + contexto.ip();

        Optional<Duration> bloqueoVigente = bloqueoVigente(clave, cuenta);
        if (bloqueoVigente.isPresent()) {
            throw new CuentaBloqueadaException(bloqueoVigente.get(),
                    "Demasiados intentos fallidos. Vuelve a intentarlo en "
                            + Math.max(1, bloqueoVigente.get().toMinutes()) + " minutos.");
        }

        Optional<Usuario> encontrado = usuarios.buscarPorCorreo(correo.normalizado());
        // Las cuentas sintéticas no tienen titular: comparten una clave precalculada, así que se tratan como un
        // correo inexistente aunque alguien la acierte. Las de panel de demostración (VEEDOR/OBSERVADOR) no son
        // sintéticas en este sentido y sí pueden entrar.
        if (encontrado.isEmpty() || encontrado.get().esSintetica()) {
            // Se gasta el mismo tiempo que un BCrypt real y no se audita: auditar cada intento
            // contra un correo inventado dejaría que cualquiera llenara la tabla de auditoría desde
            // fuera. Para ese caso ya está el límite por IP de ADR-018.
            cifrador.gastarTiempoEquivalente();
            registrarFallo(clave);
            throw new CredencialInvalidaException("Correo o clave incorrectos.");
        }

        Usuario usuario = encontrado.get();

        boolean claveCorrecta;
        if (usuario.claveHash() == null) {
            // Una invitación pendiente no tiene hash que comparar: se gasta igual el tiempo de un BCrypt, o el cronómetro
            // diría qué correos tienen una invitación sin aceptar.
            cifrador.gastarTiempoEquivalente();
            claveCorrecta = false;
        } else {
            claveCorrecta = cifrador.coincide(claveEnClaro, usuario.claveHash());
        }
        if (!claveCorrecta) {
            fallar(usuario, "Clave incorrecta", clave, contexto);
            throw new CredencialInvalidaException("Correo o clave incorrectos.");
        }

        // Cada ingreso solo abre su propia puerta. Se responde igual que a una clave mala: si no, el
        // ingreso diría qué correos son de vecinos y cuáles del panel. Va tras validar la clave por
        // la misma razón que el estado de la cuenta (punto 3 de la cabecera).
        if (usuario.esVecino() != comoVecino) {
            fallar(usuario, comoVecino
                    ? "Cuenta del panel en el ingreso de vecinos"
                    : "Cuenta de vecino en el ingreso del panel", clave, contexto);
            throw new CredencialInvalidaException("Correo o clave incorrectos.");
        }

        if (!usuario.estado().permiteIniciarSesion()) {
            throw new CuentaNoHabilitadaException(usuario.estado(), motivo(usuario.estado()));
        }

        if (usuario.tieneSegundoFactorConfirmado()) {
            exigirSegundoFactor(usuario, codigoTotp, clave, contexto);
        }

        intentos.limpiarIntentos(clave);
        intentos.limpiarIntentos(cuenta);
        intentos.limpiarIntentos(PREFIJO_GLOBAL + cuenta);

        AlcanceSesion alcance = usuario.debeCompletarAltaDeSegundoFactor()
                ? AlcanceSesion.ALTA_SEGUNDO_FACTOR
                : AlcanceSesion.COMPLETO;

        SesionEmitida sesion = SesionEmitida.de(usuario, emisorDeSesion.emitir(usuario, alcance), alcance);
        auditoria.registrarConAutor(AccionAuditada.SESION_INICIADA, usuario, usuario,
                "Ingreso correcto con alcance " + alcance, contexto);
        return sesion;
    }

    private void exigirSegundoFactor(Usuario usuario, String codigoTotp, String claveIntentos,
                                     ContextoDeAccion contexto) {
        if (codigoTotp == null || codigoTotp.isBlank()) {
            // No cuenta como fallo: la clave era correcta y todavía no se ha pedido el código.
            // Contarlo bloquearía a quien hace exactamente lo que el flujo espera de él.
            throw new SegundoFactorRequeridoException("Esta cuenta pide el código de tu app de autenticación.");
        }

        String codigo = codigoTotp.strip();
        boolean valido = segundoFactor.codigoEsValido(usuario.segundoFactor().secreto(), codigo)
                && intentos.consumirPorPrimeraVez(
                        "totp:" + usuario.id().valor() + ":" + codigo, VENTANA_ANTIRREPLAY_TOTP);

        if (!valido) {
            fallar(usuario, "Código de segundo factor inválido o ya usado", claveIntentos, contexto);
            throw new CredencialInvalidaException("Correo o clave incorrectos.");
        }
    }

    private void registrarFallo(String claveConIp) {
        intentos.registrarFallo(claveConIp, ventanaIntentos, maximoIntentos, bloqueo);
        String cuenta = claveConIp.substring(0, claveConIp.lastIndexOf('|'));
        intentos.registrarFallo(PREFIJO_GLOBAL + cuenta, ventanaIntentos, maximoIntentosGlobales, bloqueo);
    }

    private Optional<Duration> bloqueoVigente(String claveConIp, String cuenta) {
        Optional<Duration> porIp = intentos.bloqueoVigente(claveConIp);
        Optional<Duration> global = intentos.bloqueoVigente(PREFIJO_GLOBAL + cuenta);
        if (porIp.isPresent() && global.isPresent()) {
            return porIp.get().compareTo(global.get()) >= 0 ? porIp : global;
        }
        return porIp.isPresent() ? porIp : global;
    }

    private void fallar(Usuario usuario, String detalle, String claveIntentos, ContextoDeAccion contexto) {
        registrarFallo(claveIntentos);
        auditoria.registrarConAutor(AccionAuditada.SESION_RECHAZADA, usuario, usuario, detalle, contexto);
    }

    private static String motivo(EstadoCuenta estado) {
        return switch (estado) {
            case PENDIENTE_VERIFICACION ->
                    "Todavía no confirmaste tu correo. Busca el enlace que te enviamos.";
            case PENDIENTE_APROBACION ->
                    "Tu cuenta espera la aprobación de un administrador. Te avisaremos por correo.";
            case INVITADA ->
                    "Tienes una invitación sin aceptar. Abre el enlace del correo para fijar tu clave.";
            case SUSPENDIDA ->
                    "Tu cuenta está suspendida. Contacta a un administrador.";
            case RECHAZADA ->
                    "Tu solicitud de acceso fue denegada.";
            case ACTIVA ->
                    "La cuenta está activa.";
        };
    }
}
