package com.aguavigia.ctg.api;

import com.aguavigia.ctg.api.dto.EventoBitacoraRespuesta;
import com.aguavigia.ctg.api.mapper.EventoBitacoraApiMapper;
import com.aguavigia.ctg.domain.EventoBitacora;
import com.aguavigia.ctg.domain.EventoId;
import com.aguavigia.ctg.domain.FiltroBitacora;
import com.aguavigia.ctg.domain.ReporteId;
import com.aguavigia.ctg.domain.Pagina;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.TipoEvento;
import com.aguavigia.ctg.domain.port.in.ConsultarSustentoDeEventoUseCase;
import com.aguavigia.ctg.domain.port.out.EventoBitacoraRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * M8 — RF026-RF028: bitácora pública, de solo lectura y sin autenticación (RF027). Va directo al
 * puerto de salida, sin caso de uso intermedio (ADR-015): no hay regla de negocio en leer, solo en
 * anexar — eso lo hace `RegistrarEventoBitacoraUseCase`, que ningún controlador expone: nadie
 * externo debería poder anexar un evento arbitrario a la bitácora.
 *
 * La inmutabilidad de RF028 no depende de este controlador: `EventoBitacoraRepository` no declara
 * ni editar ni eliminar, así que no hay manera de romperla desde la API aunque se quisiera.
 */
@Tag(name = "Bitácora", description = "Bitácora pública de eventos, de solo anexado (RF026-RF028)")
@RestController
@RequestMapping(value = "/api/bitacora", produces = MediaType.APPLICATION_JSON_VALUE)
public class BitacoraController {

    private final EventoBitacoraRepository eventos;
    private final EventoBitacoraApiMapper mapper;
    private final ConsultarSustentoDeEventoUseCase sustento;

    public BitacoraController(EventoBitacoraRepository eventos, EventoBitacoraApiMapper mapper,
                              ConsultarSustentoDeEventoUseCase sustento) {
        this.eventos = eventos;
        this.mapper = mapper;
        this.sustento = sustento;
    }

    @Operation(summary = "Listar los eventos de la bitácora, más recientes primero",
            description = """
                    Paginado: la bitácora es de solo anexado (RF028), así que crece sin cota.
                    El total, la página y el enlace a la siguiente viajan en las cabeceras
                    `X-Total-Count`, `X-Total-Pages`, `X-Page`, `X-Page-Size` y `Link` — el cuerpo
                    sigue siendo un arreglo JSON, así que un cliente que las ignore no se rompe.
                    Por defecto 50 eventos; el máximo por página es 200.

                    Filtros opcionales, que se combinan y buscan en todo el historial: `sectorId`,
                    `tipo`, `desde` (inclusivo) y `hasta` (exclusivo), ambos instantes ISO 8601 en
                    UTC. El enlace `Link` a la siguiente página conserva los filtros. Sin
                    coincidencias, la respuesta es una página vacía, no un error; un `tipo` que no
                    existe o un `hasta` que no es posterior a `desde` son un 400.""")
    @ApiResponse(responseCode = "200", description = "Listado generado")
    @ApiResponse(responseCode = "400", description = "Tipo de evento desconocido, fecha mal formada o rango invertido",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping
    public ResponseEntity<List<EventoBitacoraRespuesta>> listar(
            @RequestParam(required = false) Integer pagina,
            @RequestParam(required = false) Integer tamano,
            @RequestParam(required = false) String sectorId,
            @Parameter(schema = @Schema(allowableValues = {"CORTE_ANUNCIADO", "CORTE_CONFIRMADO_POR_CIUDADANOS",
                    "CORTE_RESTABLECIDO", "CORTE_DETECTADO_POR_INGESTA"}))
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant hasta) {

        String barrio = sectorId == null || sectorId.isBlank() ? null : sectorId;
        TipoEvento tipoEvento = tipo == null || tipo.isBlank() ? null : tipoDe(tipo);
        FiltroBitacora filtro = new FiltroBitacora(
                barrio == null ? null : new SectorId(barrio), tipoEvento, desde, hasta);

        Pagina<EventoBitacora> resultado = eventos.listar(
                filtro, Pagina.paginaValida(pagina), Pagina.tamanoValido(tamano));

        Map<String, Object> filtros = new LinkedHashMap<>();
        filtros.put("sectorId", barrio);
        filtros.put("tipo", tipoEvento);
        filtros.put("desde", desde);
        filtros.put("hasta", hasta);
        return CabecerasDePaginacion.respuesta(
                resultado, mapper.aRespuestas(resultado.contenido()), "/api/bitacora", filtros);
    }

    /** Traduce el texto del cliente al enum sin exponer el mensaje de `valueOf`, que nombra la clase del dominio. */
    private static TipoEvento tipoDe(String texto) {
        try {
            return TipoEvento.valueOf(texto);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Tipo de evento inválido '" + texto + "'. Valores permitidos: "
                    + Arrays.stream(TipoEvento.values()).map(Enum::name).collect(Collectors.joining(", ")));
        }
    }

    @Operation(summary = "Los reportes que sustentan un evento de consenso (RF011)",
            description = """
                    Ids de los reportes ciudadanos que sostuvieron el cambio de estado, para contrastarlo con la
                    evidencia. Van aparte del listado porque en una avería grande pueden ser miles. Paginado con las
                    mismas cabeceras que el listado; por defecto 50 ids por página, máximo 200. Vacío en los
                    eventos que no son de consenso.""")
    @ApiResponse(responseCode = "200", description = "Ids de la página pedida (vacía si se pasa del final)")
    @ApiResponse(responseCode = "404", description = "No existe el evento",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/{id}/sustento")
    public ResponseEntity<List<String>> sustento(
            @PathVariable String id,
            @RequestParam(required = false) Integer pagina,
            @RequestParam(required = false) Integer tamano) {

        Pagina<ReporteId> resultado = sustento.sustento(new EventoId(id), pagina, tamano);
        List<String> ids = resultado.contenido().stream().map(ReporteId::valor).toList();

        return CabecerasDePaginacion.respuesta(
                new Pagina<>(ids, resultado.pagina(), resultado.tamano(), resultado.totalElementos()), ids,
                "/api/bitacora/" + id + "/sustento");
    }
}
