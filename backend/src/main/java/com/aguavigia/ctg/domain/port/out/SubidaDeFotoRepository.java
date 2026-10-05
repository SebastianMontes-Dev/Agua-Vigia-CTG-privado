package com.aguavigia.ctg.domain.port.out;

import com.aguavigia.ctg.domain.ReporteId;

import java.time.Instant;

/**
 * Los tokens de subida de foto: de un solo uso y de un reporte concreto. Quien conozca el id de un reporte ajeno no
 * puede ponerle una foto sin el token, que solo recibe su autor al reportar.
 *
 * Se guarda el hash del token, nunca el token: no hace falta para comprobarlo y una lectura de la base no debe
 * bastar para subir fotos.
 */
public interface SubidaDeFotoRepository {

    /** Deja un solo token vivo por reporte: guardar otro invalida el anterior. */
    void guardar(ReporteId reporte, String hashDelToken, Instant venceEn);

    /** true una sola vez: solo si el token existe, es de este reporte y no venció. Lo consume atómicamente. */
    boolean consumir(ReporteId reporte, String hashDelToken, Instant ahora);
}
