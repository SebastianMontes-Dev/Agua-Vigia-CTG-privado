package com.aguavigia.ctg.domain.port.in;

import com.aguavigia.ctg.domain.Reportante;
import com.aguavigia.ctg.domain.UsuarioId;

public interface IdentificarReportanteUseCase {

    /**
     * Quien reporta lo hace desde su cuenta de vecino (si la tiene activa) o con un token de dispositivo. La cuenta
     * manda sobre el token. Sin ninguna de las dos, o con un token que no se pueda comprobar, se rechaza con
     * {@link com.aguavigia.ctg.domain.DispositivoInvalidoException}.
     *
     * @param tokenDeDispositivo la cabecera `X-Dispositivo`, o nulo
     * @param cuentaDeVecino la cuenta de la sesión si es de un vecino, o nulo
     */
    Reportante identificar(String tokenDeDispositivo, UsuarioId cuentaDeVecino);
}
