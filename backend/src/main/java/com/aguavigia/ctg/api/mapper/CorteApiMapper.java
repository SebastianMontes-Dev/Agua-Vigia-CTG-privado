package com.aguavigia.ctg.api.mapper;

import com.aguavigia.ctg.api.dto.CorteRespuesta;
import com.aguavigia.ctg.api.dto.SolicitudCorte;
import com.aguavigia.ctg.domain.CorteAgua;
import com.aguavigia.ctg.domain.CorteId;
import com.aguavigia.ctg.domain.OrigenCorte;
import com.aguavigia.ctg.domain.SectorId;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;
import java.util.UUID;

@Mapper(componentModel = "spring")
public interface CorteApiMapper {

    /**
     * Todo por `expression`, no por `source`: `CorteAgua` no es un record (usa `Builder`), así
     * que MapStruct no reconoce `id()`, `ventana()`, etc. como propiedades de bean.
     */
    @Mapping(target = "id", expression = "java(corte.id().valor())")
    @Mapping(target = "sectoresAfectados", expression = "java(idsComoTexto(corte.sectoresAfectados()))")
    @Mapping(target = "inicio", expression = "java(corte.ventana().inicio())")
    @Mapping(target = "finPrometido", expression = "java(corte.ventana().finPrometido())")
    @Mapping(target = "finReal", expression = "java(corte.ventana().finReal())")
    @Mapping(target = "causa", expression = "java(corte.causa())")
    @Mapping(target = "origen", expression = "java(corte.origen().name())")
    @Mapping(target = "estado", expression = "java(corte.estado().name())")
    CorteRespuesta aRespuesta(CorteAgua corte);

    List<CorteRespuesta> aRespuestas(List<CorteAgua> cortes);

    default List<String> idsComoTexto(List<SectorId> sectoresAfectados) {
        return sectoresAfectados.stream().map(SectorId::valor).toList();
    }

    /**
     * El origen se fija aquí en VEEDOR, no lo decide el cliente (ver javadoc de CorteController):
     * un request arbitrario nunca puede declararse OFICIAL_ACUACAR ni INGESTA_IA.
     */
    default CorteAgua aDominio(SolicitudCorte solicitud) {
        return CorteAgua.builder()
                .id(new CorteId(UUID.randomUUID().toString()))
                .sectoresAfectados(solicitud.sectoresAfectados().stream().map(SectorId::new).toList())
                .inicio(solicitud.inicio())
                .finPrometido(solicitud.finPrometido())
                .causa(solicitud.causa())
                .origen(OrigenCorte.VEEDOR)
                .build();
    }
}
