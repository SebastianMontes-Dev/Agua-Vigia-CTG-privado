package com.aguavigia.ctg.infrastructure.config;

import com.aguavigia.ctg.application.RegistroDeAuditoria;
import com.aguavigia.ctg.domain.port.in.ControlarRelojDeSimulacionUseCase;
import com.aguavigia.ctg.domain.port.in.IniciarSesionDeAdminDeSimulacionUseCase;
import com.aguavigia.ctg.domain.port.in.InyectarBoletinSimuladoUseCase;
import com.aguavigia.ctg.domain.port.in.ReiniciarSimulacionUseCase;
import com.aguavigia.ctg.domain.port.out.BuzonDeBoletinesSimuladosPort;
import com.aguavigia.ctg.domain.port.out.CicloDeIngestaPort;
import com.aguavigia.ctg.domain.port.out.EmisorDeSesionPort;
import com.aguavigia.ctg.domain.port.out.RelojControlablePort;
import com.aguavigia.ctg.domain.port.out.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SimulacionConfigTest {

    @Configuration
    static class Colaboradores {
        @Bean RelojControlablePort relojControlable() { return mock(RelojControlablePort.class); }
        @Bean UsuarioRepository usuarios() { return mock(UsuarioRepository.class); }
        @Bean EmisorDeSesionPort emisor() { return mock(EmisorDeSesionPort.class); }
        @Bean RegistroDeAuditoria auditoria() { return mock(RegistroDeAuditoria.class); }
        @Bean CicloDeIngestaPort ciclo() { return mock(CicloDeIngestaPort.class); }
    }

    @Configuration
    static class ConBuzon {
        @Bean BuzonDeBoletinesSimuladosPort buzon() { return mock(BuzonDeBoletinesSimuladosPort.class); }
    }

    @Configuration
    @Import({Colaboradores.class, SimulacionConfig.class})
    static class SinBuzon {
    }

    @Configuration
    @Import({Colaboradores.class, ConBuzon.class, SimulacionConfig.class})
    static class Completa {
    }

    @Test
    void sinSimulacionNoSeCreaNingunCasoDeUso() {
        new ApplicationContextRunner().withUserConfiguration(Completa.class).run(c -> {
            assertThat(c).doesNotHaveBean(ControlarRelojDeSimulacionUseCase.class);
            assertThat(c).doesNotHaveBean(InyectarBoletinSimuladoUseCase.class);
            assertThat(c).doesNotHaveBean(IniciarSesionDeAdminDeSimulacionUseCase.class);
        });
    }

    @Test
    void conSimulacionSeCreanLosCuatroCasosDeUso() {
        new ApplicationContextRunner().withUserConfiguration(Completa.class)
                .withPropertyValues("aguavigia.sim.habilitada=true", "aguavigia.sistema.modo=SIMULACION")
                .run(c -> {
                    assertThat(c).hasSingleBean(ControlarRelojDeSimulacionUseCase.class);
                    assertThat(c).hasSingleBean(InyectarBoletinSimuladoUseCase.class);
                    assertThat(c).hasSingleBean(IniciarSesionDeAdminDeSimulacionUseCase.class);
                    assertThat(c).hasSingleBean(ReiniciarSimulacionUseCase.class);
                });
    }

    /** Habilitar la simulación sin poner la ingesta en modo simulación dejaría /api/sim/boletines sin a dónde ir: se dice al arrancar. */
    @Test
    void conSimulacionPeroLaIngestaNoEnModoSimulacionElArranqueFallaConUnMensajeClaro() {
        new ApplicationContextRunner().withUserConfiguration(SinBuzon.class)
                .withPropertyValues("aguavigia.sim.habilitada=true", "aguavigia.sistema.modo=SIMULACION")
                .run(c -> {
                    assertThat(c).hasFailed();
                    assertThat(c.getStartupFailure()).hasRootCauseInstanceOf(IllegalStateException.class);
                    assertThat(rootMessage(c.getStartupFailure())).contains("INGESTA_MODO=simulacion");
                });
    }

    /** La ruta de ADMIN sin segundo factor no puede existir en una instancia que se declara real: la única barrera no puede ser una sola propiedad. */
    @Test
    void conSimulacionEnUnaInstanciaQueNoSeDeclaraSimulacionElArranqueFalla() {
        for (String modo : new String[] {"REAL", ""}) {
            new ApplicationContextRunner().withUserConfiguration(Completa.class)
                    .withPropertyValues("aguavigia.sim.habilitada=true", "aguavigia.sistema.modo=" + modo)
                    .run(c -> {
                        assertThat(c).hasFailed();
                        assertThat(rootMessage(c.getStartupFailure())).contains("AGUAVIGIA_MODO=SIMULACION");
                    });
        }
        new ApplicationContextRunner().withUserConfiguration(Completa.class)
                .withPropertyValues("aguavigia.sim.habilitada=true")
                .run(c -> assertThat(c).hasFailed());
    }

    private static String rootMessage(Throwable fallo) {
        Throwable causa = fallo;
        while (causa.getCause() != null) {
            causa = causa.getCause();
        }
        return causa.getMessage();
    }
}
