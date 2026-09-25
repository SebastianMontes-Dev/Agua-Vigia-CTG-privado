package com.aguavigia.ctg.infrastructure.persistence.mongo;

import com.aguavigia.ctg.domain.CorteId;
import com.aguavigia.ctg.domain.EstadoServicio;
import com.aguavigia.ctg.domain.EventoBitacora;
import com.aguavigia.ctg.domain.EventoId;
import com.aguavigia.ctg.domain.FiltroBitacora;
import com.aguavigia.ctg.domain.Pagina;
import com.aguavigia.ctg.domain.ReporteId;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.TipoEvento;
import com.aguavigia.ctg.domain.port.out.EventoBitacoraRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class EventoBitacoraMongoAdapter implements EventoBitacoraRepository {

    private final EventoBitacoraMongoRepository repositorio;
    private final MongoTemplate mongoTemplate;

    public EventoBitacoraMongoAdapter(EventoBitacoraMongoRepository repositorio, MongoTemplate mongoTemplate) {
        this.repositorio = repositorio;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public EventoBitacora guardar(EventoBitacora evento) {
        EventoBitacoraDocumento documento = new EventoBitacoraDocumento();
        documento.setId(evento.id() != null ? evento.id().valor() : UUID.randomUUID().toString());
        documento.setTipo(evento.tipo().name());
        documento.setSectorId(evento.sectorId() != null ? evento.sectorId().valor() : null);
        documento.setCorteId(evento.corteId() != null ? evento.corteId().valor() : null);
        documento.setTimestamp(evento.timestamp());
        documento.setDescripcion(evento.descripcion());
        documento.setEstado(evento.estado() != null ? evento.estado().name() : null);
        documento.setUrlOriginal(evento.urlOriginal());
        documento.setImagenUrl(evento.imagenUrl());
        documento.setReportesSustento(evento.reportesSustento().stream().map(ReporteId::valor).toList());

        repositorio.save(documento);
        return evento;
    }

    /**
     * Más recientes primero. Los índices `timestamp` y `sectorId`+`timestamp` de IndicesMongo cubren el
     * orden con y sin barrio; el tipo es de cuatro valores y se filtra sobre lo que el índice ya acotó.
     */
    @Override
    public Pagina<EventoBitacora> listar(FiltroBitacora filtro, int pagina, int tamano) {
        Query consulta = consultaDe(filtro);
        long total = mongoTemplate.count(consulta, EventoBitacoraDocumento.class);

        List<EventoBitacoraDocumento> documentos = mongoTemplate.find(
                consulta.with(PageRequest.of(pagina, tamano, Sort.by(Sort.Direction.DESC, "timestamp"))),
                EventoBitacoraDocumento.class);

        return new Pagina<>(
                documentos.stream().map(EventoBitacoraMongoAdapter::aDominio).toList(),
                pagina,
                tamano,
                total);
    }

    private static Query consultaDe(FiltroBitacora filtro) {
        Query consulta = new Query();
        if (filtro.sectorId() != null) {
            consulta.addCriteria(Criteria.where("sectorId").is(filtro.sectorId().valor()));
        }
        if (filtro.tipo() != null) {
            consulta.addCriteria(Criteria.where("tipo").is(filtro.tipo().name()));
        }
        if (filtro.desde() != null || filtro.hasta() != null) {
            Criteria rango = Criteria.where("timestamp");
            if (filtro.desde() != null) {
                rango.gte(filtro.desde());
            }
            if (filtro.hasta() != null) {
                rango.lt(filtro.hasta());
            }
            consulta.addCriteria(rango);
        }
        return consulta;
    }

    @Override
    public Optional<EventoBitacora> buscarPorId(EventoId id) {
        return repositorio.findById(id.valor()).map(EventoBitacoraMongoAdapter::aDominio);
    }

    private static EventoBitacora aDominio(EventoBitacoraDocumento documento) {
        return new EventoBitacora(
                new EventoId(documento.getId()),
                TipoEvento.valueOf(documento.getTipo()),
                documento.getSectorId() != null ? new SectorId(documento.getSectorId()) : null,
                documento.getCorteId() != null ? new CorteId(documento.getCorteId()) : null,
                documento.getTimestamp(),
                documento.getDescripcion(),
                documento.getEstado() != null ? EstadoServicio.valueOf(documento.getEstado()) : null,
                documento.getUrlOriginal(),
                documento.getImagenUrl(),
                documento.getReportesSustento() == null ? List.of()
                        : documento.getReportesSustento().stream().map(ReporteId::new).toList());
    }
}
