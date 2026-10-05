package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.AccionAuditada;
import com.aguavigia.ctg.domain.CambiosDePerfil;
import com.aguavigia.ctg.domain.ContextoDeAccion;
import com.aguavigia.ctg.domain.SesionSinCuentaException;
import com.aguavigia.ctg.domain.Usuario;
import com.aguavigia.ctg.domain.UsuarioId;
import com.aguavigia.ctg.domain.port.in.ActualizarPerfilVecinoUseCase;
import com.aguavigia.ctg.domain.port.out.RelojPort;
import com.aguavigia.ctg.domain.port.out.SectorRepository;
import com.aguavigia.ctg.domain.port.out.UsuarioRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Cada cambio de consentimiento y de barrio queda auditado, pero la auditoría dice qué campo cambió,
 * no su valor: el nombre nuevo es dato personal y no tiene por qué repetirse en otra colección.
 */
public class ActualizarPerfilVecinoService implements ActualizarPerfilVecinoUseCase {

    private final UsuarioRepository usuarios;
    private final RegistroDeAuditoria auditoria;
    private final RelojPort reloj;
    private final SectorRepository sectores;
    private final String versionPrivacidad;

    public ActualizarPerfilVecinoService(UsuarioRepository usuarios, RegistroDeAuditoria auditoria,
                                         RelojPort reloj, SectorRepository sectores,
                                         String versionPrivacidad) {
        this.usuarios = usuarios;
        this.auditoria = auditoria;
        this.reloj = reloj;
        this.sectores = sectores;
        this.versionPrivacidad = versionPrivacidad;
    }

    @Override
    public Usuario actualizar(UsuarioId vecino, CambiosDePerfil cambios, ContextoDeAccion contexto) {
        Usuario actual = usuarios.buscarPorId(vecino)
                .orElseThrow(() -> new SesionSinCuentaException("La sesión ya no corresponde a una cuenta"));
        if (!actual.esVecino()) {
            throw new IllegalStateException("Esta acción solo existe para la cuenta de un vecino");
        }
        if (cambios.barrio() != null && sectores.buscarPorId(cambios.barrio()).isEmpty()) {
            throw new IllegalArgumentException("No existe el barrio '" + cambios.barrio().valor() + "'");
        }

        Instant ahora = reloj.ahora();
        Usuario nuevo = actual;
        List<String> cambiados = new ArrayList<>();

        if (cambios.nombre() != null && !cambios.nombre().strip().equals(actual.nombre())) {
            nuevo = nuevo.renombrar(cambios.nombre(), ahora);
            cambiados.add("nombre");
        }
        if (cambios.barrio() != null && !cambios.barrio().equals(actual.barrio())) {
            nuevo = nuevo.mudarDeBarrio(cambios.barrio(), ahora);
            cambiados.add(actual.barrioVerificado() ? "barrio (la verificación anterior deja de valer)" : "barrio");
        }
        if (cambios.recibirAvisos() != null && cambios.recibirAvisos() != actual.recibeAvisos()) {
            nuevo = cambios.recibirAvisos()
                    ? nuevo.consentirAvisos(versionPrivacidad, ahora)
                    : nuevo.retirarConsentimientoDeAvisos(ahora);
            cambiados.add(cambios.recibirAvisos() ? "avisos activados" : "avisos retirados");
        }

        if (cambiados.isEmpty()) {
            return actual;
        }
        Usuario guardado = usuarios.guardarSiNoCambio(nuevo, actual.actualizadoEn());
        auditoria.registrarConAutor(AccionAuditada.PERFIL_ACTUALIZADO, guardado, guardado,
                "Perfil actualizado: " + String.join(", ", cambiados), contexto);
        return guardado;
    }
}
