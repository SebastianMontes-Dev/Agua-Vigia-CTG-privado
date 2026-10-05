package com.aguavigia.ctg.api.mapper;

import com.aguavigia.ctg.api.dto.CoordenadaDTO;
import com.aguavigia.ctg.api.dto.ReporteModeracionRespuesta;
import com.aguavigia.ctg.domain.Coordenada;
import com.aguavigia.ctg.domain.ReporteCiudadano;
import com.aguavigia.ctg.domain.ReportesPendientes;
import org.mapstruct.Mapping;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ReporteModeracionApiMapper {

    /** El público ve la foto solo con el reporte aprobado; el panel la necesita antes, para decidir. */
    String RUTA_DE_FOTOS_DEL_PANEL = "/api/veedor/fotos/";

    @Mapping(target = "id", source = "id.valor")
    @Mapping(target = "sectorId", source = "sectorId.valor")
    @Mapping(target = "fotoEstado", expression = "java(reporte.estadoDeFoto().name())")
    @Mapping(target = "fotoUrl", expression = "java(reporte.nombreDeFoto().map(n -> RUTA_DE_FOTOS_DEL_PANEL + n).orElse(null))")
    @Mapping(target = "senalRed", ignore = true)
    ReporteModeracionRespuesta aRespuesta(ReporteCiudadano reporte);

    /** La cola de pendientes: cada reporte lleva su señal de red; las demás respuestas no la calculan. */
    default List<ReporteModeracionRespuesta> aRespuestas(ReportesPendientes pendientes) {
        return pendientes.pagina().contenido().stream()
                .map(reporte -> conSenalDeRed(aRespuesta(reporte), pendientes.enRafaga(reporte)))
                .toList();
    }

    private static ReporteModeracionRespuesta conSenalDeRed(ReporteModeracionRespuesta r, boolean enRafaga) {
        return new ReporteModeracionRespuesta(r.id(), r.sectorId(), r.tipo(), r.coordenada(), r.timestamp(),
                r.estadoModeracion(), r.verificacion(), r.fotoEstado(), r.fotoUrl(), enRafaga);
    }

    default CoordenadaDTO map(Coordenada coordenada) {
        return coordenada == null ? null : new CoordenadaDTO(coordenada.latitud(), coordenada.longitud());
    }
}
