package com.aguavigia.ctg.api.mapper;

import com.aguavigia.ctg.api.dto.ReporteRespuesta;
import com.aguavigia.ctg.domain.ReporteCiudadano;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ReporteApiMapper {

    /** Ruta pública de las fotos. Un reporte anterior a F3 guarda «/fotos/x.jpg»: sale igual, con la ruta nueva. */
    String RUTA_DE_FOTOS = "/api/fotos/";

    @Mapping(target = "id", source = "id.valor")
    @Mapping(target = "sectorId", source = "sectorId.valor")
    @Mapping(target = "confirmaciones", expression = "java(reporte.numeroConfirmaciones())")
    @Mapping(target = "fotoUrl", expression = "java(reporte.nombreDeFoto().map(n -> RUTA_DE_FOTOS + n).orElse(null))")
    @Mapping(target = "fotoEstado", expression = "java(reporte.estadoDeFoto().name())")
    @Mapping(target = "subidaToken", ignore = true)
    ReporteRespuesta aRespuesta(ReporteCiudadano reporte);
}
