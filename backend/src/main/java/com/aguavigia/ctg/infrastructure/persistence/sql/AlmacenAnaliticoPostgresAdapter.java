package com.aguavigia.ctg.infrastructure.persistence.sql;

import com.aguavigia.ctg.domain.AnaliticaNoDisponibleException;
import com.aguavigia.ctg.domain.ConteosAnaliticos;
import com.aguavigia.ctg.domain.FilaCorteAnalitica;
import com.aguavigia.ctg.domain.FilaParAnalitica;
import com.aguavigia.ctg.domain.FilaSectorAnalitica;
import com.aguavigia.ctg.domain.ResultadoDeCopia;
import com.aguavigia.ctg.domain.port.out.AlmacenAnaliticoPort;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Array;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Supplier;

/**
 * Escribe la copia analítica en PostgreSQL (ADR-100). El gestor de transacciones es privado de este adaptador y NO se registra como bean:
 * el único {@code PlatformTransactionManager} del contexto tiene que seguir siendo el de Mongo, que {@code TransaccionMongoAdapter}
 * recibe por tipo.
 *
 * Flyway no migra al arrancar sino en la primera copia que consigue conexión, así que arrancar con PostgreSQL apagado no falla.
 */
public class AlmacenAnaliticoPostgresAdapter implements AlmacenAnaliticoPort {

    private static final Logger log = LoggerFactory.getLogger(AlmacenAnaliticoPostgresAdapter.class);
    static final String ESQUEMA = "analitica";

    private final DataSource dataSource;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaccion;
    private volatile boolean migrado;

    public AlmacenAnaliticoPostgresAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
        this.jdbc = new JdbcTemplate(dataSource);
        this.transaccion = new TransactionTemplate(new JdbcTransactionManager(dataSource));
    }

    @Override
    public long registrarInicioDeCopia(Instant ahora) {
        return alcanzable(() -> {
            migrarSiHaceFalta();
            Long id = jdbc.queryForObject(
                    "INSERT INTO analitica.copia (iniciada_en, resultado) VALUES (?, 'EN_CURSO') RETURNING id",
                    Long.class, utc(ahora));
            return id;
        });
    }

    @Override
    public ConteosAnaliticos reemplazar(List<FilaSectorAnalitica> sectores, List<FilaCorteAnalitica> cortes, Instant copiadoEn) {
        return alcanzable(() -> transaccion.execute(estado -> {
            OffsetDateTime copiado = utc(copiadoEn);
            List<String> idsDeSectores = sectores.stream().map(FilaSectorAnalitica::id).toList();
            List<String> idsDeCortes = cortes.stream().map(FilaCorteAnalitica::id).toList();

            // Los pares se rehacen enteros; primero se quitan, así borrar un corte o un barrio que ya no existe no choca con la clave foránea.
            jdbc.update("DELETE FROM analitica.corte_sector");
            borrarLosQueNoEstan("analitica.corte", idsDeCortes);
            borrarLosQueNoEstan("analitica.sector", idsDeSectores);

            jdbc.batchUpdate("""
                    INSERT INTO analitica.sector (id, nombre, poblacion, copiado_en) VALUES (?, ?, ?, ?)
                    ON CONFLICT (id) DO UPDATE SET nombre = EXCLUDED.nombre, poblacion = EXCLUDED.poblacion,
                                                   copiado_en = EXCLUDED.copiado_en
                    """, sectores, 500, (ps, sector) -> {
                ps.setString(1, sector.id());
                ps.setString(2, sector.nombre());
                if (sector.poblacion() == null) {
                    ps.setNull(3, Types.INTEGER);
                } else {
                    ps.setInt(3, sector.poblacion());
                }
                ps.setObject(4, copiado);
            });

            jdbc.batchUpdate("""
                    INSERT INTO analitica.corte (id, origen, estado, causa, inicio, fin_prometido, copiado_en)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (id) DO UPDATE SET origen = EXCLUDED.origen, estado = EXCLUDED.estado, causa = EXCLUDED.causa,
                                                   inicio = EXCLUDED.inicio, fin_prometido = EXCLUDED.fin_prometido,
                                                   copiado_en = EXCLUDED.copiado_en
                    """, cortes, 500, (ps, corte) -> {
                ps.setString(1, corte.id());
                ps.setString(2, corte.origen());
                ps.setString(3, corte.estado());
                ps.setString(4, corte.causa());
                ps.setObject(5, utc(corte.inicio()));
                ps.setObject(6, utc(corte.finPrometido()));
                ps.setObject(7, copiado);
            });

            List<ParDeCorte> pares = cortes.stream()
                    .flatMap(corte -> corte.pares().stream().map(par -> new ParDeCorte(corte.id(), par)))
                    .toList();
            jdbc.batchUpdate("""
                    INSERT INTO analitica.corte_sector (corte_id, sector_id, cierre_hora, cierre_fuente, cierre_provisional)
                    VALUES (?, ?, ?, ?, ?)
                    """, pares, 1000, (ps, par) -> {
                ps.setString(1, par.corteId());
                ps.setString(2, par.fila().sectorId());
                if (par.fila().cierreHora() == null) {
                    ps.setNull(3, Types.TIMESTAMP_WITH_TIMEZONE);
                } else {
                    ps.setObject(3, utc(par.fila().cierreHora()));
                }
                ps.setString(4, par.fila().cierreFuente());
                ps.setBoolean(5, par.fila().cierreProvisional());
            });

            return contar();
        }));
    }

    @Override
    public void cerrarCopia(long id, Instant terminadaEn, ResultadoDeCopia resultado, ConteosAnaliticos enMongo,
                            ConteosAnaliticos enSql, String detalle) {
        alcanzable(() -> jdbc.update("""
                UPDATE analitica.copia
                   SET terminada_en = ?, resultado = ?,
                       sectores_en_mongo = ?, cortes_en_mongo = ?, pares_en_mongo = ?,
                       sectores_en_sql = ?, cortes_en_sql = ?, pares_en_sql = ?, detalle = ?
                 WHERE id = ?
                """, utc(terminadaEn), resultado.name(),
                enMongo == null ? null : enMongo.sectores(), enMongo == null ? null : enMongo.cortes(),
                enMongo == null ? null : enMongo.pares(),
                enSql == null ? null : enSql.sectores(), enSql == null ? null : enSql.cortes(),
                enSql == null ? null : enSql.pares(),
                detalle, id));
    }

    private ConteosAnaliticos contar() {
        return jdbc.queryForObject("""
                SELECT (SELECT COUNT(*) FROM analitica.sector),
                       (SELECT COUNT(*) FROM analitica.corte),
                       (SELECT COUNT(*) FROM analitica.corte_sector)
                """, (rs, fila) -> new ConteosAnaliticos(rs.getInt(1), rs.getInt(2), rs.getInt(3)));
    }

    /** Quita las filas cuyo id no viene en la copia (en cascada, sus pares). Con una lista vacía quita todas. */
    private void borrarLosQueNoEstan(String tabla, List<String> ids) {
        jdbc.update(conexion -> {
            Array arreglo = conexion.createArrayOf("text", ids.toArray());
            var ps = conexion.prepareStatement("DELETE FROM " + tabla + " WHERE id <> ALL (?)");
            ps.setArray(1, arreglo);
            return ps;
        });
    }

    private void migrarSiHaceFalta() {
        if (migrado) {
            return;
        }
        synchronized (this) {
            if (migrado) {
                return;
            }
            var resultado = Flyway.configure()
                    .dataSource(dataSource)
                    .schemas(ESQUEMA)
                    .locations("classpath:db/migration")
                    .load()
                    .migrate();
            if (resultado.migrationsExecuted > 0) {
                log.info("Base analítica: {} migración(es) aplicada(s), esquema en la versión {}",
                        resultado.migrationsExecuted, resultado.targetSchemaVersion);
            }
            migrado = true;
        }
    }

    /** Cualquier fallo de la base o de las migraciones sale como «analítica no disponible»: el caso de uso lo trata como una copia fallida. */
    private <T> T alcanzable(Supplier<T> operacion) {
        try {
            return operacion.get();
        } catch (DataAccessException | FlywayException | org.springframework.transaction.TransactionException fallo) {
            throw new AnaliticaNoDisponibleException("La base analítica falló: " + causaRaiz(fallo), fallo);
        }
    }

    static String causaRaiz(Throwable fallo) {
        Throwable raiz = fallo;
        while (raiz.getCause() != null && raiz.getCause() != raiz) {
            raiz = raiz.getCause();
        }
        return raiz.getMessage() == null ? raiz.getClass().getSimpleName() : raiz.getMessage();
    }

    static OffsetDateTime utc(Instant instante) {
        return instante == null ? null : OffsetDateTime.ofInstant(instante, ZoneOffset.UTC);
    }

    private record ParDeCorte(String corteId, FilaParAnalitica fila) {
    }
}
