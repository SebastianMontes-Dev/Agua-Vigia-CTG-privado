package com.aguavigia.ctg.domain.port.in;

import com.aguavigia.ctg.domain.ClaveEnClaro;
import com.aguavigia.ctg.domain.ConsentimientosAceptados;
import com.aguavigia.ctg.domain.ContextoDeAccion;
import com.aguavigia.ctg.domain.CorreoElectronico;
import com.aguavigia.ctg.domain.SectorId;

/**
 * Registro abierto de un vecino (D11). A diferencia del registro del panel, probar el correo basta
 * para activar la cuenta: un vecino solo gestiona su propio perfil.
 */
public interface RegistrarVecinoUseCase {

    /**
     * No devuelve la cuenta ni indica si el correo ya existía (RNF024): quien ya tiene cuenta recibe
     * un aviso por correo; quien no, el enlace de verificación. Se rechaza con
     * {@link IllegalArgumentException}, antes de mirar el correo, un barrio ausente o inexistente y
     * la falta de consentimiento de privacidad.
     */
    void registrar(CorreoElectronico correo, String nombre, ClaveEnClaro clave, SectorId barrio,
                   ConsentimientosAceptados consentimientos, ContextoDeAccion contexto);
}
