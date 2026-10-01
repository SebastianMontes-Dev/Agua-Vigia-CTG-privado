package com.aguavigia.ctg.domain.port.in;

import com.aguavigia.ctg.domain.ContextoDeAccion;
import com.aguavigia.ctg.domain.Coordenada;
import com.aguavigia.ctg.domain.Usuario;
import com.aguavigia.ctg.domain.UsuarioId;

public interface VerificarBarrioVecinoUseCase {

    /**
     * Comprueba con la ubicación del momento que el vecino está en el barrio que declaró, y guarda solo
     * el resultado (barrio verificado y fecha). La coordenada se usa y se descarta: no se persiste ni
     * se audita.
     */
    Usuario verificar(UsuarioId vecino, Coordenada coordenada, double precisionMetros, ContextoDeAccion contexto);
}
