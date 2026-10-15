package com.aguavigia.ctg.api.mapper;

import com.aguavigia.ctg.api.dto.ConteosAnaliticosRespuesta;
import com.aguavigia.ctg.api.dto.CopiaAnaliticaRespuesta;
import com.aguavigia.ctg.api.dto.CumplimientoAnaliticoRespuesta;
import com.aguavigia.ctg.api.dto.PuntoMensualAnaliticoRespuesta;
import com.aguavigia.ctg.domain.ConteosAnaliticos;
import com.aguavigia.ctg.domain.CumplimientoAnalitico;
import com.aguavigia.ctg.domain.PuntoMensualAnalitico;
import com.aguavigia.ctg.domain.RegistroDeCopia;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.Instant;
import java.util.List;

@Mapper(componentModel = "spring")
public interface AnaliticaApiMapper {

    default CumplimientoAnaliticoRespuesta aRespuesta(CumplimientoAnalitico indice, Instant copiadoEn) {
        return new CumplimientoAnaliticoRespuesta(indice.sectorId(), indice.nombre(), indice.paresCerrados(),
                indice.prometidaSegundos(), indice.realSegundos(), indice.desviacionSegundos(),
                indice.porcentajeCumplimiento(), copiadoEn);
    }

    /** `periodo` sale como "2026-08": YearMonth.toString() ya es ISO 8601. */
    @Mapping(target = "periodo", expression = "java(punto.mes().toString())")
    @Mapping(target = "duracionPrometidaSegundos", source = "prometidaSegundos")
    @Mapping(target = "duracionRealSegundos", source = "realSegundos")
    PuntoMensualAnaliticoRespuesta aRespuesta(PuntoMensualAnalitico punto);

    List<PuntoMensualAnaliticoRespuesta> aRespuestas(List<PuntoMensualAnalitico> puntos);

    ConteosAnaliticosRespuesta aRespuesta(ConteosAnaliticos conteos);

    @Mapping(target = "resultado", expression = "java(copia.resultado().name())")
    CopiaAnaliticaRespuesta aRespuesta(RegistroDeCopia copia);

    List<CopiaAnaliticaRespuesta> aRespuestasDeCopias(List<RegistroDeCopia> copias);
}
