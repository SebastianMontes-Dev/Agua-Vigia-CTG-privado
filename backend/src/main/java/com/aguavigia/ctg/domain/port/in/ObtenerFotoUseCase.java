package com.aguavigia.ctg.domain.port.in;

import com.aguavigia.ctg.domain.FotoLeida;

import java.util.Optional;

/**
 * Servir una foto. Vacío significa «no hay nada que mostrar» y se responde igual en todos los casos (no existe, no es
 * pública, ya no está en disco): distinguirlos diría qué fotos hay.
 */
public interface ObtenerFotoUseCase {

    /** Solo la foto de un reporte aprobado cuya foto no se descartó. */
    Optional<FotoLeida> paraPublico(String nombre);

    /** El panel ve toda foto que algún reporte reclame, en cualquier estado: es la evidencia que modera. */
    Optional<FotoLeida> paraPanel(String nombre);
}
