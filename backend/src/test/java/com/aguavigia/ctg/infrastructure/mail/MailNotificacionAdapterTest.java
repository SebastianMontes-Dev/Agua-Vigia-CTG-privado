package com.aguavigia.ctg.infrastructure.mail;

import com.aguavigia.ctg.domain.CorreoElectronico;
import com.aguavigia.ctg.domain.EstadoServicio;
import com.aguavigia.ctg.domain.EstadoSuscripcion;
import com.aguavigia.ctg.domain.Sector;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.Suscripcion;
import com.aguavigia.ctg.domain.SuscripcionId;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Instant;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * El paquete de correo era el de menor cobertura del proyecto (21.6%) y el único adaptador con
 * envío real sin ninguna prueba — justo donde vive la promesa de RF015 (baja en 1 clic en cada
 * correo) y donde el vecino recibía el nombre crudo de un enum.
 */
class MailNotificacionAdapterTest {

    private static final Instant ACTUALIZADO_EN = Instant.parse("2026-08-09T20:00:00Z");
    private static final String URL_FRONTEND = "https://app.aguavigia.example";
    private static final String TOKEN = "token-abc-123";

    private JavaMailSender mailSender;
    private MailNotificacionAdapter adaptador;

    @BeforeEach
    void montar() {
        mailSender = mock(JavaMailSender.class);
        given(mailSender.createMimeMessage())
                .willAnswer(invocacion -> new MimeMessage(Session.getInstance(new Properties())));
        firma = mock(com.aguavigia.ctg.domain.port.out.FirmaDeEnlacesPort.class);
        given(firma.emitir(any())).willReturn("enlace-firmado");
        adaptador = new MailNotificacionAdapter(mailSender,
                "AguaVigía CTG <no-responder@aguavigia.local>", URL_FRONTEND, 48, firma, () -> ACTUALIZADO_EN, 24);
    }

    private com.aguavigia.ctg.domain.port.out.FirmaDeEnlacesPort firma;

    // --- «¿Ya volvió el agua?» (D18) ---

    @Test
    void elAvisoDeUnBarrioSinServicioDebeLlevarElEnlaceDeUnToque() throws Exception {
        adaptador.avisarCambioDeEstado(suscripcion(), sector(EstadoServicio.SIN_SERVICIO));

        String cuerpo = cuerpoEnviado();
        assertThat(cuerpo).contains("Sí, ya volvió el agua");
        assertThat(cuerpo).contains(URL_FRONTEND + "/sectores/manga/restablecimiento?token=enlace-firmado");
        assertThat(cuerpo).doesNotContain("{{");
    }

    @Test
    void elAvisoDePresionBajaTambienPregunta() throws Exception {
        adaptador.avisarCambioDeEstado(suscripcion(), sector(EstadoServicio.PRESION_BAJA));

        assertThat(cuerpoEnviado()).contains("/sectores/manga/restablecimiento?token=enlace-firmado");
    }

    /** Preguntar «¿ya volvió?» a quien acaba de recibir la noticia de que volvió, o de que habrá un corte, no tiene sentido. */
    @Test
    void losDemasAvisosNoPreguntan() throws Exception {
        for (EstadoServicio estado : new EstadoServicio[]{EstadoServicio.CON_SERVICIO, EstadoServicio.CORTE_PROGRAMADO}) {
            org.mockito.Mockito.clearInvocations(mailSender);
            adaptador.avisarCambioDeEstado(suscripcion(), sector(estado));

            assertThat(cuerpoEnviado()).as(estado.name()).doesNotContain("restablecimiento?token=")
                    .doesNotContain("{{");
        }
    }

    @Test
    void elEnlaceEsDeEsaSuscripcionYEseBarrioYVenceEnElPlazoConfigurado() {
        adaptador.avisarCambioDeEstado(suscripcion(), sector(EstadoServicio.SIN_SERVICIO));

        org.mockito.Mockito.verify(firma).emitir(new com.aguavigia.ctg.domain.EnlaceDeRestablecimiento(
                new SectorId("manga"), new SuscripcionId("s-1"), ACTUALIZADO_EN.plusSeconds(24 * 3600)));
    }

    private Suscripcion suscripcion() {
        return new Suscripcion(
                new SuscripcionId("s-1"),
                new CorreoElectronico("vecino@correo.com"),
                List.of(new SectorId("manga")),
                EstadoSuscripcion.CONFIRMADA,
                TOKEN,
                ACTUALIZADO_EN);
    }

    private Sector sector(EstadoServicio estado) {
        return new Sector(new SectorId("manga"), "Manga", 5000, estado, ACTUALIZADO_EN);
    }

    /**
     * getContent() y no writeTo(): al serializar, JavaMail codifica el cuerpo en quoted-printable
     * y parte las líneas a 76 caracteres, con lo que un `?token=abc` queda como `?token=3Dabc`
     * cortado por la mitad y ninguna aserción sobre una URL se sostiene.
     */
    private String cuerpoEnviado() throws Exception {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue().getContent().toString();
    }

    private String asuntoEnviado() throws Exception {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue().getSubject();
    }

    // --- Aviso de cambio de estado ---

