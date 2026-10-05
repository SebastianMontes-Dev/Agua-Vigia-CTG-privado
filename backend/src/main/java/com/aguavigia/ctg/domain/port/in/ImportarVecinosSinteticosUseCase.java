package com.aguavigia.ctg.domain.port.in;

import com.aguavigia.ctg.domain.ClaveHash;

/**
 * Crea las cuentas sintéticas de vecino (D36): lo único inventado del sistema, para probar el volumen. Las hace el sistema,
 * con las reglas de alta de un vecino, sin correo ni consentimiento ni verificación de barrio fingidos.
 */
public interface ImportarVecinosSinteticosUseCase {

    /**
     * Completa hasta {@code objetivo} cuentas sintéticas. Idempotente: repetirlo solo crea las que faltan.
     *
     * @param claveCompartida el hash que llevan todas; nadie conoce la contraseña, así que ninguna cuenta se puede abrir
     * @return cuántas cuentas se crearon en esta pasada
     */
    int importar(int objetivo, ClaveHash claveCompartida);
}
