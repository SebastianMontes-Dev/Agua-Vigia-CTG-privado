package com.aguavigia.ctg.infrastructure.mail;

import com.aguavigia.ctg.domain.Usuario;
import com.aguavigia.ctg.domain.port.out.NotificacionCuentaPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Solo para la demo de carga con registro masivo (ADR-088): con `aguavigia.correo.cuentas-habilitado=false`
 * los correos de cuentas no salen, para que decenas de miles de altas no llenen MailHog. La cuenta se crea
 * igual; lo único que se pierde es el mensaje, así que una invitación hecha con esto activo no se puede
 * aceptar (el enlace solo existía en el correo). Los avisos de corte a la ciudadanía no pasan por aquí.
 */
@Component
@ConditionalOnProperty(prefix = "aguavigia.correo", name = "cuentas-habilitado", havingValue = "false")
public class CorreoDeCuentaDescartadoAdapter implements NotificacionCuentaPort {

    private static final Logger log = LoggerFactory.getLogger(CorreoDeCuentaDescartadoAdapter.class);

    private final AtomicLong descartados = new AtomicLong();

    public CorreoDeCuentaDescartadoAdapter() {
        log.warn("Correos de cuentas DESACTIVADOS (aguavigia.correo.cuentas-habilitado=false): registros, "
                + "invitaciones y restablecimientos no envían correo. Solo para la demo de carga.");
    }

    @Override
    public void enviarVerificacionDeCorreo(Usuario usuario, String tokenEnClaro) {
        descartar();
    }

    @Override
    public void enviarInvitacion(Usuario invitado, Usuario autorDeLaInvitacion, String tokenEnClaro) {
        descartar();
    }

    @Override
    public void enviarActivacionDeVecino(Usuario vecino, String tokenEnClaro) {
        descartar();
    }

    @Override
    public void enviarEnlaceDeRestablecimiento(Usuario usuario, String tokenEnClaro) {
        descartar();
    }

    @Override
    public void avisarCambioDeAcceso(Usuario usuario, String asunto, String mensaje) {
        descartar();
    }

    long descartados() {
        return descartados.get();
    }

    private void descartar() {
        long total = descartados.incrementAndGet();
        if (total % 1000 == 0) {
            log.info("Correos de cuentas descartados hasta ahora: {}", total);
        }
    }
}
