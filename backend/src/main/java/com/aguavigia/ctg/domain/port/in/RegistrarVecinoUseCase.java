package com.aguavigia.ctg.domain.port.in;

import com.aguavigia.ctg.domain.ClaveEnClaro;
import com.aguavigia.ctg.domain.ConsentimientosAceptados;
import com.aguavigia.ctg.domain.ContextoDeAccion;
import com.aguavigia.ctg.domain.CorreoElectronico;
import com.aguavigia.ctg.domain.SectorId;

/**
 * Registro abierto de un vecino (D11). A diferencia del registro del panel, no hay aprobación: el vecino
 * elige su clave desde el enlace del correo y su cuenta queda activa, porque solo gestiona su propio perfil.
 * La clave no viaja en el registro: si la eligiera quien rellena el formulario, podría registrar el correo
 * de otra persona con una clave suya.
 */
public interface RegistrarVecinoUseCase {

    /**
     * No devuelve la cuenta ni indica si el correo ya existía (RNF024): quien ya tiene cuenta recibe
     * un aviso por correo; quien no, el enlace para elegir su clave. Se rechaza con
     * {@link IllegalArgumentException}, antes de mirar el correo, un barrio ausente o inexistente y
     * la falta de consentimiento de privacidad.
     */
    void registrar(CorreoElectronico correo, String nombre, SectorId barrio,
                   ConsentimientosAceptados consentimientos, ContextoDeAccion contexto);
}
