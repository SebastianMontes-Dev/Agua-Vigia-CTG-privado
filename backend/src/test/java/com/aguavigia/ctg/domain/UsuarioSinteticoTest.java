package com.aguavigia.ctg.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Las cuentas sintéticas (D20): las crea el sistema para probar el volumen, no representan a nadie. No fingen nada que nadie
 * hizo: ni consentimiento ni barrio verificado ni correo confirmado.
 */
class UsuarioSinteticoTest {

    private static final Instant MOMENTO = Instant.parse("2026-10-02T08:00:00Z");
    private static final ClaveHash CLAVE = new ClaveHash("$2a$10$hash-compartido-de-prueba");

    private static Usuario sintetico() {
        return Usuario.sinteticoComoVecino(new UsuarioId("u-1"), new CorreoElectronico("sintetica-000001@demo.aguavigia.invalid"),
                "Cuenta sintética 000001", new SectorId("manga"), MOMENTO);
    }

    @Test
    void nacenComoLasDeUnVecinoPeroMarcadasComoDemostracion() {
        Usuario usuario = sintetico();

        assertThat(usuario.esVecino()).isTrue();
        assertThat(usuario.datosDeDemostracion()).isTrue();
        assertThat(usuario.estado()).isEqualTo(EstadoCuenta.INVITADA);
        assertThat(usuario.claveHash()).isNull();
        assertThat(usuario.barrio()).isEqualTo(new SectorId("manga"));
    }

    @Test
    void noFingenConsentimientosNiBarrioVerificado() {
        Usuario activa = sintetico().aceptarInvitacion(CLAVE, MOMENTO);

        assertThat(activa.estado()).isEqualTo(EstadoCuenta.ACTIVA);
        assertThat(activa.consentimientos()).isEmpty();
        assertThat(activa.barrioVerificado()).isFalse();
        assertThat(activa.permisosEfectivos()).containsExactly(Permiso.GESTIONAR_PERFIL_PROPIO);
    }

    @Test
    void laMarcaDeDemostracionSobreviveACualquierCambio() {
        Usuario activa = sintetico().aceptarInvitacion(CLAVE, MOMENTO);

        assertThat(activa.datosDeDemostracion()).isTrue();
        assertThat(activa.mudarDeBarrio(new SectorId("bocagrande"), MOMENTO).datosDeDemostracion()).isTrue();
        assertThat(activa.suspender(MOMENTO).datosDeDemostracion()).isTrue();
    }

    @Test
    void unaCuentaRealNoEsDeDemostracion() {
        Usuario real = Usuario.registradoComoVecino(new UsuarioId("u-2"), new CorreoElectronico("ana@correo.com"), "Ana",
                new SectorId("manga"), java.util.List.of(new Consentimiento(TipoConsentimiento.PRIVACIDAD, "borrador", MOMENTO)),
                MOMENTO);

        assertThat(real.datosDeDemostracion()).isFalse();
    }

    @Test
    void unaSinteticaSigueNecesitandoBarrio() {
        assertThatThrownBy(() -> Usuario.sinteticoComoVecino(new UsuarioId("u-3"),
                new CorreoElectronico("s@demo.aguavigia.invalid"), "x", null, MOMENTO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
