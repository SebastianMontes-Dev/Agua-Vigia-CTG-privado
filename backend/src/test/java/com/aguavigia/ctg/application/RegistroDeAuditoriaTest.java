package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.AccionAuditada;
import com.aguavigia.ctg.domain.ClaveHash;
import com.aguavigia.ctg.domain.Consentimiento;
import com.aguavigia.ctg.domain.ContextoDeAccion;
import com.aguavigia.ctg.domain.CorreoElectronico;
import com.aguavigia.ctg.domain.EstadoCuenta;
import com.aguavigia.ctg.domain.EventoAuditoria;
import com.aguavigia.ctg.domain.PermisosEfectivos;
import com.aguavigia.ctg.domain.RolVeedor;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.TipoConsentimiento;
import com.aguavigia.ctg.domain.Usuario;
import com.aguavigia.ctg.domain.UsuarioId;
import com.aguavigia.ctg.domain.port.out.AuditoriaRepository;
import com.aguavigia.ctg.domain.port.out.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * La auditoría de la ciudadanía no debe guardar más de lo que hace falta: una IP completa junto a la cuenta de cada
 * vecino, para siempre, sería un dato personal acumulado sin que sirva para investigar nada que un bloque de red no
 * permita.
 */
class RegistroDeAuditoriaTest {

    private static final Instant AHORA = Instant.parse("2026-10-01T15:00:00Z");
    private static final ClaveHash HASH =
            new ClaveHash("$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy");

    private AuditoriaRepository auditoria;
    private RegistroDeAuditoria registro;

    @BeforeEach
    void montar() {
        auditoria = mock(AuditoriaRepository.class);
        UsuarioRepository usuarios = mock(UsuarioRepository.class);
        registro = new RegistroDeAuditoria(auditoria, usuarios, () -> AHORA, Duration.ofDays(180));
    }

    private static Usuario vecino() {
        return Usuario.registradoComoVecino(new UsuarioId("v-1"), new CorreoElectronico("vecina@ejemplo.org"),
                "Vecina", new SectorId("manga"),
                List.of(new Consentimiento(TipoConsentimiento.PRIVACIDAD, "v1", AHORA)), AHORA);
    }

    private static Usuario cuentaDelPanel() {
        return new Usuario(new UsuarioId("u-1"), new CorreoElectronico("ana@ejemplo.org"), "Ana", HASH,
                EstadoCuenta.ACTIVA, PermisosEfectivos.deRol(RolVeedor.VEEDOR), null, AHORA, AHORA);
    }

    private EventoAuditoria eventoRegistrado() {
        ArgumentCaptor<EventoAuditoria> evento = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(evento.capture());
        return evento.getValue();
    }

    @Test
    void unEventoSobreUnVecinoDebeGuardarSoloElBloqueDeRedYCaducar() {
        Usuario vecino = vecino();

        registro.registrarConAutor(AccionAuditada.BARRIO_VERIFICADO, vecino, vecino, "detalle",
                ContextoDeAccion.anonimo("190.20.30.40"));

        EventoAuditoria evento = eventoRegistrado();
        assertThat(evento.ip()).isEqualTo("190.20.30.0/24");
        assertThat(evento.venceEn()).isEqualTo(AHORA.plus(Duration.ofDays(180)));
    }

    @Test
    void unEventoSobreUnVecinoConIpv6DebeGuardarSoloSuPrefijo() {
        Usuario vecino = vecino();

        registro.registrarConAutor(AccionAuditada.SESION_INICIADA, vecino, vecino, "detalle",
                ContextoDeAccion.anonimo("2800:484:1234:5678:aaaa:bbbb:cccc:dddd"));

        assertThat(eventoRegistrado().ip()).isEqualTo("2800:0484:1234:5678::/64");
    }

    /** Lo que hace el panel sí se conserva completo y para siempre: es la evidencia de quién hizo qué. */
    @Test
    void unEventoSobreUnaCuentaDelPanelDebeConservarLaIpCompletaYNoCaducar() {
        Usuario cuenta = cuentaDelPanel();

        registro.registrarConAutor(AccionAuditada.CUENTA_SUSPENDIDA, cuenta, cuenta, "detalle",
                ContextoDeAccion.anonimo("190.20.30.40"));

        EventoAuditoria evento = eventoRegistrado();
        assertThat(evento.ip()).isEqualTo("190.20.30.40");
        assertThat(evento.venceEn()).isNull();
    }

    @Test
    void unEventoSinSujetoNoDebeCaducarNiTocarLaIp() {
        registro.registrarConAutor(AccionAuditada.SESION_RECHAZADA, null, null, "detalle",
                ContextoDeAccion.anonimo("190.20.30.40"));

        EventoAuditoria evento = eventoRegistrado();
        assertThat(evento.ip()).isEqualTo("190.20.30.40");
        assertThat(evento.venceEn()).isNull();
    }
}
