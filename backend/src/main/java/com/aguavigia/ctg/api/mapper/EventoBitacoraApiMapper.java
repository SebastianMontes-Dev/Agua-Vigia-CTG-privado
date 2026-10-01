package com.aguavigia.ctg.api.mapper;

import com.aguavigia.ctg.api.dto.EventoBitacoraRespuesta;
import com.aguavigia.ctg.domain.CorteId;
import com.aguavigia.ctg.domain.EventoBitacora;
import com.aguavigia.ctg.domain.ReporteId;
import com.aguavigia.ctg.domain.SectorId;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface EventoBitacoraApiMapper {

    @Mapping(target = "id", source = "id.valor")
    @Mapping(target = "tipo", expression = "java(evento.tipo().name())")
    @Mapping(target = "estado", expression = "java(evento.estado() == null ? null : evento.estado().name())")
    @Mapping(target = "cantidadReportesSustento", expression = "java(evento.reportesSustento().size())")
    @Mapping(target = "fuente", expression = "java(evento.fuente() == null ? null : evento.fuente().name())")
    @Mapping(target = "respaldo", expression = "java(evento.respaldo() == null ? null : new com.aguavigia.ctg.api.dto.EventoBitacoraRespuesta.RespaldoRespuesta(evento.respaldo().vecinos(), evento.respaldo().umbral()))")
    EventoBitacoraRespuesta aRespuesta(EventoBitacora evento);

    List<EventoBitacoraRespuesta> aRespuestas(List<EventoBitacora> eventos);

    default String map(SectorId sectorId) {
        return sectorId == null ? null : sectorId.valor();
    }

    default String map(ReporteId reporteId) {
        return reporteId == null ? null : reporteId.valor();
    }

    default String map(CorteId corteId) {
        return corteId == null ? null : corteId.valor();
    }
}
