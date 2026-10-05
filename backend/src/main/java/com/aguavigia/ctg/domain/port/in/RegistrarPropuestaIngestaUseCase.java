package com.aguavigia.ctg.domain.port.in;

import com.aguavigia.ctg.domain.AvisoDeIngesta;
import com.aguavigia.ctg.domain.EstadoServicio;
import com.aguavigia.ctg.domain.PropuestaIngesta;
import com.aguavigia.ctg.domain.SectorId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** M9 — lo que el pipeline de ingesta llama en vez de escribir el estado del sector. */
public interface RegistrarPropuestaIngestaUseCase {

    /**
     * Registra lo que dice una zona de un boletín, decidiendo en conjunto —con todos sus sectores a la vista— si puede
     * publicarse sola (D5) o espera al veedor con el motivo. Devuelve una propuesta por cada sector que existe y no
     * tiene ya una idéntica pendiente.
     */
    List<PropuestaIngesta> registrarAviso(AvisoDeIngesta aviso);

    /**
     * Un solo sector, sin contexto de zona: equivale a un aviso de un barrio sin alias ambiguos.
     *
     * Vacío cuando la propuesta no se registra: o el sector no existe, o ya hay una pendiente
     * idéntica esperando revisión. Ninguno de los dos casos es un error del pipeline — no debe
     * cortar el ciclo por eso.
     */
    Optional<PropuestaIngesta> registrar(SectorId sectorId, EstadoServicio estadoPropuesto,
                                          String fuente, String urlOriginal, String citaTextual,
                                          double confianza, Instant inicioDeclarado,
                                          Instant finPrometido,
                                          String imagenUrl,
                                          Instant publicadoEn,
                                          String tituloOriginal);
}
