package com.aguavigia.ctg.domain.port.in;

import com.aguavigia.ctg.domain.ReporteCiudadano;

public interface AgregarEvidenciaUseCase {

    /**
     * {@code tokenDeSubida} es el que recibió el autor al reportar (de un solo uso, atado a ese reporte): sin él, falla con
     * {@code SubidaNoAutorizadaException}. {@code contentType} es el declarado por el cliente (p. ej. "image/jpeg"); se
     * valida contra una lista blanca y contra los primeros bytes del archivo.
     */
    ReporteCiudadano agregarEvidencia(String reporteId, String tokenDeSubida, String contentType, byte[] contenido);
}
