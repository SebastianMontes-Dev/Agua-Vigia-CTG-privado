package com.aguavigia.ctg.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Un rol es un paquete de permisos con nombre, no una categoría aparte: el sistema autoriza
 * siempre por Permiso (ver esa clase). Sobre este paquete, el admin puede conceder o revocar
 * permisos sueltos por persona — la resolución vive en PermisosEfectivos.
 */
public enum RolVeedor {

    /** Solo lectura del panel. Sirve para que alguien acompañe la moderación sin poder ejecutarla. */
    OBSERVADOR(Set.of(
            Permiso.VER_PANEL,
            Permiso.CONFIGURAR_SEGUNDO_FACTOR)),

    /** El trabajo diario de veeduría: moderar, registrar y cerrar cortes, revisar la ingesta. */
    VEEDOR(Set.of(
            Permiso.VER_PANEL,
            Permiso.MODERAR_REPORTES,
            Permiso.GESTIONAR_CORTES,
            Permiso.REVISAR_INGESTA,
            Permiso.CONFIGURAR_SEGUNDO_FACTOR)),

    /**
     * Todo lo anterior más la gestión de cuentas y la auditoría. Exige segundo factor. No lleva el permiso del
     * vecino: ese es solo de quien gestiona su propio perfil, y heredarlo obligaría a casos especiales donde se
     * decide quién cuenta como vecino.
     */
    ADMIN(EnumSet.complementOf(EnumSet.of(Permiso.GESTIONAR_PERFIL_PROPIO))),

    /**
     * Cuenta ciudadana que se registra sola (D11). Solo gestiona su propio perfil: nunca recibe
     * permisos de panel, ni por el rol ni concedidos a mano (lo impide PermisosEfectivos).
     */
    VECINO(Set.of(Permiso.GESTIONAR_PERFIL_PROPIO));

    private final Set<Permiso> permisosBase;

    RolVeedor(Set<Permiso> permisosBase) {
        this.permisosBase = Set.copyOf(permisosBase);
    }

    public Set<Permiso> permisosBase() {
        return permisosBase;
    }

    /**
     * RNF011 y la decisión de esta ADR: la cuenta que puede crear y despromover cuentas es la que
     * más daño hace si se la roban, así que el segundo factor no es opcional para ella.
     */
    public boolean exigeSegundoFactor() {
        return this == ADMIN;
    }
}
