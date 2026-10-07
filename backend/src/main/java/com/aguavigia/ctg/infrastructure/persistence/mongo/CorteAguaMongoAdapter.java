package com.aguavigia.ctg.infrastructure.persistence.mongo;

import com.aguavigia.ctg.domain.AgregadoDuraciones;
import com.aguavigia.ctg.domain.CalidadDelDato;
import com.aguavigia.ctg.domain.CierreDeCorte;
import com.aguavigia.ctg.domain.CorteAgua;
import com.aguavigia.ctg.domain.CorteId;
import com.aguavigia.ctg.domain.EstadoCorte;
import com.aguavigia.ctg.domain.OrigenCorte;
import com.aguavigia.ctg.domain.OrigenEstado;
import com.aguavigia.ctg.domain.PuntoAgregadoMensual;
import com.aguavigia.ctg.domain.SectorId;
import com.aguavigia.ctg.domain.port.out.CorteAguaRepository;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Adaptador de CorteAguaRepository sobre MongoDB (RF016-RF017).
 *
 * A diferencia de SectorMongoAdapter, aqui no hay que preservar campos ajenos al leer antes de
 * guardar: CorteAgua es dueño exclusivo de su documento, nadie mas lo siembra ni lo enriquece.
 */
@Component
public class CorteAguaMongoAdapter implements CorteAguaRepository {

    private final CorteAguaMongoRepository repositorio;
    private final MongoTemplate mongoTemplate;

