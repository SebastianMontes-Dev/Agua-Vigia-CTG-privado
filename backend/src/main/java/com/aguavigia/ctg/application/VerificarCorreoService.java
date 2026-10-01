package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.AccionAuditada;
import com.aguavigia.ctg.domain.ContextoDeAccion;
import com.aguavigia.ctg.domain.TipoTokenCuenta;
import com.aguavigia.ctg.domain.Usuario;
import com.aguavigia.ctg.domain.port.in.VerificarCorreoUseCase;
import com.aguavigia.ctg.domain.port.out.RelojPort;
import com.aguavigia.ctg.domain.port.out.UsuarioRepository;

/**
 * Verificar el correo no da acceso al panel: mueve la cuenta a PENDIENTE_APROBACION y ahí se queda
 * hasta que un ADMIN decida. Es el punto donde el registro abierto deja de ser un riesgo. La cuenta
 * de un vecino es la excepción (D11): no tiene permisos de panel, así que pasa directo a ACTIVA.
 */
public class VerificarCorreoService implements VerificarCorreoUseCase {

    private final UsuarioRepository usuarios;
    private final EmisorDeTokensDeCuenta emisorDeTokens;
    private final RegistroDeAuditoria auditoria;
    private final RelojPort reloj;

    public VerificarCorreoService(UsuarioRepository usuarios,
                                  EmisorDeTokensDeCuenta emisorDeTokens,
                                  RegistroDeAuditoria auditoria,
                                  RelojPort reloj) {
        this.usuarios = usuarios;
        this.emisorDeTokens = emisorDeTokens;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    @Override
    public Usuario verificar(String tokenEnClaro, ContextoDeAccion contexto) {
        Usuario usuario = emisorDeTokens.consumir(tokenEnClaro, TipoTokenCuenta.VERIFICACION_CORREO);
        Usuario verificado = usuarios.guardar(usuario.verificarCorreo(reloj.ahora()));

        auditoria.registrarConAutor(AccionAuditada.CORREO_VERIFICADO, verificado, verificado,
                verificado.esVecino()
                        ? "Correo verificado; la cuenta de vecino queda activa"
                        : "Correo verificado; espera aprobación de un administrador",
                contexto);
        return verificado;
    }
}
