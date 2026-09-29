package com.aguavigia.ctg.domain.port.out;

import com.aguavigia.ctg.domain.DocumentoFallido;

import java.util.List;

/** Solo lectura: quien escribe la cola de fallidos es el pipeline de ingesta, que vive en infraestructura. */
public interface DocumentosFallidosPort {

    /** Los 200 más recientes primero, el mismo tope que las demás colas paginadas del veedor. */
    List<DocumentoFallido> masRecientes();
}
