package com.aguavigia.ctg.domain;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Cuenta del panel del veedor, o de un vecino registrado (rol VECINO, ADR-089). Sustituye a la credencial compartida
 * de ADR-016.
 *
 * Todas las transiciones de estado viven aquí y devuelven una copia nueva: si estuvieran en los
 * servicios, cada nuevo caso de uso podría inventarse su propio camino hasta ACTIVA, y "aprobada"
 * dejaría de significar lo mismo en todo el sistema. La regla que sostiene el resto es simple —
 * ninguna cuenta llega a ACTIVA sin clave, y solo ACTIVA autentica.
 */
public record Usuario(
        UsuarioId id,
        CorreoElectronico correo,
        String nombre,
        ClaveHash claveHash,
        EstadoCuenta estado,
        PermisosEfectivos permisos,
        SegundoFactor segundoFactor,
        Instant creadoEn,
        Instant actualizadoEn,
        SectorId barrio,
        List<Consentimiento> consentimientos,
        boolean barrioVerificado,
        Instant barrioVerificadoEn,
        /**
         * Cuenta sintética (D20): la creó el sistema para probar el volumen, no una persona. Se declara tal cual en
         * `GET /api/sistema/modo`; no son adopción.
         */
        boolean datosDeDemostracion) {

    /** Una cuenta real: lo que existía antes de las cuentas sintéticas. */
    public Usuario(UsuarioId id, CorreoElectronico correo, String nombre, ClaveHash claveHash,
                   EstadoCuenta estado, PermisosEfectivos permisos, SegundoFactor segundoFactor,
                   Instant creadoEn, Instant actualizadoEn, SectorId barrio, List<Consentimiento> consentimientos,
                   boolean barrioVerificado, Instant barrioVerificadoEn) {
        this(id, correo, nombre, claveHash, estado, permisos, segundoFactor, creadoEn, actualizadoEn, barrio,
                consentimientos, barrioVerificado, barrioVerificadoEn, false);
    }

    /** Sin barrio: el ADMIN inicial y las cuentas anteriores a ADR-081 no lo tienen. */
    public Usuario(UsuarioId id, CorreoElectronico correo, String nombre, ClaveHash claveHash,
                   EstadoCuenta estado, PermisosEfectivos permisos, SegundoFactor segundoFactor,
                   Instant creadoEn, Instant actualizadoEn) {
        this(id, correo, nombre, claveHash, estado, permisos, segundoFactor, creadoEn, actualizadoEn, null);
    }

    /** Cuenta del panel: no lleva consentimientos ni verificación de barrio, que son cosa del vecino. */
    public Usuario(UsuarioId id, CorreoElectronico correo, String nombre, ClaveHash claveHash,
                   EstadoCuenta estado, PermisosEfectivos permisos, SegundoFactor segundoFactor,
                   Instant creadoEn, Instant actualizadoEn, SectorId barrio) {
        this(id, correo, nombre, claveHash, estado, permisos, segundoFactor, creadoEn, actualizadoEn, barrio,
                List.of(), false, null);
    }

    public Usuario {
        if (id == null) {
            throw new IllegalArgumentException("El usuario debe tener id");
        }
        if (correo == null) {
            throw new IllegalArgumentException("El usuario debe tener correo");
        }
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("El usuario debe tener nombre");
        }
        if (estado == null) {
            throw new IllegalArgumentException("El usuario debe tener estado");
        }
        if (permisos == null) {
            throw new IllegalArgumentException("El usuario debe tener permisos");
        }
        if (creadoEn == null) {
            throw new IllegalArgumentException("El usuario debe tener fecha de creación");
        }
        if (estado.permiteIniciarSesion() && claveHash == null) {
            throw new IllegalArgumentException("Una cuenta activa no puede estar sin clave");
        }
        if (permisos.rol() == RolVeedor.VECINO && barrio == null) {
            throw new IllegalArgumentException("Un vecino debe declarar su barrio");
        }
        consentimientos = consentimientos == null ? List.of() : List.copyOf(consentimientos);
        if (barrioVerificado && (barrio == null || barrioVerificadoEn == null)) {
            throw new IllegalArgumentException("Un barrio verificado necesita barrio y fecha de verificación");
        }
    }

    /** Auto-registro: nace sin permisos útiles y sin poder entrar hasta verificar y ser aprobada. */
    public static Usuario registrado(UsuarioId id, CorreoElectronico correo, String nombre,
                                     ClaveHash claveHash, Instant momento) {
        return registrado(id, correo, nombre, claveHash, null, momento);
    }

    /** `barrio` es opcional: el barrio donde vive quien se registra (ADR-081). */
    public static Usuario registrado(UsuarioId id, CorreoElectronico correo, String nombre,
                                     ClaveHash claveHash, SectorId barrio, Instant momento) {
        if (claveHash == null) {
            throw new IllegalArgumentException("Quien se registra debe fijar una clave");
        }
        return new Usuario(id, correo, nombre, claveHash, EstadoCuenta.PENDIENTE_VERIFICACION,
                PermisosEfectivos.deRol(RolVeedor.OBSERVADOR), null, momento, momento, barrio);
    }

    /**
     * Alta por invitación: el rol ya viene decidido por quien invita, y el correo se da por probado
     * — la invitación llegó a esa dirección. Falta solo que la persona fije su clave.
     */
    public static Usuario invitado(UsuarioId id, CorreoElectronico correo, String nombre,
                                   RolVeedor rol, Instant momento) {
        return invitado(id, correo, nombre, rol, null, momento);
    }

    public static Usuario invitado(UsuarioId id, CorreoElectronico correo, String nombre,
                                   RolVeedor rol, SectorId barrio, Instant momento) {
        exigirRolDePanel(rol);
        return new Usuario(id, correo, nombre, null, EstadoCuenta.INVITADA,
                PermisosEfectivos.deRol(rol), null, momento, momento, barrio);
    }

    public Usuario verificarCorreo(Instant momento) {
        if (estado != EstadoCuenta.PENDIENTE_VERIFICACION) {
            throw new IllegalStateException("Esta cuenta no está esperando verificación de correo");
        }
        // Un vecino nunca pasa por aquí: nace INVITADA y se activa al fijar su clave desde el enlace.
        return copiaCon(claveHash, EstadoCuenta.PENDIENTE_APROBACION, permisos, segundoFactor, momento);
    }

    public Usuario aceptarInvitacion(ClaveHash nuevaClave, Instant momento) {
        if (estado != EstadoCuenta.INVITADA) {
            throw new IllegalStateException("Esta cuenta no tiene una invitación pendiente");
        }
        if (nuevaClave == null) {
            throw new IllegalArgumentException("Aceptar la invitación exige fijar una clave");
        }
        Usuario activa = copiaCon(nuevaClave, EstadoCuenta.ACTIVA, permisos, segundoFactor, momento);
        if (!esVecino()) {
            return activa;
        }
        // Lo que un vecino acepta vale desde que la persona del correo actúa, no desde que alguien rellenó el
        // formulario con su dirección.
        List<Consentimiento> aceptados = consentimientos.stream()
                .map(c -> new Consentimiento(c.tipo(), c.version(), momento))
                .toList();
        return activa.copiaDeVecino(nombre, barrio, aceptados, barrioVerificado, barrioVerificadoEn, momento);
    }

    public Usuario aprobar(PermisosEfectivos permisosAsignados, Instant momento) {
        if (estado != EstadoCuenta.PENDIENTE_APROBACION) {
            throw new IllegalStateException(
                    "Solo se aprueba una cuenta que ya verificó su correo y espera aprobación");
        }
        if (permisosAsignados == null) {
            throw new IllegalArgumentException("Aprobar exige decir con qué permisos");
        }
        exigirRolDePanel(permisosAsignados.rol());
        return copiaCon(claveHash, EstadoCuenta.ACTIVA, permisosAsignados, segundoFactor, momento);
    }

    public Usuario rechazar(Instant momento) {
        if (estado == EstadoCuenta.ACTIVA || estado == EstadoCuenta.SUSPENDIDA) {
            throw new IllegalStateException("Una cuenta que ya entró en servicio se suspende, no se rechaza");
        }
        if (estado == EstadoCuenta.RECHAZADA) {
            throw new IllegalStateException("Esta cuenta ya estaba rechazada");
        }
        return copiaCon(claveHash, EstadoCuenta.RECHAZADA, permisos, segundoFactor, momento);
    }

    public Usuario suspender(Instant momento) {
        if (estado != EstadoCuenta.ACTIVA) {
            throw new IllegalStateException("Solo se suspende una cuenta activa");
        }
        return copiaCon(claveHash, EstadoCuenta.SUSPENDIDA, permisos, segundoFactor, momento);
    }

    public Usuario reactivar(Instant momento) {
        if (estado != EstadoCuenta.SUSPENDIDA) {
            throw new IllegalStateException("Solo se reactiva una cuenta suspendida");
        }
        return copiaCon(claveHash, EstadoCuenta.ACTIVA, permisos, segundoFactor, momento);
    }

    public Usuario cambiarPermisos(PermisosEfectivos nuevos, Instant momento) {
        if (nuevos == null) {
            throw new IllegalArgumentException("Los permisos nuevos no pueden ser nulos");
        }
        if (estado == EstadoCuenta.RECHAZADA) {
            throw new IllegalStateException("Una cuenta rechazada no tiene permisos que cambiar");
        }
        // Una cuenta no cruza entre vecino y panel en ninguna dirección: el panel solo se alcanza
        // por registro de panel o invitación, y un vecino se queda siendo vecino.
        exigirRolDePanel(nuevos.rol());
        if (permisos.rol() == RolVeedor.VECINO) {
            throw new IllegalArgumentException("La cuenta de un vecino no puede pasar a un rol del panel");
        }
        return copiaCon(claveHash, estado, nuevos, segundoFactor, momento);
    }

    public Usuario cambiarClave(ClaveHash nueva, Instant momento) {
        if (nueva == null) {
            throw new IllegalArgumentException("La clave nueva no puede ser nula");
        }
        if (estado == EstadoCuenta.RECHAZADA) {
            throw new IllegalStateException("Una cuenta rechazada no puede cambiar su clave");
        }
        return copiaCon(nueva, estado, permisos, segundoFactor, momento);
    }

    /** Genera el secreto, pero no exige nada todavía: ver el javadoc de SegundoFactor. */
    public Usuario iniciarSegundoFactor(SecretoTotp secreto, Instant momento) {
        return copiaCon(claveHash, estado, permisos, SegundoFactor.sinConfirmar(secreto), momento);
    }

    public Usuario confirmarSegundoFactor(Instant momento) {
        if (segundoFactor == null) {
            throw new IllegalStateException("No hay un alta de segundo factor en curso");
        }
        return copiaCon(claveHash, estado, permisos, segundoFactor.confirmar(momento), momento);
    }

    public Usuario desactivarSegundoFactor(Instant momento) {
        if (permisos.rol().exigeSegundoFactor()) {
            throw new IllegalStateException(
                    "El rol " + permisos.rol() + " exige segundo factor: no se puede desactivar");
        }
        return copiaCon(claveHash, estado, permisos, null, momento);
    }

    public boolean tieneSegundoFactorConfirmado() {
        return segundoFactor != null && segundoFactor.estaConfirmado();
    }

    /** Un ADMIN sin TOTP confirmado tiene que darlo de alta antes de poder hacer nada más. */
    public boolean debeCompletarAltaDeSegundoFactor() {
        return permisos.rol().exigeSegundoFactor() && !tieneSegundoFactorConfirmado();
    }

    public Set<Permiso> permisosEfectivos() {
        return permisos.resolver();
    }

    private static void exigirRolDePanel(RolVeedor rol) {
        if (rol == RolVeedor.VECINO) {
            throw new IllegalArgumentException(
                    "El rol VECINO nace del registro de vecinos; no se asigna desde el panel");
        }
    }

    private Usuario copiaCon(ClaveHash nuevaClave, EstadoCuenta nuevoEstado, PermisosEfectivos nuevosPermisos,
                             SegundoFactor nuevoSegundoFactor, Instant momento) {
        if (momento == null) {
            throw new IllegalArgumentException("Todo cambio en la cuenta necesita un instante");
        }
        return new Usuario(id, correo, nombre, nuevaClave, nuevoEstado, nuevosPermisos,
                nuevoSegundoFactor, creadoEn, momento, barrio, consentimientos, barrioVerificado,
                barrioVerificadoEn, datosDeDemostracion);
    }

    // --- Vecino registrado (D11) ---

    /**
     * Registro abierto de un vecino: barrio obligatorio y consentimiento de privacidad. Nace INVITADA y sin
     * clave: la fija quien abre el enlace del correo (aceptarInvitacion), y solo entonces queda ACTIVA, sin
     * aprobación de nadie. Si eligiera la clave quien rellena el formulario, podría registrar el correo de otra
     * persona con una clave suya y quedarse con la cuenta cuando ella confirmara el enlace.
     */
    public static Usuario registradoComoVecino(UsuarioId id, CorreoElectronico correo, String nombre,
                                               SectorId barrio, List<Consentimiento> consentimientos,
                                               Instant momento) {
        if (barrio == null) {
            throw new IllegalArgumentException("Un vecino debe declarar su barrio");
        }
        if (consentimientos == null
                || consentimientos.stream().noneMatch(c -> c.tipo() == TipoConsentimiento.PRIVACIDAD)) {
            throw new IllegalArgumentException("Un vecino debe aceptar el aviso de privacidad");
        }
        return new Usuario(id, correo, nombre, null, EstadoCuenta.INVITADA,
                PermisosEfectivos.deRol(RolVeedor.VECINO), null, momento, momento, barrio,
                consentimientos, false, null);
    }

    /**
     * Una cuenta sintética de vecino (D20, D36). Nace INVITADA como cualquier vecino y la activa el sistema
     * (`aceptarInvitacion` con una clave compartida de la que nadie conoce la contraseña). No finge nada que nadie hizo: sin
     * consentimiento, sin barrio verificado y con un correo de dominio reservado que no existe.
     */
    public static Usuario sinteticoComoVecino(UsuarioId id, CorreoElectronico correo, String nombre, SectorId barrio,
                                              Instant momento) {
        if (barrio == null) {
            throw new IllegalArgumentException("Un vecino debe declarar su barrio");
        }
        return new Usuario(id, correo, nombre, null, EstadoCuenta.INVITADA, PermisosEfectivos.deRol(RolVeedor.VECINO),
                null, momento, momento, barrio, List.of(), false, null, true);
    }

    public boolean esVecino() {
        return permisos.rol() == RolVeedor.VECINO;
    }

    /**
     * Una cuenta sintética de vecino (D20): nadie es su titular. Las cuentas de panel de demostración que siembra
     * `sembrar-usuarios-demo.mjs` también llevan `datosDeDemostracion`, pero tienen claves conocidas y sí se usan.
     */
    public boolean esSintetica() {
        return datosDeDemostracion && esVecino();
    }

    public Usuario verificarBarrio(Instant momento) {
        exigirVecino();
        return copiaDeVecino(nombre, barrio, consentimientos, true, momento, momento);
    }

    /** Declarar otro barrio invalida la verificación: probaba que vivía en el anterior. */
    public Usuario mudarDeBarrio(SectorId nuevoBarrio, Instant momento) {
        exigirVecino();
        if (nuevoBarrio == null) {
            throw new IllegalArgumentException("El barrio nuevo no puede ser nulo");
        }
        if (nuevoBarrio.equals(barrio)) {
            return this;
        }
        return copiaDeVecino(nombre, nuevoBarrio, consentimientos, false, null, momento);
    }

    public Usuario renombrar(String nuevoNombre, Instant momento) {
        exigirVecino();
        if (nuevoNombre == null || nuevoNombre.isBlank()) {
            throw new IllegalArgumentException("El nombre no puede estar vacío");
        }
        return copiaDeVecino(nuevoNombre.strip(), barrio, consentimientos, barrioVerificado,
                barrioVerificadoEn, momento);
    }

    public Usuario consentirAvisos(String version, Instant momento) {
        exigirVecino();
        List<Consentimiento> nuevos = new java.util.ArrayList<>(sinAvisos());
        nuevos.add(new Consentimiento(TipoConsentimiento.AVISOS, version, momento));
        return copiaDeVecino(nombre, barrio, nuevos, barrioVerificado, barrioVerificadoEn, momento);
    }

    public Usuario retirarConsentimientoDeAvisos(Instant momento) {
        exigirVecino();
        return copiaDeVecino(nombre, barrio, sinAvisos(), barrioVerificado, barrioVerificadoEn, momento);
    }

    public boolean recibeAvisos() {
        return consentimientos.stream().anyMatch(c -> c.tipo() == TipoConsentimiento.AVISOS);
    }

    private List<Consentimiento> sinAvisos() {
        return consentimientos.stream().filter(c -> c.tipo() != TipoConsentimiento.AVISOS).toList();
    }

    private void exigirVecino() {
        if (!esVecino()) {
            throw new IllegalStateException("Esta acción solo existe para la cuenta de un vecino");
        }
    }

    private Usuario copiaDeVecino(String nuevoNombre, SectorId nuevoBarrio,
                                  List<Consentimiento> nuevosConsentimientos, boolean verificado,
                                  Instant verificadoEn, Instant momento) {
        if (momento == null) {
            throw new IllegalArgumentException("Todo cambio en la cuenta necesita un instante");
        }
        return new Usuario(id, correo, nuevoNombre, claveHash, estado, permisos, segundoFactor,
                creadoEn, momento, nuevoBarrio, nuevosConsentimientos, verificado, verificadoEn, datosDeDemostracion);
    }
}
