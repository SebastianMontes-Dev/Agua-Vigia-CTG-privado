package com.aguavigia.ctg.api;

import com.aguavigia.ctg.api.dto.CorteRespuesta;
import com.aguavigia.ctg.api.mapper.CorteApiMapper;
import com.aguavigia.ctg.domain.CorteAgua;
import com.aguavigia.ctg.domain.EntidadNoEncontradaException;
import com.aguavigia.ctg.domain.Pagina;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.port.out.CorteAguaRepository;
import com.aguavigia.ctg.domain.port.out.SectorRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;

/**
 * RF002 — el histórico de cortes de un sector, público y de solo lectura. Va directo a los puertos de
 * salida, sin caso de uso (ADR-015): leer no tiene regla de negocio. Es el mismo dato que ve el panel
 * (`GET /api/veedor/cortes`), pero sin sesión: los cortes oficiales son información pública.
 */
@Tag(name = "Sectores", description = "Sectores de Cartagena y su estado del servicio")
@RestController
@RequestMapping(value = "/api/sectores/{sectorId}/cortes", produces = MediaType.APPLICATION_JSON_VALUE)
public class HistorialDeCortesController {

    private final SectorRepository sectores;
    private final CorteAguaRepository cortes;
    private final CorteApiMapper mapper;

    public HistorialDeCortesController(SectorRepository sectores, CorteAguaRepository cortes, CorteApiMapper mapper) {
        this.sectores = sectores;
        this.cortes = cortes;
        this.mapper = mapper;
    }

    @Operation(summary = "Histórico de cortes de un sector, del más reciente al más antiguo",
            description = """
                    Cortes oficiales que afectaron al sector, abiertos y cerrados. Paginado con las mismas
                    cabeceras que la bitácora (`X-Total-Count`, `X-Total-Pages`, `X-Page`, `X-Page-Size`, `Link`);
                    por defecto 50, máximo 200. Un sector sin cortes devuelve una lista vacía, no un 404.""")
    @ApiResponse(responseCode = "200", description = "Cortes de la página pedida")
    @ApiResponse(responseCode = "404", description = "No existe el sector")
    @GetMapping
    public ResponseEntity<List<CorteRespuesta>> listar(
            @PathVariable String sectorId,
            @RequestParam(required = false) Integer pagina,
            @RequestParam(required = false) Integer tamano) {

        SectorId id = new SectorId(sectorId);
        sectores.buscarPorId(id)
                .orElseThrow(() -> new EntidadNoEncontradaException("No existe el sector '" + sectorId + "'"));

        List<CorteAgua> ordenados = cortes.listarPorSector(id).stream()
                .sorted(Comparator.comparing((CorteAgua corte) -> corte.ventana().inicio()).reversed())
                .toList();
        int paginaPedida = Pagina.paginaValida(pagina);
        int tamanoPedido = Pagina.tamanoValido(tamano);
        int desde = (int) Math.min((long) paginaPedida * tamanoPedido, ordenados.size());
        int hasta = Math.min(desde + tamanoPedido, ordenados.size());
        List<CorteRespuesta> pagina1 = mapper.aRespuestas(ordenados.subList(desde, hasta));

        return CabecerasDePaginacion.respuesta(
                new Pagina<>(pagina1, paginaPedida, tamanoPedido, ordenados.size()), pagina1,
                "/api/sectores/" + sectorId + "/cortes");
    }
}
