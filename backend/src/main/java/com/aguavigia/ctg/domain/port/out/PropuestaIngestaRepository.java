package com.aguavigia.ctg.domain.port.out;

import com.aguavigia.ctg.domain.EstadoServicio;
import com.aguavigia.ctg.domain.Pagina;
import com.aguavigia.ctg.domain.PropuestaId;
import com.aguavigia.ctg.domain.PropuestaIngesta;
import com.aguavigia.ctg.domain.SectorId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PropuestaIngestaRepository {

    PropuestaIngesta guardar(PropuestaIngesta propuesta);

    Optional<PropuestaIngesta> buscarPorId(PropuestaId id);

    /** La cola de revisión del veedor, más recientes primero y paginada. */
    Pagina<PropuestaIngesta> listarPendientes(int pagina, int tamano);

    /**
     * Los cuatro feeds configurados (Google News, Zona Cero, Caracol, W Radio) cubren las mismas
     * noticias, y cada versión tiene su propio hash — así que `DeduplicadorReciente` no las une.
     * Sin este chequeo, un solo corte le deja al veedor cuatro propuestas idénticas que revisar.
     */
    boolean existePendiente(SectorId sectorId, EstadoServicio estadoPropuesto);

    /**
     * Si ya se registró una propuesta de este boletín para este barrio, en **cualquier** estado de revisión (también las anuladas y
     * descartadas: lo que el veedor decidió no se deshace porque el colector vuelva a leer el mismo boletín). Es lo que hace idempotente
     * la ingesta: ni la marca de lectura ni el deduplicador de Redis sobreviven a todo (un Redis vaciado, el respaldo local releyendo el
     * archivo). Un boletín con varias zonas puede nombrar el mismo barrio con otra ventana: por eso entra también el inicio declarado.
     */
    boolean existeDelBoletin(SectorId sectorId, String urlOriginal, EstadoServicio estadoPropuesto, java.time.Instant inicioDeclarado);

    /**
     * Propuestas ya aprobadas cuya ventana declarada todavía puede mover el estado de un sector:
     * las que aún no terminan, más las que acaban de terminar y falta devolver el barrio a
     * CON_SERVICIO. Acotar por {@code finDesde} evita recorrer el histórico entero en cada barrido.
     */
    List<PropuestaIngesta> listarAprobadasConVentanaVigente(Instant finDesde);

    /**
     * Todo lo aprobado de un barrio, con o sin ventana declarada: el resolutor decide qué pesa ahora.
     * Un boletín de restablecimiento no trae ventana, así que no lo alcanza la consulta de arriba.
     */
    List<PropuestaIngesta> listarAprobadasPorSector(SectorId sectorId);
}
