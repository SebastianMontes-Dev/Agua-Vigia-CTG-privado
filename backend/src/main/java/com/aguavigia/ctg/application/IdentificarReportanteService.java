package com.aguavigia.ctg.application;

import com.aguavigia.ctg.domain.DispositivoId;
import com.aguavigia.ctg.domain.DispositivoInvalidoException;
import com.aguavigia.ctg.domain.EstadoCuenta;
import com.aguavigia.ctg.domain.HuellaDispositivo;
import com.aguavigia.ctg.domain.Reportante;
import com.aguavigia.ctg.domain.Usuario;
import com.aguavigia.ctg.domain.UsuarioId;
import com.aguavigia.ctg.domain.port.in.IdentificarReportanteUseCase;
import com.aguavigia.ctg.domain.port.out.DispositivoRepository;
import com.aguavigia.ctg.domain.port.out.FirmaDeDispositivosPort;
import com.aguavigia.ctg.domain.port.out.RelojPort;
import com.aguavigia.ctg.domain.port.out.UsuarioRepository;

import java.util.Optional;

/**
 * Sustituye a la `huella` que elegía el cliente. Aquí la identidad la decide el servidor: o una cuenta de
 * vecino activa, o un dispositivo cuyo token firmó este servidor y que sigue existiendo.
 *
 * La cuenta manda sobre el token: si valieran las dos, un vecino con sesión podría votar con su cuenta y con
 * un dispositivo, que son dos votos para una sola persona. Una cuenta que no sirve (suspendida, inexistente o
 * que no es de vecino) no bloquea a quien además trae un token válido: simplemente vota como dispositivo.
 */
public class IdentificarReportanteService implements IdentificarReportanteUseCase {

    private final DispositivoRepository dispositivos;
    private final FirmaDeDispositivosPort firma;
    private final UsuarioRepository usuarios;
    private final RelojPort reloj;

    public IdentificarReportanteService(DispositivoRepository dispositivos, FirmaDeDispositivosPort firma,
                                        UsuarioRepository usuarios, RelojPort reloj) {
        this.dispositivos = dispositivos;
        this.firma = firma;
        this.usuarios = usuarios;
        this.reloj = reloj;
    }

    @Override
    public Reportante identificar(String tokenDeDispositivo, UsuarioId cuentaDeVecino) {
        Optional<Reportante> comoVecino = cuentaDeVecino == null
                ? Optional.empty()
                : usuarios.buscarPorId(cuentaDeVecino).filter(IdentificarReportanteService::puedeReportarComoVecino)
                        .map(IdentificarReportanteService::reportanteDe);
        if (comoVecino.isPresent()) {
            return comoVecino.get();
        }
        return comoDispositivo(tokenDeDispositivo);
    }

    private static boolean puedeReportarComoVecino(Usuario usuario) {
        return usuario.esVecino() && usuario.estado() == EstadoCuenta.ACTIVA;
    }

    private static Reportante reportanteDe(Usuario vecino) {
        return new Reportante(HuellaDispositivo.deCuenta(vecino.id()), vecino.id(),
                vecino.barrioVerificado() ? vecino.barrio() : null);
    }

    private Reportante comoDispositivo(String token) {
        if (token == null || token.isBlank()) {
            throw new DispositivoInvalidoException(
                    "Falta la identidad del dispositivo. Pídela con POST /api/dispositivos y envíala en X-Dispositivo.");
        }
        DispositivoId id = firma.verificar(token.strip()).orElseThrow(() -> new DispositivoInvalidoException(
                "La identidad del dispositivo no es válida. Pide una nueva con POST /api/dispositivos."));
        dispositivos.buscarPorId(id).orElseThrow(() -> new DispositivoInvalidoException(
                "La identidad del dispositivo ya no existe. Pide una nueva con POST /api/dispositivos."));

        dispositivos.registrarVisto(id, reloj.ahora());
        return Reportante.anonimo(HuellaDispositivo.deDispositivo(id));
    }
}
