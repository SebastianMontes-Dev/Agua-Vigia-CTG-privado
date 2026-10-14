package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.AccionAuditada;
import com.aguavigia.ctg.domain.ClaveHash;
import com.aguavigia.ctg.domain.ContextoDeAccion;
import com.aguavigia.ctg.domain.CorreoElectronico;
import com.aguavigia.ctg.domain.Sector;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.Usuario;
import com.aguavigia.ctg.domain.UsuarioId;
import com.aguavigia.ctg.domain.port.in.ImportarVecinosSinteticosUseCase;
import com.aguavigia.ctg.domain.port.out.RelojPort;
import com.aguavigia.ctg.domain.port.out.SectorRepository;
import com.aguavigia.ctg.domain.port.out.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.UUID;

/**
 * Las 30 000 cuentas sintéticas (D36). Se dicen sintéticas: «30 000 cuentas sintéticas generadas con las reglas de alta de un
 * vecino», nunca «30 000 personas se registraron».
 *
 * Cada una pasa por el mismo camino de dominio que un vecino real —nace INVITADA y se activa con `aceptarInvitacion`— pero
 * sin lo que nadie hizo: ni correo enviado, ni consentimiento, ni barrio verificado. El reparto por barrio es proporcional a
 * la población y determinista (la cuenta número N siempre cae en el mismo barrio), así que repetir el arranque, o completar una
 * pasada que quedó a medias, produce las mismas cuentas y no duplica ninguna: siempre se recorre desde la primera y la base ignora las que ya
 * existen, así que una pasada cortada a mitad de un lote, que deja huecos en la numeración, se repara sola.
 */
public class ImportarVecinosSinteticosService implements ImportarVecinosSinteticosUseCase {

    private static final Logger log = LoggerFactory.getLogger(ImportarVecinosSinteticosService.class);

    /** Dominio reservado por RFC 2606: ninguna dirección de aquí existe ni se puede entregar. */
    private static final String DOMINIO = "demo.aguavigia.invalid";
    private static final long SEMILLA = 20260930L;

    private final UsuarioRepository usuarios;
    private final SectorRepository sectores;
    private final RegistroDeAuditoria auditoria;
    private final RelojPort reloj;
    private final int tamanoDeLote;

    public ImportarVecinosSinteticosService(UsuarioRepository usuarios, SectorRepository sectores,
                                            RegistroDeAuditoria auditoria, RelojPort reloj, int tamanoDeLote) {
        if (tamanoDeLote < 1) {
            throw new IllegalArgumentException("El tamaño del lote debe ser al menos 1");
        }
        this.usuarios = usuarios;
        this.sectores = sectores;
        this.auditoria = auditoria;
        this.reloj = reloj;
        this.tamanoDeLote = tamanoDeLote;
    }

    @Override
    public int importar(int objetivo, ClaveHash claveCompartida) {
        if (objetivo <= 0) {
            return 0;
        }
        long existentes = usuarios.contarSinteticas();
        if (existentes >= objetivo) {
            return 0;
        }
        List<Sector> catalogo = sectores.listarTodos();
        if (catalogo.isEmpty()) {
            log.warn("No hay sectores: no se pueden asignar barrios a las cuentas sintéticas. Se reintentará en el próximo arranque.");
            return 0;
        }
        long[] acumulado = pesosAcumulados(catalogo);

        Instant ahora = reloj.ahora();
        int creadas = 0;
        List<Usuario> lote = new ArrayList<>(tamanoDeLote);
        for (long numero = 1; numero <= objetivo; numero++) {
            lote.add(cuentaNumero(numero, catalogo, acumulado, claveCompartida, ahora));
            if (lote.size() == tamanoDeLote || numero == objetivo) {
                creadas += guardarLote(lote, numero);
                lote.clear();
            }
        }
        return creadas;
    }

    private int guardarLote(List<Usuario> lote, long ultimoNumero) {
        int insertadas = usuarios.insertarSinteticasSiNoExisten(List.copyOf(lote));
        if (insertadas == 0) {
            // Todas existían: no hubo activación que anotar.
            return 0;
        }
        long primero = ultimoNumero - lote.size() + 1;
        // Un evento por lote y no por cuenta: cada evento sería una lectura más sobre la base para anotar lo mismo 30 000 veces.
        auditoria.registrar(AccionAuditada.CUENTA_SINTETICA_ACTIVADA, null,
                "El sistema activó %d cuentas sintéticas de vecino del lote %s a %s, sin correo, consentimiento ni verificación de barrio"
                        .formatted(insertadas, correoDe(primero), correoDe(ultimoNumero)),
                ContextoDeAccion.delSistema());
        return insertadas;
    }

    private Usuario cuentaNumero(long numero, List<Sector> catalogo, long[] acumulado, ClaveHash clave, Instant ahora) {
        SectorId barrio = catalogo.get(indiceDeBarrio(numero, acumulado)).id();
        // Id derivado del número: la cuenta 241 es siempre la misma, así que repetir una pasada no la duplica.
        UsuarioId id = new UsuarioId(UUID.nameUUIDFromBytes(("cuenta-sintetica-" + numero).getBytes(StandardCharsets.UTF_8)).toString());
        Usuario invitada = Usuario.sinteticoComoVecino(id, new CorreoElectronico(correoDe(numero)),
                "Cuenta sintética %06d".formatted(numero), barrio, ahora);
        return invitada.aceptarInvitacion(clave, ahora);
    }

    private static String correoDe(long numero) {
        return "cuenta-sintetica-%06d@%s".formatted(numero, DOMINIO);
    }

    /** Peso = población, con un mínimo de 1 para los barrios sin dato censal (27 de 213). */
    private static long[] pesosAcumulados(List<Sector> catalogo) {
        long[] acumulado = new long[catalogo.size()];
        long suma = 0;
        for (int i = 0; i < catalogo.size(); i++) {
            Integer poblacion = catalogo.get(i).poblacion();
            suma += Math.max(1, poblacion == null ? 1 : poblacion);
            acumulado[i] = suma;
        }
        return acumulado;
    }

    private static int indiceDeBarrio(long numero, long[] acumulado) {
        long punto = new SplittableRandom(SEMILLA + numero).nextLong(acumulado[acumulado.length - 1]);
        int bajo = 0;
        int alto = acumulado.length - 1;
        while (bajo < alto) {
            int medio = (bajo + alto) >>> 1;
            if (acumulado[medio] > punto) {
                alto = medio;
            } else {
                bajo = medio + 1;
            }
        }
        return bajo;
    }
}
