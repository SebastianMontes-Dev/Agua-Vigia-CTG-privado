package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.AccionAuditada;
import com.aguavigia.ctg.domain.ClaveEnClaro;
import com.aguavigia.ctg.domain.Consentimiento;
import com.aguavigia.ctg.domain.ConsentimientosAceptados;
import com.aguavigia.ctg.domain.ContextoDeAccion;
import com.aguavigia.ctg.domain.CorreoElectronico;
import com.aguavigia.ctg.domain.CorreoYaRegistradoException;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.TipoConsentimiento;
import com.aguavigia.ctg.domain.TipoTokenCuenta;
import com.aguavigia.ctg.domain.Usuario;
import com.aguavigia.ctg.domain.UsuarioId;
import com.aguavigia.ctg.domain.port.in.RegistrarVecinoUseCase;
import com.aguavigia.ctg.domain.port.out.CifradorClavePort;
import com.aguavigia.ctg.domain.port.out.NotificacionCuentaPort;
import com.aguavigia.ctg.domain.port.out.RelojPort;
import com.aguavigia.ctg.domain.port.out.SectorRepository;
import com.aguavigia.ctg.domain.port.out.TiempoConstantePort;
import com.aguavigia.ctg.domain.port.out.UsuarioRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Mismo molde que RegistrarUsuarioService: la respuesta es uniforme exista o no el correo (RNF024),
 * y a quien ya tiene cuenta se le avisa por correo en vez de decírselo a quien rellenó el formulario.
 *
 * Lo que cambia es el destino: el vecino no pasa por la aprobación de un ADMIN (ver
 * Usuario.verificarCorreo), así que no hay nada que revisar a mano. Lo que sí se exige aquí, y antes
 * de mirar el correo, es el barrio y el aviso de privacidad aceptado.
 */
public class RegistrarVecinoService implements RegistrarVecinoUseCase {

    private final UsuarioRepository usuarios;
    private final CifradorClavePort cifrador;
    private final EmisorDeTokensDeCuenta emisorDeTokens;
    private final NotificacionCuentaPort notificaciones;
    private final RegistroDeAuditoria auditoria;
    private final RelojPort reloj;
    private final SectorRepository sectores;
    private final TiempoConstantePort tiempoConstante;
    private final String versionPrivacidad;

    public RegistrarVecinoService(UsuarioRepository usuarios,
                                  CifradorClavePort cifrador,
                                  EmisorDeTokensDeCuenta emisorDeTokens,
                                  NotificacionCuentaPort notificaciones,
                                  RegistroDeAuditoria auditoria,
                                  RelojPort reloj,
                                  SectorRepository sectores,
                                  TiempoConstantePort tiempoConstante,
                                  String versionPrivacidad) {
        this.usuarios = usuarios;
        this.cifrador = cifrador;
        this.emisorDeTokens = emisorDeTokens;
        this.notificaciones = notificaciones;
        this.auditoria = auditoria;
        this.reloj = reloj;
        this.sectores = sectores;
        this.tiempoConstante = tiempoConstante;
        this.versionPrivacidad = versionPrivacidad;
    }

    @Override
    public void registrar(CorreoElectronico correo, String nombre, ClaveEnClaro clave, SectorId barrio,
                          ConsentimientosAceptados consentimientos, ContextoDeAccion contexto) {
        // Todo lo que se rechaza por contenido sale antes de mirar el correo: si no, la respuesta
        // distinguiría un correo con cuenta de uno sin ella (RNF024).
        if (consentimientos == null || !consentimientos.privacidad()) {
            throw new IllegalArgumentException("Para registrarte debes aceptar el aviso de privacidad");
        }
        if (barrio == null) {
            throw new IllegalArgumentException("Debes indicar el barrio donde vives");
        }
        if (sectores.buscarPorId(barrio).isEmpty()) {
            throw new IllegalArgumentException("No existe el barrio '" + barrio.valor() + "'");
        }
        tiempoConstante.ejecutar(() -> registrarSinDelatarDuracion(correo, nombre, clave, barrio,
                consentimientos, contexto));
    }

    private void registrarSinDelatarDuracion(CorreoElectronico correo, String nombre, ClaveEnClaro clave,
                                             SectorId barrio, ConsentimientosAceptados consentimientos,
                                             ContextoDeAccion contexto) {
        CorreoElectronico normalizado = correo.normalizado();

        var existente = usuarios.buscarPorCorreo(normalizado);
        if (existente.isPresent()) {
            // Un alta nueva cifra la clave (BCrypt); sin este gasto equivalente, la respuesta más
            // rápida delataría qué correos ya tienen cuenta.
            cifrador.gastarTiempoEquivalente();
            notificaciones.avisarCambioDeAcceso(existente.get(),
                    "Alguien intentó registrarse con tu correo",
                    "Recibimos una solicitud de registro en AguaVigía con esta dirección, que ya "
                            + "tiene cuenta. No hicimos ningún cambio. Si fuiste tú y no recuerdas "
                            + "tu clave, puedes restablecerla desde el ingreso.");
            return;
        }

        Instant ahora = reloj.ahora();
        Usuario nuevo;
        try {
            nuevo = usuarios.guardar(Usuario.registradoComoVecino(
                    new UsuarioId(UUID.randomUUID().toString()),
                    normalizado,
                    nombre.strip(),
                    cifrador.cifrar(clave.valor()),
                    barrio,
                    consentimientosDe(consentimientos, ahora),
                    ahora));
        } catch (CorreoYaRegistradoException altaConcurrente) {
            // Otro registro con este correo ganó la carrera: la respuesta sigue siendo la uniforme (RNF024).
            return;
        }

        String token = emisorDeTokens.emitir(nuevo.id(), TipoTokenCuenta.VERIFICACION_CORREO);
        notificaciones.enviarVerificacionDeCorreo(nuevo, token);
        auditoria.registrar(AccionAuditada.CUENTA_REGISTRADA, nuevo,
                "Registro de vecino; queda pendiente de verificar el correo", contexto);
    }

    private List<Consentimiento> consentimientosDe(ConsentimientosAceptados aceptados, Instant ahora) {
        List<Consentimiento> consentimientos = new ArrayList<>();
        consentimientos.add(new Consentimiento(TipoConsentimiento.PRIVACIDAD, versionPrivacidad, ahora));
        if (aceptados.avisos()) {
            consentimientos.add(new Consentimiento(TipoConsentimiento.AVISOS, versionPrivacidad, ahora));
        }
        return consentimientos;
    }
}
