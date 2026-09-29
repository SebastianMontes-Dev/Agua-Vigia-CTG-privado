package com.aguavigia.ctg.infrastructure.mail;

import com.aguavigia.ctg.domain.CorreoElectronico;
import com.aguavigia.ctg.domain.RolVeedor;
import com.aguavigia.ctg.domain.Usuario;
import com.aguavigia.ctg.domain.UsuarioId;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Instant;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * El enlace de cada correo de cuenta apunta a las pantallas de la SPA (`/cuenta/verificar`, `/cuenta/invitacion` y
 * `/cuenta/restablecer`). Estas pruebas fijan esa ruta: un enlace que no coincida con las rutas de `frontend/` le llegaría
 * roto a quien espera confirmar su correo.
 */
class MailCuentaAdapterTest {

    private static final String URL_FRONTEND = "https://aguavigia.example";
    private static final Instant AHORA = Instant.parse("2026-09-21T15:00:00Z");

    private JavaMailSender mailSender;
    private MailCuentaAdapter adaptador;

    @BeforeEach
    void montar() {
        mailSender = mock(JavaMailSender.class);
        given(mailSender.createMimeMessage())
                .willAnswer(invocacion -> new MimeMessage(Session.getInstance(new Properties())));
        adaptador = new MailCuentaAdapter(mailSender, "AguaVigía CTG <no-responder@aguavigia.local>", URL_FRONTEND);
    }

    private static Usuario usuario(String nombre) {
        return Usuario.invitado(new UsuarioId("u-1"), new CorreoElectronico("persona@correo.com"),
                nombre, RolVeedor.VEEDOR, AHORA);
    }

    /** getContent() y no writeTo(): ver el comentario equivalente en MailNotificacionAdapterTest. */
    private String cuerpoEnviado() throws Exception {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue().getContent().toString();
    }

    @Test
    void elCorreoDeVerificacionDebeApuntarALaPantallaDelBackend() throws Exception {
        adaptador.enviarVerificacionDeCorreo(usuario("Ana"), "tok-verif");

        assertThat(cuerpoEnviado()).contains(URL_FRONTEND + "/cuenta/verificar?token=tok-verif");
    }

    @Test
    void elCorreoDeInvitacionDebeApuntarALaPantallaDelBackend() throws Exception {
        adaptador.enviarInvitacion(usuario("Beto"), usuario("Ana"), "tok-invit");

        assertThat(cuerpoEnviado()).contains(URL_FRONTEND + "/cuenta/invitacion?token=tok-invit");
    }

    @Test
    void elCorreoDeRestablecimientoDebeApuntarALaPantallaDelBackend() throws Exception {
        adaptador.enviarEnlaceDeRestablecimiento(usuario("Ana"), "tok-reset");

        assertThat(cuerpoEnviado()).contains(URL_FRONTEND + "/cuenta/restablecer?token=tok-reset");
    }

    @Test
    void ningunEnlaceDebeQuedarConLasRutasDelFrontendRetirado() throws Exception {
        adaptador.enviarEnlaceDeRestablecimiento(usuario("Ana"), "t");

        assertThat(cuerpoEnviado()).doesNotContain("/cuentas/restablecer").doesNotContain("{{");
    }

    @Test
    void elTokenDebeCodificarseParaQueNoRompaLaUrl() throws Exception {
        adaptador.enviarVerificacionDeCorreo(usuario("Ana"), "a+b/c=d e");

        assertThat(cuerpoEnviado()).contains("?token=a%2Bb%2Fc%3Dd+e");
    }

    @Test
    void unaUrlDelFrontendConBarraFinalNoDebeDuplicarLaBarra() throws Exception {
        adaptador = new MailCuentaAdapter(mailSender, "AguaVigía CTG <no-responder@aguavigia.local>",
                URL_FRONTEND + "/");

        adaptador.enviarVerificacionDeCorreo(usuario("Ana"), "t");

        assertThat(cuerpoEnviado()).contains(URL_FRONTEND + "/cuenta/verificar")
                .doesNotContain(".example//cuenta");
    }

    @Test
    void elNombreEscritoPorLaPersonaNoDebeInyectarHtmlEnElCorreo() throws Exception {
        adaptador.enviarVerificacionDeCorreo(usuario("<script>alert(1)</script>"), "t");

        assertThat(cuerpoEnviado()).doesNotContain("<script>alert(1)").contains("&lt;script&gt;");
    }

    @Test
    void elAvisoDeCambioDeAccesoDebeLlevarElMensaje() throws Exception {
        adaptador.avisarCambioDeAcceso(usuario("Ana"), "Tu acceso fue aprobado", "Ya puedes entrar al panel.");

        assertThat(cuerpoEnviado()).contains("Tu acceso fue aprobado").contains("Ya puedes entrar al panel.");
    }

    @Test
    void siFallaElCorreoDeVerificacionNoDebePropagarElErrorPeroSiLaInvitacion() {
        willThrow(new MailSendException("SMTP caído")).given(mailSender).send(any(MimeMessage.class));

        // La cuenta ya quedó creada: tumbar la petición porque el SMTP tardó no la desharía.
        assertThatCode(() -> adaptador.enviarVerificacionDeCorreo(usuario("Ana"), "t")).doesNotThrowAnyException();

        // La invitación es la excepción: su enlace no se puede volver a pedir desde fuera.
        assertThatThrownBy(() -> adaptador.enviarInvitacion(usuario("Beto"), usuario("Ana"), "t"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("persona@correo.com");
    }
}
