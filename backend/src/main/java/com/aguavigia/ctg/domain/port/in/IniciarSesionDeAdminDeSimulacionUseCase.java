package com.aguavigia.ctg.domain.port.in;

import com.aguavigia.ctg.domain.ContextoDeAccion;
import com.aguavigia.ctg.domain.SesionEmitida;

/**
 * Sesión del ADMIN inicial sin clave ni segundo factor, para que el simulador pueda invitar a las demás cuentas del panel. Solo existe en
 * la instancia de simulación, detrás de la clave de simulación.
 */
public interface IniciarSesionDeAdminDeSimulacionUseCase {

    SesionEmitida iniciar(ContextoDeAccion contexto);
}
