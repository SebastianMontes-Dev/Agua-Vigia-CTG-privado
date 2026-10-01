package com.aguavigia.ctg.api;

import com.aguavigia.ctg.domain.AlcanceSesion;
import com.aguavigia.ctg.domain.Permiso;
import com.aguavigia.ctg.domain.SesionAutenticada;

import java.time.Instant;
import java.util.Set;

/**
 * Arma la sesión que un token válido habría producido, para las pruebas de controlador que solo
 * quieren llegar al método y no ejercitar el login.
 *
 * Cada prueba declara los permisos que su endpoint necesita en vez de recibir una sesión con todo:
 * así, si alguien cambia el `@PreAuthorize` de un endpoint, la prueba de otro no lo tapa.
 */
final class AutenticacionDePrueba {

    static final String USUARIO_ID = "11111111-1111-1111-1111-111111111111";

    private AutenticacionDePrueba() {
    }

    /** La sesión de un vecino registrado: el rol VECINO y su único permiso. */
    static SesionAutenticada sesionDeVecino() {
        return new SesionAutenticada(
                USUARIO_ID,
                "vecina@aguavigia.test",
                "Vecina de prueba",
                "VECINO",
                Set.of(Permiso.GESTIONAR_PERFIL_PROPIO),
                AlcanceSesion.COMPLETO,
                Instant.parse("2026-08-09T20:00:00Z"));
    }

    /** Un ADMIN: tiene GESTIONAR_PERFIL_PROPIO por heredar todos los permisos, pero no es un vecino. */
    static SesionAutenticada sesionDeAdmin() {
        return new SesionAutenticada(
                USUARIO_ID,
                "admin@aguavigia.test",
                "Admin de prueba",
                "ADMIN",
                Set.of(Permiso.values()),
                AlcanceSesion.COMPLETO,
                Instant.parse("2026-08-09T20:00:00Z"));
    }

    static SesionAutenticada sesionCon(Permiso... permisos) {
        return new SesionAutenticada(
                USUARIO_ID,
                "veedor@aguavigia.test",
                "Veedor de prueba",
                "VEEDOR",
                Set.of(permisos),
                AlcanceSesion.COMPLETO,
                Instant.parse("2026-08-09T20:00:00Z"));
    }
}
