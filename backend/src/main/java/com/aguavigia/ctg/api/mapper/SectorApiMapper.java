package com.aguavigia.ctg.api.mapper;

import com.aguavigia.ctg.api.dto.SectorRespuesta;
import com.aguavigia.ctg.domain.RespaldoVecinal;
import com.aguavigia.ctg.domain.Sector;
import com.aguavigia.ctg.domain.VentanaTiempo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * Dominio -> DTO. Nunca al reves: no hay ningun punto de la API que construya un Sector desde
 * la red en el Sprint 1.
 */
@Mapper(componentModel = "spring")
public interface SectorApiMapper {

    /**
     * RF003 — `actualizadoEn` es la antiguedad del dato que el mapa muestra junto a cada sector.
     * Viaja nulo solo cuando el sector todavia no tiene estado registrado: sin estado no hay fecha
     * de estado, y el cliente debe presentarlo como "sin datos" (ADR-014).
     */
    @Mapping(target = "id", source = "id.valor")
    @Mapping(target = "estado", source = "estadoActual")
    @Mapping(target = "actualizadoEn", source = "estadoActualizadoEn")
    @Mapping(target = "verificadoEn", source = "estadoVerificadoEn")
    @Mapping(target = "origen", source = "marcas.origen")
    @Mapping(target = "ventanaPrometida", source = "marcas.ventanaPrometida")
    @Mapping(target = "restablecimientoPorConfirmar", source = "marcas.porConfirmar")
    @Mapping(target = "enDisputa", source = "marcas.enDisputa")
    @Mapping(target = "reportesEnContra", source = "marcas.reportesEnContra")
    @Mapping(target = "respaldo", source = "marcas.respaldo")
    SectorRespuesta aRespuesta(Sector sector);

    List<SectorRespuesta> aRespuestas(List<Sector> sectores);

    @Mapping(target = "fin", source = "finPrometido")
    SectorRespuesta.VentanaPrometidaRespuesta aVentanaPrometida(VentanaTiempo ventana);

    SectorRespuesta.RespaldoVecinalRespuesta aRespaldo(RespaldoVecinal respaldo);
}
