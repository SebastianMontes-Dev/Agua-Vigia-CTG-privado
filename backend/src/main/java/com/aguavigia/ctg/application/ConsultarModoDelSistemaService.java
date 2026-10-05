package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.ModoDelSistema;
import com.aguavigia.ctg.domain.ModoDelSistema.Modo;
import com.aguavigia.ctg.domain.port.in.ConsultarModoDelSistemaUseCase;
import com.aguavigia.ctg.domain.port.out.RelojPort;
import com.aguavigia.ctg.domain.port.out.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Es público y lo pide cada pantalla, así que el conteo de cuentas sintéticas se recuerda un minuto: contar decenas de miles de
 * documentos en cada petición sería un coste que cualquiera podría disparar.
 *
 * <p>Nadie espera en fila detrás de la consulta a Mongo: la lectura del conteo no toma ningún cerrojo, y de los que llegan con el
 * conteo vencido solo uno lo renueva (los demás reciben el valor anterior). Si Mongo falla se conserva el último conteo conocido,
 * porque un fallo ahí no debe esconder el modo (el banner de la simulación sigue saliendo); el intento fallido también ocupa su
 * minuto, para no insistir en cada visita contra una base caída.</p>
 */
public class ConsultarModoDelSistemaService implements ConsultarModoDelSistemaUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(ConsultarModoDelSistemaService.class);
    private static final Duration VIGENCIA_DEL_CONTEO = Duration.ofMinutes(1);

    private record Conteo(long cuentasSinteticas, Instant contadoEn) {
    }

    private final UsuarioRepository usuarios;
    private final Modo modo;
    private final RelojPort reloj;
    private final ReentrantLock renovando = new ReentrantLock();

    private volatile Conteo conteo;

    public ConsultarModoDelSistemaService(UsuarioRepository usuarios, Modo modo, RelojPort reloj) {
        this.usuarios = usuarios;
        this.modo = modo;
        this.reloj = reloj;
    }

    @Override
    public ModoDelSistema consultar() {
        Instant ahora = reloj.ahora();
        if (vencido(conteo, ahora) && renovando.tryLock()) {
            try {
                if (vencido(conteo, ahora)) {
                    conteo = contar(ahora);
                }
            } finally {
                renovando.unlock();
            }
        }
        Conteo actual = conteo;
        return new ModoDelSistema(modo, actual == null ? 0 : actual.cuentasSinteticas());
    }

    private static boolean vencido(Conteo actual, Instant ahora) {
        return actual == null || ahora.isAfter(actual.contadoEn().plus(VIGENCIA_DEL_CONTEO));
    }

    private Conteo contar(Instant ahora) {
        try {
            return new Conteo(usuarios.contarSinteticas(), ahora);
        } catch (RuntimeException e) {
            LOG.warn("No se pudo contar las cuentas sinteticas; se conserva el ultimo conteo conocido: {}", e.toString());
            Conteo anterior = conteo;
            return new Conteo(anterior == null ? 0 : anterior.cuentasSinteticas(), ahora);
        }
    }
}
