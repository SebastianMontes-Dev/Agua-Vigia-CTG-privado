package com.aguavigia.ctg.infrastructure.config;

import com.aguavigia.ctg.application.ControlarRelojDeSimulacionService;
import com.aguavigia.ctg.application.IniciarSesionDeAdminDeSimulacionService;
import com.aguavigia.ctg.application.InyectarBoletinSimuladoService;
import com.aguavigia.ctg.application.ReiniciarSimulacionService;
import com.aguavigia.ctg.application.RegistroDeAuditoria;
import com.aguavigia.ctg.domain.port.in.ReiniciarSimulacionUseCase;
import com.aguavigia.ctg.domain.port.in.ControlarRelojDeSimulacionUseCase;
import com.aguavigia.ctg.domain.port.in.IniciarSesionDeAdminDeSimulacionUseCase;
import com.aguavigia.ctg.domain.port.in.InyectarBoletinSimuladoUseCase;
import com.aguavigia.ctg.domain.port.out.BuzonDeBoletinesSimuladosPort;
import com.aguavigia.ctg.domain.port.out.CicloDeIngestaPort;
import com.aguavigia.ctg.domain.port.out.EmisorDeSesionPort;
import com.aguavigia.ctg.domain.port.out.RelojControlablePort;
import com.aguavigia.ctg.domain.port.out.RelojPort;
import com.aguavigia.ctg.domain.port.out.UsuarioRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Los casos de uso de la simulación (D26). Solo existen con {@code aguavigia.sim.habilitada=true}: en la instancia real no hay reloj que
 * mover, ni buzón de boletines, ni sesión de ADMIN sin clave.
 */
@Configuration
@ConditionalOnProperty(prefix = "aguavigia.sim", name = "habilitada", havingValue = "true")
public class SimulacionConfig {

    /**
     * La sesión de ADMIN sin segundo factor y el reloj movible no pueden existir en una instancia que se declara real: la única barrera no puede ser
     * una sola propiedad. Con la simulación habilitada, la instancia tiene que declararse también de simulación (AGUAVIGIA_MODO=SIMULACION).
     */
    public SimulacionConfig(@Value("${aguavigia.sistema.modo:REAL}") String modo) {
        if (!"SIMULACION".equalsIgnoreCase(modo)) {
            throw new IllegalStateException("La simulación está habilitada (aguavigia.sim.habilitada=true) pero la instancia no se declara de simulación: "
                    + "ponle AGUAVIGIA_MODO=SIMULACION. Una instancia real nunca debe tener las rutas /api/sim/**.");
        }
    }

    @Bean
    public ControlarRelojDeSimulacionUseCase controlarRelojDeSimulacion(RelojControlablePort reloj) {
        return new ControlarRelojDeSimulacionService(reloj);
    }

    @Bean
    public InyectarBoletinSimuladoUseCase inyectarBoletinSimulado(ObjectProvider<BuzonDeBoletinesSimuladosPort> buzon,
                                                                  CicloDeIngestaPort ciclo, RelojPort reloj) {
        return new InyectarBoletinSimuladoService(buzonDeLaIngesta(buzon), ciclo, reloj);
    }

    @Bean
    public ReiniciarSimulacionUseCase reiniciarSimulacion(ObjectProvider<BuzonDeBoletinesSimuladosPort> buzon,
                                                          RelojControlablePort reloj) {
        return new ReiniciarSimulacionService(buzonDeLaIngesta(buzon), reloj);
    }

    private static BuzonDeBoletinesSimuladosPort buzonDeLaIngesta(ObjectProvider<BuzonDeBoletinesSimuladosPort> buzon) {
        BuzonDeBoletinesSimuladosPort destino = buzon.getIfAvailable();
        if (destino == null) {
            throw new IllegalStateException("La simulación está habilitada (aguavigia.sim.habilitada=true) pero la ingesta no está en "
                    + "modo simulación: ponla con INGESTA_MODO=simulacion para que los boletines simulados tengan a dónde llegar");
        }
        return destino;
    }

    @Bean
    public IniciarSesionDeAdminDeSimulacionUseCase iniciarSesionDeAdminDeSimulacion(UsuarioRepository usuarios,
                                                                                    EmisorDeSesionPort emisor,
                                                                                    RegistroDeAuditoria auditoria) {
        return new IniciarSesionDeAdminDeSimulacionService(usuarios, emisor, auditoria);
    }
}
