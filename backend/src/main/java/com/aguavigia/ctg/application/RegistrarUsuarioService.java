package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.AccionAuditada;
import com.aguavigia.ctg.domain.ClaveEnClaro;
import com.aguavigia.ctg.domain.ContextoDeAccion;
import com.aguavigia.ctg.domain.CorreoElectronico;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.TipoTokenCuenta;
import com.aguavigia.ctg.domain.Usuario;
import com.aguavigia.ctg.domain.UsuarioId;
import com.aguavigia.ctg.domain.port.in.RegistrarUsuarioUseCase;
import com.aguavigia.ctg.domain.port.out.CifradorClavePort;
import com.aguavigia.ctg.domain.port.out.NotificacionCuentaPort;
import com.aguavigia.ctg.domain.port.out.RelojPort;
import com.aguavigia.ctg.domain.port.out.SectorRepository;
import com.aguavigia.ctg.domain.port.out.TiempoConstantePort;
import com.aguavigia.ctg.domain.port.out.UsuarioRepository;

import java.util.UUID;

/**
 * Registro abierto con dos frenos: el correo hay que probarlo, y un ADMIN tiene que aprobar. Sin
 * el segundo, "registro abierto" sería "cualquiera entra al panel de moderación".
 *
 * Si el correo ya tiene cuenta no se avisa a quien rellenó el formulario — se avisa al dueño de la
 * dirección, por correo. Así el formulario no sirve para averiguar qué correos están registrados, y
 * de paso el titular se entera de que alguien intentó usar el suyo.
 */
public class RegistrarUsuarioService implements RegistrarUsuarioUseCase {

    private final UsuarioRepository usuarios;
    private final CifradorClavePort cifrador;
    private final EmisorDeTokensDeCuenta emisorDeTokens;
    private final NotificacionCuentaPort notificaciones;
    private final RegistroDeAuditoria auditoria;
    private final RelojPort reloj;
    private final SectorRepository sectores;
    private final TiempoConstantePort tiempoConstante;

    public RegistrarUsuarioService(UsuarioRepository usuarios,
                                   CifradorClavePort cifrador,
                                   EmisorDeTokensDeCuenta emisorDeTokens,
                                   NotificacionCuentaPort notificaciones,
                                   RegistroDeAuditoria auditoria,
                                   RelojPort reloj,
                                   SectorRepository sectores,
                                   TiempoConstantePort tiempoConstante) {
        this.usuarios = usuarios;
        this.cifrador = cifrador;
        this.emisorDeTokens = emisorDeTokens;
        this.notificaciones = notificaciones;
        this.auditoria = auditoria;
        this.reloj = reloj;
        this.sectores = sectores;
        this.tiempoConstante = tiempoConstante;
    }

    @Override
    public void registrar(CorreoElectronico correo, String nombre, ClaveEnClaro clave, SectorId barrio,
                          ContextoDeAccion contexto) {
        // Antes de mirar el correo: si el barrio no existe la respuesta es la misma tenga o no cuenta (RNF024).
        if (barrio != null && sectores.buscarPorId(barrio).isEmpty()) {
            throw new IllegalArgumentException("No existe el barrio '" + barrio.valor() + "'");
        }
        // RNF024: además del gasto de cifrado, la espera mínima cubre lo que el cifrado no iguala
        // (guardar, emitir el token, auditar): medido, ~49 ms con correo existente contra ~71 ms con uno nuevo.
        tiempoConstante.ejecutar(() -> registrarSinDelatarDuracion(correo, nombre, clave, barrio, contexto));
    }

    private void registrarSinDelatarDuracion(CorreoElectronico correo, String nombre, ClaveEnClaro clave,
                                             SectorId barrio, ContextoDeAccion contexto) {
        CorreoElectronico normalizado = correo.normalizado();

        var existente = usuarios.buscarPorCorreo(normalizado);
        if (existente.isPresent()) {
            // Un alta nueva cifra la clave (BCrypt, ~100 ms); sin este gasto equivalente, la
            // respuesta más rápida delataría qué correos ya tienen cuenta.
            cifrador.gastarTiempoEquivalente();
            notificaciones.avisarCambioDeAcceso(existente.get(),
                    "Alguien intentó registrarse con tu correo",
                    "Recibimos una solicitud de registro en AguaVigía con esta dirección, que ya "
                            + "tiene cuenta. No hicimos ningún cambio. Si fuiste tú y no recuerdas "
                            + "tu clave, usa la opción de restablecerla desde el ingreso del veedor.");
            return;
        }

        Usuario nuevo;
        try {
            nuevo = usuarios.guardar(Usuario.registrado(
                new UsuarioId(UUID.randomUUID().toString()),
                normalizado,
                nombre.strip(),
                cifrador.cifrar(clave.valor()),
                barrio,
                reloj.ahora()));
        } catch (com.aguavigia.ctg.domain.CorreoYaRegistradoException altaConcurrente) {
            // Otro registro con este correo ganó la carrera: la respuesta sigue siendo la uniforme (RNF024).
            return;
        }

        String token = emisorDeTokens.emitir(nuevo.id(), TipoTokenCuenta.VERIFICACION_CORREO);
        notificaciones.enviarVerificacionDeCorreo(nuevo, token);
        auditoria.registrar(AccionAuditada.CUENTA_REGISTRADA, nuevo,
                "Auto-registro; queda pendiente de verificar correo", contexto);
    }
}
