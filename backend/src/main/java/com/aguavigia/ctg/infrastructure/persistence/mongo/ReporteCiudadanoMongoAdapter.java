package com.aguavigia.ctg.infrastructure.persistence.mongo;

import com.aguavigia.ctg.domain.Coordenada;
import com.aguavigia.ctg.domain.EstadoModeracion;
import com.aguavigia.ctg.domain.EvidenciaVencida;
import com.aguavigia.ctg.domain.HuellaDispositivo;
import com.aguavigia.ctg.domain.Pagina;
import com.aguavigia.ctg.domain.ReporteCiudadano;
import com.aguavigia.ctg.domain.ReporteId;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.TipoReporte;
import com.aguavigia.ctg.domain.port.out.RelojPort;
import com.aguavigia.ctg.domain.port.out.ReporteCiudadanoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOptions;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ReporteCiudadanoMongoAdapter implements ReporteCiudadanoRepository {

    private final ReporteCiudadanoMongoRepository repositorio;
    private final RelojPort reloj;
    private final MongoTemplate mongoTemplate;

    public ReporteCiudadanoMongoAdapter(ReporteCiudadanoMongoRepository repositorio, RelojPort reloj,
                                         MongoTemplate mongoTemplate) {
        this.repositorio = repositorio;
        this.reloj = reloj;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public ReporteCiudadano guardar(ReporteCiudadano reporte) {
        ReporteCiudadanoDocumento documento = new ReporteCiudadanoDocumento();
        documento.setId(reporte.id().valor());
        documento.setSectorId(reporte.sectorId().valor());
        documento.setTipo(reporte.tipo().name());
        if (reporte.coordenada() != null) {
            documento.setLatitud(reporte.coordenada().latitud());
            documento.setLongitud(reporte.coordenada().longitud());
        }
        documento.setHuella(reporte.huella().hash());
        documento.setTimestamp(reporte.timestamp());
        documento.setEstadoModeracion(reporte.estadoModeracion().name());
        documento.setFotoUrl(reporte.fotoUrl());
        documento.setHuellasConfirmacion(reporte.huellasConfirmacion());

        repositorio.save(documento);
        return reporte;
    }

    @Override
    public List<ReporteCiudadano> listarRecientesPorSector(SectorId sectorId, Duration ventana) {
        var desde = reloj.ahora().minus(ventana);
        return repositorio.findBySectorIdAndTimestampGreaterThanEqualAndEstadoModeracionNot(
                        sectorId.valor(), desde, EstadoModeracion.DESCARTADO.name()).stream()
                .map(ReporteCiudadanoMongoAdapter::aDominio)
                .toList();
    }

    /**
     * La ordenación por `timestamp` descendente sigue el índice `sectorId+timestamp`, así que Mongo no
     * ordena en memoria; `allowDiskUse` es solo la red de seguridad si el plan cambiara. El resultado
     * son como mucho tres filas, sea cual sea el tamaño de la ventana.
     */
    @Override
    public Map<TipoReporte, Long> contarVotosRecientes(SectorId sectorId, Duration ventana) {
        var desde = reloj.ahora().minus(ventana);
        Aggregation agregacion = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("sectorId").is(sectorId.valor())
                        .and("timestamp").gte(desde)
                        .and("estadoModeracion").ne(EstadoModeracion.DESCARTADO.name())),
                Aggregation.sort(Sort.Direction.DESC, "timestamp"),
                Aggregation.group("huella").first("tipo").as("tipo"),
                Aggregation.group("tipo").count().as("votos"))
                .withOptions(AggregationOptions.builder().allowDiskUse(true).build());
        Map<TipoReporte, Long> votos = new EnumMap<>(TipoReporte.class);
        mongoTemplate.aggregate(agregacion, "reportes", org.bson.Document.class).getMappedResults()
                .forEach(fila -> votos.put(TipoReporte.valueOf(fila.getString("_id")),
                        ((Number) fila.get("votos")).longValue()));
        return votos;
    }

    @Override
    public long contarRecientesPorSectorYDispositivo(SectorId sectorId, Duration ventana, HuellaDispositivo huella) {
        var desde = reloj.ahora().minus(ventana);
        return repositorio.countBySectorIdAndTimestampGreaterThanEqualAndHuella(
                sectorId.valor(), desde, huella.hash());
    }

    @Override
    public Optional<ReporteCiudadano> buscarPorId(ReporteId id) {
        return repositorio.findById(id.valor()).map(ReporteCiudadanoMongoAdapter::aDominio);
    }

    /** Más antiguos primero: la cola de moderación se atiende en orden de llegada. */
    @Override
    public Pagina<ReporteCiudadano> listarPendientes(int pagina, int tamano) {
        Page<ReporteCiudadanoDocumento> resultado = repositorio.findPendientesIncluyendoNulo(
                EstadoModeracion.PENDIENTE.name(),
                PageRequest.of(pagina, tamano, Sort.by(Sort.Direction.ASC, "timestamp")));

        return new Pagina<>(
                resultado.getContent().stream().map(ReporteCiudadanoMongoAdapter::aDominio).toList(),
                pagina,
                tamano,
                resultado.getTotalElements());
    }

    @Override
    public Set<String> listarNombresDeFotoReferenciados() {
        // Proyección a solo fotoUrl: este job nocturno recorre TODA la colección de reportes (que
        // se conserva indefinidamente por diseño, ver PurgaEvidenciaAntiguaJob), y traer el
        // documento completo por cada uno solo para leer un campo es el sobre-fetch más caro de
        // los dos jobs de mantenimiento.
        Query query = Query.query(Criteria.where("fotoUrl").ne(null));
        query.fields().include("fotoUrl");
        return mongoTemplate.find(query, ReporteCiudadanoDocumento.class).stream()
                .map(ReporteCiudadanoDocumento::getFotoUrl)
                .map(ReporteCiudadanoMongoAdapter::nombreDeArchivo)
                .collect(Collectors.toSet());
    }

    @Override
    public List<EvidenciaVencida> listarEvidenciaAnteriorA(Instant limite) {
        Query query = Query.query(Criteria.where("fotoUrl").ne(null).and("timestamp").lt(limite));
        query.fields().include("fotoUrl");
        return mongoTemplate.find(query, ReporteCiudadanoDocumento.class).stream()
                .map(doc -> new EvidenciaVencida(new ReporteId(doc.getId()), doc.getFotoUrl()))
                .toList();
    }

    @Override
    public void quitarFotosDe(List<ReporteId> ids) {
        if (ids.isEmpty()) {
            return;
        }
        List<String> valores = ids.stream().map(ReporteId::valor).toList();
        Query query = Query.query(Criteria.where("_id").in(valores));
        mongoTemplate.updateMulti(query, Update.update("fotoUrl", null), ReporteCiudadanoDocumento.class);
    }

    private static String nombreDeArchivo(String fotoUrl) {
        int barra = fotoUrl.lastIndexOf('/');
        return barra >= 0 ? fotoUrl.substring(barra + 1) : fotoUrl;
    }

    private static ReporteCiudadano aDominio(ReporteCiudadanoDocumento documento) {
        Coordenada coordenada = documento.getLatitud() != null
                ? new Coordenada(documento.getLatitud(), documento.getLongitud())
                : null;
        // Nulo en documentos sembrados antes de RF018 (ADR-023): se tratan como PENDIENTE, no como
        // ya moderados — un reporte viejo sin decision del veedor sigue siendo candidato.
        EstadoModeracion estado = documento.getEstadoModeracion() != null
                ? EstadoModeracion.valueOf(documento.getEstadoModeracion())
                : EstadoModeracion.PENDIENTE;
        java.util.Set<String> confirmaciones = documento.getHuellasConfirmacion() != null 
                ? documento.getHuellasConfirmacion() 
                : java.util.Collections.emptySet();
        return new ReporteCiudadano(
                new ReporteId(documento.getId()),
                new SectorId(documento.getSectorId()),
                TipoReporte.valueOf(documento.getTipo()),
                coordenada,
                new HuellaDispositivo(documento.getHuella()),
                documento.getTimestamp(),
                estado,
                documento.getFotoUrl(),
                confirmaciones);
    }
}