    @Test
    void debeHablarEnEspanolYNoConElNombreDelEnum() throws Exception {
        adaptador.avisarCambioDeEstado(suscripcion(), sector(EstadoServicio.SIN_SERVICIO));

        assertThat(asuntoEnviado()).isEqualTo("Se fue el agua en Manga");
        assertThat(cuerpoEnviado()).doesNotContain("SIN_SERVICIO");
    }

    @Test
    void debeUsarUnTitularDistintoPorCadaEstado() throws Exception {
        adaptador.avisarCambioDeEstado(suscripcion(), sector(EstadoServicio.CON_SERVICIO));
        assertThat(asuntoEnviado()).isEqualTo("Volvió el agua en Manga");
    }

    @Test
    void debeAvisarDeUnCorteProgramadoComoTal() throws Exception {
        adaptador.avisarCambioDeEstado(suscripcion(), sector(EstadoServicio.CORTE_PROGRAMADO));
        assertThat(asuntoEnviado()).isEqualTo("Cortan el agua en Manga");
    }

    /** RF015 — la baja en 1 clic va en cada correo, no solo en el de confirmación. */
    @Test
    void debeIncluirElEnlaceDeBajaEnElAviso() throws Exception {
        adaptador.avisarCambioDeEstado(suscripcion(), sector(EstadoServicio.SIN_SERVICIO));

        assertThat(cuerpoEnviado())
                .contains(URL_FRONTEND + "/avisos/baja?token=" + TOKEN);
    }

    @Test
    void elEnlaceParaVerElSectorDebeApuntarAlFrontend() throws Exception {
        adaptador.avisarCambioDeEstado(suscripcion(), sector(EstadoServicio.SIN_SERVICIO));

        assertThat(cuerpoEnviado()).contains(URL_FRONTEND + "/sectores/manga")
                .doesNotContain(URL_FRONTEND + "/api/sectores/manga");
    }

    @Test
    void debeMostrarLaFechaDelCambioEnHoraDeCartagenaYNoEnUtc() throws Exception {
        adaptador.avisarCambioDeEstado(suscripcion(), sector(EstadoServicio.SIN_SERVICIO));

        // 20:00 UTC son las 3:00 p. m. en Cartagena.
        assertThat(cuerpoEnviado()).contains("3:00");
    }

    @Test
    void noDebeQuedarNingunMarcadorSinReemplazar() throws Exception {
        adaptador.avisarCambioDeEstado(suscripcion(), sector(EstadoServicio.PRESION_BAJA));

        assertThat(cuerpoEnviado()).doesNotContain("{{");
    }

    /** "Desconocido" era exactamente lo que el correo anterior le mandaba al vecino. */
    @Test
    void noDebeEnviarNadaSiElSectorNoTieneEstadoRegistrado() {
        adaptador.avisarCambioDeEstado(suscripcion(),
                new Sector(new SectorId("manga"), "Manga", 5000, null));

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    // --- Correo de confirmación (doble opt-in, RF013) ---

    @Test
    void elCorreoDeConfirmacionDebeLlevarElEnlaceConSuToken() throws Exception {
        adaptador.enviarConfirmacionSuscripcion(suscripcion(), List.of(sector(EstadoServicio.SIN_SERVICIO)));

        assertThat(cuerpoEnviado())
                .contains(URL_FRONTEND + "/avisos/confirmar?token=" + TOKEN)
                .contains("48")
                .doesNotContain("{{");
    }

    /** RF015 — la baja en un clic va en TODO correo, también en el de confirmación (Ley 1581/2012). */
    @Test
    void elCorreoDeConfirmacionDebeIncluirElEnlaceDeBaja() throws Exception {
        adaptador.enviarConfirmacionSuscripcion(suscripcion(), List.of(sector(EstadoServicio.SIN_SERVICIO)));

        assertThat(cuerpoEnviado()).contains(URL_FRONTEND + "/avisos/baja?token=" + TOKEN);
    }

    @Test
    void elCorreoDeConfirmacionDebeNombrarTodosLosSectoresSuscritos() throws Exception {
        adaptador.enviarConfirmacionSuscripcion(suscripcion(), List.of(
                sector(EstadoServicio.SIN_SERVICIO),
                new Sector(new SectorId("crespo"), "Crespo", 3000, null)));

        assertThat(asuntoEnviado()).contains("Manga", "Crespo");
    }

    // --- Fallos de envío ---

    /**
     * La suscripción ya quedó guardada: un SMTP caído no puede propagar hacia arriba y tumbar el
     * flujo, ni dejar al ciudadano sin su 201.
     */
    @Test
    void unFalloDelServidorDeCorreoNoDebePropagarse() {
        willThrow(new org.springframework.mail.MailSendException("SMTP caído"))
                .given(mailSender).send(any(MimeMessage.class));

        assertThatCode(() -> adaptador.avisarCambioDeEstado(suscripcion(), sector(EstadoServicio.SIN_SERVICIO)))
                .doesNotThrowAnyException();
        assertThatCode(() -> adaptador.enviarConfirmacionSuscripcion(suscripcion(), List.of(sector(EstadoServicio.SIN_SERVICIO))))
                .doesNotThrowAnyException();
    }
}
