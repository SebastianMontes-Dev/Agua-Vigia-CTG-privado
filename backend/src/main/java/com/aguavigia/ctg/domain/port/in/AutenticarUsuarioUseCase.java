package com.aguavigia.ctg.domain.port.in;

import com.aguavigia.ctg.domain.ContextoDeAccion;
import com.aguavigia.ctg.domain.CorreoElectronico;
import com.aguavigia.ctg.domain.SesionEmitida;

public interface AutenticarUsuarioUseCase {

    /** `codigoTotp` nulo si la cuenta no tiene segundo factor o si todavía no se ha pedido. */
    SesionEmitida autenticar(CorreoElectronico correo, String claveEnClaro, String codigoTotp,
                             ContextoDeAccion contexto);

    /**
     * Ingreso de un vecino registrado (D11). Mismas reglas de bloqueo y mismo mensaje de error que el
     * del panel, pero solo abre sesión a cuentas VECINO, sin segundo factor.
     */
    SesionEmitida autenticarVecino(CorreoElectronico correo, String claveEnClaro, ContextoDeAccion contexto);
}
