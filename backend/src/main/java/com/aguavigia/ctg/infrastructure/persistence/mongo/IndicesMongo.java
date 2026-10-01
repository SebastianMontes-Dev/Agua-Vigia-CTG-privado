package com.aguavigia.ctg.infrastructure.persistence.mongo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.CompoundIndexDefinition;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexType;
import org.springframework.data.mongodb.core.index.GeospatialIndex;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import org.bson.Document;

import java.time.Duration;

/**
 * Asegura los indices de `sectores` al arrancar. Spring Data no los crea solo (la creacion
 * automatica esta desactivada por defecto desde 3.0) y el sembrador solo corre a mano,
 * asi que sin esto un despliegue limpio quedaria sin el 2dsphere que necesitan las consultas
 * geoespaciales de M2.
 *
 * createIndex es idempotente: repetirlo con la misma definicion no hace nada.
 */
@Component
public class IndicesMongo {

    private static final Logger log = LoggerFactory.getLogger(IndicesMongo.class);

    private final MongoTemplate mongoTemplate;
    private final long diasRetencionReportes;
    private final long diasRetencionDispositivos;

    public IndicesMongo(MongoTemplate mongoTemplate,
                        @Value("${aguavigia.retencion.reportes-dias:365}") long diasRetencionReportes,
                        @Value("${aguavigia.retencion.dispositivos-dias:365}") long diasRetencionDispositivos) {
        this.mongoTemplate = mongoTemplate;
        this.diasRetencionReportes = diasRetencionReportes;
        this.diasRetencionDispositivos = diasRetencionDispositivos;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void asegurarIndices() {
        intentar("Sectores", () -> {
            var indicesSectores = mongoTemplate.indexOps(SectorDocumento.class);
            indicesSectores.ensureIndex(new GeospatialIndex("geometry").typed(GeoSpatialIndexType.GEO_2DSPHERE));
            indicesSectores.ensureIndex(new Index().on("slug", Sort.Direction.ASC).unique());
            log.info("Indices de `sectores` asegurados: geometry (2dsphere) y slug (unico)");
        });

        intentar("Cortes", () -> {
            var indicesCortes = mongoTemplate.indexOps(CorteAguaDocumento.class);
            indicesCortes.ensureIndex(new Index().on("sectoresAfectados", Sort.Direction.ASC));
            // El Indice de Cumplimiento y las estadisticas agregan solo los cortes cerrados (`finReal`
            // no nulo); sin este indice cada agregacion recorria la coleccion entera.
            indicesCortes.ensureIndex(new Index().on("finReal", Sort.Direction.ASC));
            log.info("Indices de `cortes` asegurados: sectoresAfectados y finReal");
        });

            // Cada POST /api/reportes cuenta lo que el dispositivo ya envio (cupo RF006) y evalua el
            // consenso (votos por tipo): sin estos compuestos, cada llamada recorre la ventana entera
            // del sector, que en una averia masiva son miles de documentos.
        intentar("Reportes", () -> {
            var indicesReportes = mongoTemplate.indexOps(ReporteCiudadanoDocumento.class);
            indicesReportes.ensureIndex(new CompoundIndexDefinition(
                    new Document("sectorId", 1).append("timestamp", -1)));
            indicesReportes.ensureIndex(new CompoundIndexDefinition(
                    new Document("sectorId", 1).append("huella", 1).append("timestamp", -1)));
            // La cola de moderacion pide PENDIENTE (o sin campo) por antiguedad. Con este compuesto Mongo lee
            // solo la pagina; con el de un solo campo examinaba todos los pendientes y ordenaba en memoria.
            indicesReportes.ensureIndex(new CompoundIndexDefinition(
                    new Document("estadoModeracion", 1).append("timestamp", 1)));
            // El de un solo campo (bases creadas antes) es prefijo del compuesto: solo encarece cada insercion.
            retirarIndiceSiExiste(indicesReportes, "estadoModeracion_1");
            log.info("Indices de `reportes` asegurados: sectorId+timestamp, sectorId+huella+timestamp y estadoModeracion+timestamp");
            asegurarRetencionDeReportes(indicesReportes);
        });

        intentar("Suscripciones", () -> {
            var indicesSuscripciones = mongoTemplate.indexOps(SuscripcionDocumento.class);
            indicesSuscripciones.ensureIndex(new Index().on("tokenConfirmacion", Sort.Direction.ASC).unique());
            indicesSuscripciones.ensureIndex(new Index().on("sectorIds", Sort.Direction.ASC));
            log.info("Indices de `suscripciones` asegurados: tokenConfirmacion (unico) y sectorIds");
        });

        intentar("SuscripcionesTelegram", () -> {
            var indicesSuscripcionesTelegram = mongoTemplate.indexOps(SuscripcionTelegramDocumento.class);
            indicesSuscripcionesTelegram.ensureIndex(new Index().on("sectorIds", Sort.Direction.ASC));
            log.info("Indices de `suscripciones_telegram` asegurados: sectorIds");
        });

        intentar("Bitacora", () -> {
            var indicesBitacora = mongoTemplate.indexOps(EventoBitacoraDocumento.class);
            indicesBitacora.ensureIndex(new Index().on("timestamp", Sort.Direction.DESC));
            // La bitacora de un sector: sin esto recorria todos los eventos y filtraba por sector.
            indicesBitacora.ensureIndex(new CompoundIndexDefinition(
                    new Document("sectorId", 1).append("timestamp", -1)));
            // El filtro por tipo sin barrio: con 300 000 eventos el total y la pagina recorrian toda la coleccion
            // (~100 ms y creciendo). Con este compuesto Mongo cuenta y ordena sobre el indice.
            indicesBitacora.ensureIndex(new CompoundIndexDefinition(
                    new Document("tipo", 1).append("timestamp", -1)));
            log.info("Indices de `eventos_bitacora` asegurados: timestamp, sectorId+timestamp y tipo+timestamp");
        });

            // La cola del veedor se lee filtrando por estadoRevision y ordenando por detectadaEn, y
            // el pipeline pregunta existePendiente(sector, estado) por cada documento de cada ciclo.
        intentar("Propuestas", () -> {
            var indicesPropuestas = mongoTemplate.indexOps(PropuestaIngestaDocumento.class);
            indicesPropuestas.ensureIndex(new CompoundIndexDefinition(
                    new Document("estadoRevision", 1).append("detectadaEn", -1)));
            indicesPropuestas.ensureIndex(new CompoundIndexDefinition(
                    new Document("sectorId", 1).append("estadoPropuesto", 1).append("estadoRevision", 1)));
            log.info("Indices de `propuestas_ingesta` asegurados: estadoRevision+detectadaEn y sectorId+estadoPropuesto+estadoRevision");
        });

            // El correo es la identidad de acceso: el indice unico es la unica garantia real de
            // que no haya dos cuentas con el mismo. Comprobarlo antes de insertar deja una ventana
            // entre la lectura y la escritura por la que dos registros a la vez pasan los dos.
        intentar("Usuarios", () -> {
            var indicesUsuarios = mongoTemplate.indexOps(UsuarioDocumento.class);
            indicesUsuarios.ensureIndex(new Index().on("correo", Sort.Direction.ASC).unique());
            indicesUsuarios.ensureIndex(new CompoundIndexDefinition(
                    new Document("rol", 1).append("estado", 1)));
            indicesUsuarios.ensureIndex(new Index().on("estado", Sort.Direction.ASC));
            // El listado del panel filtra por barrio y ordena por creadoEn descendente (ADR-081).
            indicesUsuarios.ensureIndex(new CompoundIndexDefinition(
                    new Document("barrio", 1).append("creadoEn", -1)));
            log.info("Indices de `usuarios` asegurados: correo (unico), rol+estado, estado y barrio+creadoEn");
        });

            // TTL sobre expiraEn: Mongo borra solo el token vencido. Sin esto la coleccion crece
            // con hashes que ya no valen para nada (TokenCuenta.estaVigente los rechaza igual).
        intentar("Tokens", () -> {
            var indicesTokens = mongoTemplate.indexOps(TokenCuentaDocumento.class);
            indicesTokens.ensureIndex(new Index().on("usuarioId", Sort.Direction.ASC));
            indicesTokens.ensureIndex(new Index().on("expiraEn", Sort.Direction.ASC)
                    .expire(java.time.Duration.ZERO));
            log.info("Indices de `tokens_cuenta` asegurados: usuarioId y expiraEn (TTL)");
        });

        intentar("Auditoria", () -> {
            var indicesAuditoria = mongoTemplate.indexOps(EventoAuditoriaDocumento.class);
            indicesAuditoria.ensureIndex(new Index().on("ocurrioEn", Sort.Direction.DESC));
            log.info("Indices de `auditoria_cuentas` asegurados: ocurrioEn");
        });

            // Retencion de dispositivos (D19): sin dueño que los reclame ni utilidad una vez inactivos, Mongo
            // los borra solo `diasRetencionDispositivos` despues de su ultimo uso. 0 desactiva la retencion.
        intentar("Dispositivos", () -> {
            if (diasRetencionDispositivos <= 0) {
                return;
            }
            mongoTemplate.indexOps(DispositivoDocumento.class).ensureIndex(
                    new Index().on("ultimoVisto", Sort.Direction.ASC)
                            .expire(Duration.ofDays(diasRetencionDispositivos)));
            log.info("Retencion de `dispositivos`: se borran solos a los {} dias de su ultimo uso",
                    diasRetencionDispositivos);
        });

            // RNF006/BUG-091 — la cola de fallidos del veedor se lee ordenada por el ultimo intento.
        intentar("Fallidos", () -> {
            var indicesFallidos = mongoTemplate.indexOps(DocumentoFallidoDocumento.class);
            indicesFallidos.ensureIndex(new Index().on("ultimoIntento", Sort.Direction.DESC));
            log.info("Indices de `documentos_fallidos` asegurados: ultimoIntento");
        });
    }

    /**
     * Cada colección va en su propio intento: si un índice falla (una geometría inválida en `sectores`, un
     * duplicado al crear un único) los demás se crean igual, en particular el único de `usuarios.correo`, que es
     * lo que impide dos cuentas con el mismo correo. Un fallo se registra como error, sin tumbar el arranque.
     */
    private void intentar(String coleccion, Runnable bloque) {
        try {
            bloque.run();
        } catch (DataAccessException e) {
            log.error("No se pudieron asegurar los indices de `{}`: {}", coleccion, e.getMessage());
        }
    }

    /**
     * Retencion de reportes: Mongo los borra solo pasados `diasRetencionReportes` (indice TTL sobre
     * `timestamp`). Los eventos de la bitacora son permanentes y conservan los ids de sus reportes de sustento,
     * que pasado ese plazo apuntan a reportes que ya no existen. 0 desactiva la retencion.
     *
     * Va aparte del resto: cambiar el plazo en una base ya creada hace que Mongo rechace el indice (mismo nombre,
     * otra caducidad), y eso no debe impedir que se creen los demas.
     */
    private void asegurarRetencionDeReportes(org.springframework.data.mongodb.core.index.IndexOperations indicesReportes) {
        if (diasRetencionReportes <= 0) {
            return;
        }
        try {
            indicesReportes.ensureIndex(new Index().on("timestamp", Sort.Direction.ASC)
                    .expire(Duration.ofDays(diasRetencionReportes)));
            log.info("Retencion de `reportes`: se borran solos a los {} dias", diasRetencionReportes);
        } catch (DataAccessException e) {
            log.warn("No se pudo asegurar la retencion de reportes ({} dias); si ya existia con otro plazo, "
                    + "hay que retirar el indice `timestamp_1` a mano: {}", diasRetencionReportes, e.getMessage());
        }
    }

    private static void retirarIndiceSiExiste(org.springframework.data.mongodb.core.index.IndexOperations indices, String nombre) {
        boolean existe = indices.getIndexInfo().stream().anyMatch(indice -> nombre.equals(indice.getName()));
        if (existe) {
            indices.dropIndex(nombre);
            log.info("Indice redundante `{}` retirado", nombre);
        }
    }
}
