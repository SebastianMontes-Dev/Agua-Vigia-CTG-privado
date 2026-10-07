package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.AccionAuditada;
import com.aguavigia.ctg.domain.AlcanceSesion;
import com.aguavigia.ctg.domain.ContextoDeAccion;
import com.aguavigia.ctg.domain.CuentaNoHabilitadaException;
import com.aguavigia.ctg.domain.RolVeedor;
import com.aguavigia.ctg.domain.SesionEmitida;
import com.aguavigia.ctg.domain.Usuario;
import com.aguavigia.ctg.domain.port.in.IniciarSesionDeAdminDeSimulacionUseCase;
import com.aguavigia.ctg.domain.port.out.EmisorDeSesionPort;
import com.aguavigia.ctg.domain.port.out.UsuarioRepository;

public class IniciarSesionDeAdminDeSimulacionService implements IniciarSesionDeAdminDeSimulacionUseCase {

    private final UsuarioRepository usuarios;
    private final EmisorDeSesionPort emisorDeSesion;
    private final RegistroDeAuditoria auditoria;

    public IniciarSesionDeAdminDeSimulacionService(UsuarioRepository usuarios, EmisorDeSesionPort emisorDeSesion,
                                                   RegistroDeAuditoria auditoria) {
        this.usuarios = usuarios;
        this.emisorDeSesion = emisorDeSesion;
        this.auditoria = auditoria;
    }

    @Override
    public SesionEmitida iniciar(ContextoDeAccion contexto) {
        Usuario admin = usuarios.buscarPrimeroPorRol(RolVeedor.ADMIN)
                .orElseThrow(() -> new IllegalStateException("La instancia no tiene ninguna cuenta ADMIN: arranca el backend con ADMIN_INICIAL_CORREO"));
        if (!admin.estado().permiteIniciarSesion()) {
            throw new CuentaNoHabilitadaException(admin.estado(), "La cuenta ADMIN no está habilitada para entrar");
        }
        SesionEmitida sesion = SesionEmitida.de(admin, emisorDeSesion.emitir(admin, AlcanceSesion.COMPLETO), AlcanceSesion.COMPLETO);
        // Ninguna otra vía abre una sesión de ADMIN sin segundo factor: queda dicho en la auditoría.
        auditoria.registrarConAutor(AccionAuditada.SESION_INICIADA, admin, admin,
                "Ingreso por la ruta de simulación, sin clave ni segundo factor", contexto);
        return sesion;
    }
}