    public CorteAguaMongoAdapter(CorteAguaMongoRepository repositorio, MongoTemplate mongoTemplate) {
        this.repositorio = repositorio;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Optional<CorteAgua> buscarPorId(CorteId id) {
        return repositorio.findById(id.valor()).map(CorteAguaMongoAdapter::aDominio);
    }

    @Override
    public List<CorteAgua> listarPorSector(SectorId sectorId) {
        return repositorio.findBySectoresAfectadosContaining(sectorId.valor()).stream()
                .map(CorteAguaMongoAdapter::aDominio)
                .toList();
    }

    @Override
    public List<CorteAgua> listarPorSectores(List<SectorId> sectorIds) {
        List<String> valores = sectorIds.stream().map(SectorId::valor).toList();
        return repositorio.findBySectoresAfectadosIn(valores).stream()
                .map(CorteAguaMongoAdapter::aDominio)
                .toList();
    }

    @Override
    public List<CorteAgua> listarAbiertos() {
        return repositorio.findByEstadoIn(List.of(EstadoCorte.ANUNCIADO.name(), EstadoCorte.CONFIRMADO.name())).stream()
                .map(CorteAguaMongoAdapter::aDominio)
                .toList();
    }

    @Override
    public List<CorteAgua> listarConCierresProvisionales() {
        Query query = Query.query(Criteria.where("cierres").elemMatch(Criteria.where("provisional").is(true))
                .and("estado").ne(EstadoCorte.ANULADO.name()));
        return mongoTemplate.find(query, CorteAguaDocumento.class).stream()
                .map(CorteAguaMongoAdapter::aDominio)
                .toList();
    }

    @Override
    public List<CorteAgua> listarTodos() {
        return repositorio.findAll().stream()
                .map(CorteAguaMongoAdapter::aDominio)
                .toList();
    }

    @Override
    public CorteAgua guardar(CorteAgua corte) {
        CorteAguaDocumento documento = new CorteAguaDocumento();
        documento.setId(corte.id().valor());
        documento.setSectoresAfectados(corte.sectoresAfectados().stream().map(SectorId::valor).toList());
        documento.setInicio(corte.ventana().inicio());
        documento.setFinPrometido(corte.ventana().finPrometido());
        documento.setFinReal(corte.ventana().finReal());
        documento.setCausa(corte.causa());
        documento.setOrigen(corte.origen().name());
        documento.setEstado(corte.estado().name());
        documento.setCierres(corte.cierres().entrySet().stream()
                .map(entrada -> aDocumento(entrada.getKey(), entrada.getValue()))
                .toList());
        documento.setMotivoAnulacion(corte.motivoAnulacion());
        documento.setCaducaEn(corte.caducaEn());

        repositorio.save(documento);
        return corte;
    }

    /**
     * `$addToSet` + `$setOnInsert` en una sola operación atómica de Mongo: si el corte ya existe,
     * solo anexa el sector (no-op si ya estaba); si no existe, lo crea con estos campos. Ver
     * javadoc del puerto — reemplaza el leer→modificar→guardar que RevisarPropuestaIngestaService
     * hacía antes en memoria.
     */
    @Override
    public void anexarSectorAlCorte(CorteId id, SectorId sectorId, Instant inicio, Instant finPrometido,
                                     String causa, OrigenCorte origen, EstadoCorte estado) {
        Query query = Query.query(Criteria.where("_id").is(id.valor()));
        Update update = new Update()
                .addToSet("sectoresAfectados", sectorId.valor())
                .setOnInsert("inicio", inicio)
                .setOnInsert("finPrometido", finPrometido)
                .setOnInsert("causa", causa)
                .setOnInsert("origen", origen.name())
                .setOnInsert("estado", estado.name());
        mongoTemplate.upsert(query, update, CorteAguaDocumento.class);
    }

    /**
     * `estado-del-backend.md` #6.1: `listarTodos()` para calcular el índice global traía todos los
     * cortes cerrados a memoria — hoy acotado por el volumen real, pero incorrecto si el histórico
     * crece. La suma vive en Mongo; `CalcularCumplimientoService` solo calcula el porcentaje sobre
     * los dos totales.
     */
    @Override
    public AgregadoDuraciones agregarCerrados(SectorId sectorId) {
        List<AggregationOperation> etapas = new ArrayList<>(paresCerrados(sectorId, null, null));
        etapas.add(agruparTotal());
        Aggregation pipeline = Aggregation.newAggregation(etapas);

        AggregationResults<Document> resultados =
                mongoTemplate.aggregate(pipeline, CorteAguaDocumento.class, Document.class);
        Document total = resultados.getUniqueMappedResult();
        return total == null ? AgregadoDuraciones.vacio() : aAgregado(total);
    }

    /**
     * Mismo principio que {@link #agregarCerrados}, agrupado por mes con `$dateToString` en
     * `America/Bogota` (RF024: el mes se decide en hora de Cartagena, no en UTC). El formato
     * "%Y-%m" ordena cronológicamente como texto y `YearMonth.parse` lo reconstruye tal cual.
     */
    @Override
    public List<PuntoAgregadoMensual> agregarCerradosPorMes(SectorId sectorId, Instant desde, Instant hasta) {
        List<AggregationOperation> etapas = new ArrayList<>(paresCerrados(sectorId, desde, hasta));
        etapas.add(agruparPorMes());
        etapas.add(Aggregation.sort(Sort.Direction.ASC, "_id"));
        Aggregation pipeline = Aggregation.newAggregation(etapas);

        AggregationResults<Document> resultados =
                mongoTemplate.aggregate(pipeline, CorteAguaDocumento.class, Document.class);
        return resultados.getMappedResults().stream()
                .map(doc -> new PuntoAgregadoMensual(YearMonth.parse(doc.getString("_id")), aAgregado(doc)))
                .toList();
    }

    /**
     * Cada par corte-barrio con cierre es una fila (D14): un barrio restablecido a la una hora cuenta aunque otro del mismo corte siga
     * sin servicio, y cada uno con su propia hora. Los documentos anteriores a los cierres por sector no traen la lista: su
     * `finReal` vale para todos sus barrios. Los anulados no entran al Índice (se publicaron por error). Un expirado entra por los barrios que sí se cerraron (el filtro de abajo deja fuera los que no tienen ninguno): que otro barrio del mismo corte nunca se cerrara no borra lo medido.
     */
    private static List<AggregationOperation> paresCerrados(SectorId sectorId, Instant desde, Instant hasta) {
        Document primero = new Document("estado", new Document("$ne", "ANULADO"))
                .append("$or", List.of(new Document("finReal", new Document("$ne", null)),
                        new Document("cierres.0", new Document("$exists", true))));
        if (sectorId != null) {
            // sectoresAfectados es un arreglo; comparar contra un escalar es «el arreglo lo contiene».
            primero.append("sectoresAfectados", sectorId.valor());
        }

        List<AggregationOperation> etapas = new ArrayList<>();
        etapas.add(contexto -> new Document("$match", primero));
        etapas.add(contexto -> new Document("$addFields", new Document("cierresEfectivos", cierresEfectivos())));
        etapas.add(contexto -> new Document("$unwind", "$cierresEfectivos"));

        Document filtroDelPar = new Document();
        if (sectorId != null) {
            filtroDelPar.append("cierresEfectivos.sectorId", sectorId.valor());
        }
        if (desde != null || hasta != null) {
            Document hora = new Document();
            if (desde != null) {
                hora.append("$gte", Date.from(desde));
            }
            if (hasta != null) {
                hora.append("$lte", Date.from(hasta));
            }
            filtroDelPar.append("cierresEfectivos.hora", hora);
        }
        if (!filtroDelPar.isEmpty()) {
            etapas.add(contexto -> new Document("$match", filtroDelPar));
        }
        return etapas;
    }

    /** Los cierres del documento; si no trae la lista pero sí `finReal`, un cierre en esa hora por cada barrio; si no, ninguno. */
    private static Document cierresEfectivos() {
        Document deLosBarrios = new Document("$map", new Document("input", "$sectoresAfectados").append("as", "s")
                .append("in", new Document("sectorId", "$$s").append("hora", "$finReal").append("provisional", false)));
        return new Document("$cond", List.of(
                new Document("$gt", List.of(new Document("$size", new Document("$ifNull", List.of("$cierres", List.of()))), 0)),
                "$cierres",
                // $gt contra null y no $ne: en una expresion, un campo ausente no es igual a null, y finReal se omite cuando no hay.
                new Document("$cond", List.of(new Document("$gt", java.util.Arrays.asList("$finReal", null)), deLosBarrios, List.of()))));
    }

    private static AggregationOperation agruparTotal() {
        return context -> new Document("$group", camposDeSuma(new Document("_id", null)));
    }

    private static AggregationOperation agruparPorMes() {
        Document mesEnCartagena = new Document("$dateToString", new Document("format", "%Y-%m")
                .append("date", "$cierresEfectivos.hora")
                .append("timezone", "America/Bogota"));
        return context -> new Document("$group", camposDeSuma(new Document("_id", mesEnCartagena)));
    }

    /** $subtract entre dos fechas da milisegundos — el mismo operador que ya usa EstadisticasMongoAdapter. */
    private static Document camposDeSuma(Document idDelGrupo) {
        return idDelGrupo
                .append("milisPrometidos", new Document("$sum",
                        new Document("$subtract", List.of("$finPrometido", "$inicio"))))
                .append("milisReales", new Document("$sum",
                        new Document("$subtract", List.of("$cierresEfectivos.hora", "$inicio"))))
                .append("cantidad", new Document("$sum", 1))
                .append("provisionales", new Document("$sum",
                        new Document("$cond", List.of("$cierresEfectivos.provisional", 1, 0))));
    }

    @Override
    public CalidadDelDato calidadDelDato(SectorId sectorId, Instant ahora) {
        Document delBarrio = sectorId == null ? new Document() : new Document("sectoresAfectados", sectorId.valor());

        long anulados = mongoTemplate.getCollection("cortes")
                .countDocuments(new Document(delBarrio).append("estado", "ANULADO"));

        // Corte vencido que no es anulado y en el que algún barrio (o el barrio pedido) no tiene cierre. Un expirado
        // no tiene ninguno: es justo «sin cierre confirmado».
        Document filtro = new Document(delBarrio)
                .append("estado", new Document("$ne", "ANULADO"))
                .append("finPrometido", new Document("$lte", Date.from(ahora)));
        Document sinCierre = sectorId == null
                ? new Document("$lt", List.of(new Document("$size", "$cierresEfectivos"), new Document("$size", "$sectoresAfectados")))
                : new Document("$not", List.of(new Document("$in", List.of(sectorId.valor(), "$cierresEfectivos.sectorId"))));
        Aggregation pipeline = Aggregation.newAggregation(List.of(
                (AggregationOperation) contexto -> new Document("$match", filtro),
                contexto -> new Document("$addFields", new Document("cierresEfectivos", cierresEfectivos())),
                contexto -> new Document("$match", new Document("$expr", sinCierre)),
                contexto -> new Document("$count", "cortes")));
        Document resultado = mongoTemplate.aggregate(pipeline, CorteAguaDocumento.class, Document.class).getUniqueMappedResult();
        long sinCierres = resultado == null ? 0 : ((Number) resultado.get("cortes")).longValue();
        return new CalidadDelDato(sinCierres, anulados);
    }

    private static AgregadoDuraciones aAgregado(Document doc) {
        long milisPrometidos = ((Number) doc.get("milisPrometidos")).longValue();
        long milisReales = ((Number) doc.get("milisReales")).longValue();
        long cantidad = ((Number) doc.get("cantidad")).longValue();
        long provisionales = doc.get("provisionales") == null ? 0 : ((Number) doc.get("provisionales")).longValue();
        return new AgregadoDuraciones(Duration.ofMillis(milisPrometidos), Duration.ofMillis(milisReales), cantidad, provisionales);
    }

    private static CorteAguaDocumento.Cierre aDocumento(SectorId sectorId, CierreDeCorte cierre) {
        CorteAguaDocumento.Cierre documento = new CorteAguaDocumento.Cierre();
        documento.setSectorId(sectorId.valor());
        documento.setHora(cierre.hora());
        documento.setFuente(cierre.fuente().name());
        documento.setProvisional(cierre.provisional());
        return documento;
    }

    /** Un documento anterior a los cierres por sector no trae la lista: el dominio lo completa desde finReal. */
    private static Map<SectorId, CierreDeCorte> cierresDe(CorteAguaDocumento documento) {
        Map<SectorId, CierreDeCorte> cierres = new LinkedHashMap<>();
        if (documento.getCierres() != null) {
            for (CorteAguaDocumento.Cierre cierre : documento.getCierres()) {
                cierres.put(new SectorId(cierre.getSectorId()),
                        new CierreDeCorte(cierre.getHora(), OrigenEstado.valueOf(cierre.getFuente()), cierre.isProvisional()));
            }
        }
        return cierres;
    }

    private static CorteAgua aDominio(CorteAguaDocumento documento) {
        try {
            return CorteAgua.builder()
                    .id(new CorteId(documento.getId()))
                    .sectoresAfectados(documento.getSectoresAfectados().stream().map(SectorId::new).toList())
                    .inicio(documento.getInicio())
                    .finPrometido(documento.getFinPrometido())
                    .finReal(documento.getFinReal())
                    .causa(documento.getCausa())
                    .origen(OrigenCorte.valueOf(documento.getOrigen()))
                    .estado(EstadoCorte.valueOf(documento.getEstado()))
                    .cierres(cierresDe(documento))
                    .motivoAnulacion(documento.getMotivoAnulacion())
                    .caducaEn(documento.getCaducaEn())
                    .build();
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw new IllegalStateException(
                    "Corte '" + documento.getId() + "' persistido con datos inconsistentes", e);
        }
    }
}
